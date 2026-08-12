package io.neonbee.cluster;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.test.helper.DeploymentHelper.deployVerticle;
import static io.neonbee.test.helper.DeploymentHelper.undeployAllVerticlesOfClass;
import static io.vertx.core.Future.succeededFuture;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.data.Property;
import org.apache.olingo.commons.api.data.ValueType;
import org.apache.olingo.commons.api.edm.FullQualifiedName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.neonbee.NeonBee;
import io.neonbee.NeonBeeDeployable;
import io.neonbee.NeonBeeExtension;
import io.neonbee.NeonBeeInstanceConfiguration;
import io.neonbee.data.DataContext;
import io.neonbee.data.DataQuery;
import io.neonbee.data.DataRequest;
import io.neonbee.data.internal.DataContextImpl;
import io.neonbee.entity.EntityVerticle;
import io.neonbee.entity.EntityWrapper;
import io.neonbee.internal.cluster.ClusterHelper;
import io.vertx.core.Future;
import io.vertx.junit5.VertxTestContext;

/**
 * Verifies the routing of entity verticle requests in a clustered setup using the in-JVM
 * {@link io.vertx.test.fakecluster.FakeClusterManager} (so it needs no external infrastructure and is CI-safe).
 * <p>
 * Entity verticles register a consumer at the shared FQN address {@code EntityVerticle[<FQN>]} which forwards to the
 * co-located {@code DataVerticle[<name>]} instance. When the same entity verticle is deployed on multiple nodes both
 * the FQN address and the inner {@code DataVerticle[<name>]} address exist on every node. These tests assert that a
 * request with a local instance available always resolves to the local instance and never round-robins across the
 * cluster - covering both the outer FQN hop and the inner forward hop.
 */
class EntityVerticleLocalPreferredClusterTest extends NeonBeeExtension.TestBase {

    static final FullQualifiedName ENTITY_FQN = new FullQualifiedName("cluster.test", "NodeTagged");

    private static final String NODE_PROPERTY = "node";

    private static final long REPETITION = 10L;

    private NeonBee localNode;

    @BeforeEach
    @DisplayName("Set up two cluster nodes and deploy the entity verticle on both")
    void setUp(@NeonBeeInstanceConfiguration(clustered = true, activeProfiles = {}) NeonBee localNode,
            @NeonBeeInstanceConfiguration(clustered = true, activeProfiles = {}) NeonBee remoteNode,
            VertxTestContext testContext) {
        this.localNode = localNode;
        deployVerticle(localNode.getVertx(), new NodeTaggingEntityVerticle())
                .compose(v -> deployVerticle(remoteNode.getVertx(), new NodeTaggingEntityVerticle()))
                .onComplete(testContext.succeedingThenComplete());
    }

    @Test
    @DisplayName("entity requests with a local instance available always resolve locally (never round-robin)")
    void testEntityRequestPrefersLocalInstance(VertxTestContext testContext) {
        String localNodeId = ClusterHelper.getClusterNodeId(localNode.getVertx());
        fireEntityRequests(localNode).onComplete(testContext.succeeding(responses -> {
            testContext.verify(() -> {
                // Every request must have been served by the local instance - proves neither the outer FQN hop nor
                // the inner DataVerticle[<name>] forward round-robined to the remote node.
                assertThat(responses).containsExactly(localNodeId, REPETITION);
            });
            testContext.completeNow();
        }));
    }

    @Test
    @DisplayName("the FQN address is registered and deregistered as a local consumer with the verticle lifecycle")
    void testFqnAddressLocalConsumerRegistration(VertxTestContext testContext) {
        String fqnAddress = "EntityVerticle[" + ENTITY_FQN.getFullQualifiedNameAsString() + "]";
        assertThat(localNode.isLocalConsumerAvailable(fqnAddress)).isTrue();
        undeployAllVerticlesOfClass(localNode.getVertx(), NodeTaggingEntityVerticle.class)
                .onComplete(testContext.succeeding(v -> {
                    testContext.verify(
                            () -> assertThat(localNode.isLocalConsumerAvailable(fqnAddress)).isFalse());
                    testContext.completeNow();
                }));
    }

    private Future<Map<String, Long>> fireEntityRequests(NeonBee from) {
        return Future.all(IntStream.rangeClosed(1, (int) REPETITION)
                .mapToObj(i -> EntityVerticle.requestEntity(from.getVertx(),
                        new DataRequest(ENTITY_FQN, new DataQuery()), new DataContextImpl()))
                .toList())
                .map(cf -> cf.<EntityWrapper>list().stream()
                        .map(ew -> (String) ew.getEntities().get(0).getProperty(NODE_PROPERTY).getValue())
                        .collect(Collectors.groupingBy(node -> node, Collectors.counting())));
    }

    @NeonBeeDeployable(namespace = "cluster", autoDeploy = false)
    public static class NodeTaggingEntityVerticle extends EntityVerticle {
        @Override
        public Future<Set<FullQualifiedName>> entityTypeNames() {
            return succeededFuture(Set.of(ENTITY_FQN));
        }

        @Override
        public Future<EntityWrapper> retrieveData(DataQuery query, DataContext context) {
            // Tag the response with the id of the node that actually served it, so the caller can assert routing.
            Entity entity = new Entity();
            entity.addProperty(new Property(null, NODE_PROPERTY, ValueType.PRIMITIVE,
                    ClusterHelper.getClusterNodeId(vertx)));
            return succeededFuture(new EntityWrapper(ENTITY_FQN, entity));
        }
    }
}
