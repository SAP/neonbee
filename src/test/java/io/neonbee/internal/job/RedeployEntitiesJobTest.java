package io.neonbee.internal.job;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.NeonBeeInstanceConfiguration.ClusterManager.INFINISPAN;
import static io.neonbee.NeonBeeProfile.ALL;
import static io.vertx.core.Future.succeededFuture;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.edm.FullQualifiedName;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Isolated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.neonbee.NeonBee;
import io.neonbee.NeonBeeDeployable;
import io.neonbee.NeonBeeExtension;
import io.neonbee.NeonBeeInstanceConfiguration;
import io.neonbee.NeonBeeOptions;
import io.neonbee.NeonBeeProfile;
import io.neonbee.data.DataContext;
import io.neonbee.data.DataQuery;
import io.neonbee.entity.EntityVerticle;
import io.neonbee.entity.EntityWrapper;
import io.neonbee.internal.Registry;
import io.neonbee.internal.cluster.ClusterHelper;
import io.neonbee.internal.cluster.entity.ClusterEntityRegistry;
import io.neonbee.test.helper.FileSystemHelper;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Verticle;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(NeonBeeExtension.class)
@Isolated
class RedeployEntitiesJobTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(RedeployEntitiesJobTest.class);

    private Path tempDirPath;

    @BeforeEach
    void setup() throws IOException {
        tempDirPath = createTempDirectory();
        LOGGER.info("Temporary file directory : " + tempDirPath.toString());
    }

    @AfterEach
    void tearDown() {
        FileSystemHelper.deleteRecursiveBlocking(tempDirPath);
    }

    /**
     * Creates a temporary directory.
     *
     * @return The path of the temporary created directory.
     * @throws IOException If creating the temporary directory is not successful
     */
    public static Path createTempDirectory() throws IOException {
        return Files.createTempDirectory(RedeployEntitiesJobTest.class.getName());
    }

    @NeonBeeDeployable(namespace = NeonBeeDeployable.NEONBEE_NAMESPACE, autoDeploy = false)
    public static class TestEntityVerticle1 extends EntityVerticle {

        /**
         * The fully qualified name of the entity type "ERP.Customers".
         */
        static final FullQualifiedName FQN_ERP_CUSTOMERS = new FullQualifiedName("ERP", "Customers");

        /**
         * The fully qualified name of the entity type "Sales.Orders".
         */
        static final FullQualifiedName FQN_SALES_ORDERS = new FullQualifiedName("Sales.Orders");

        @Override
        public Future<Set<FullQualifiedName>> entityTypeNames() {
            return succeededFuture(Set.of(FQN_ERP_CUSTOMERS, FQN_SALES_ORDERS));
        }

        @Override
        public Future<EntityWrapper> retrieveData(DataQuery query, DataContext context) {
            return succeededFuture(new EntityWrapper(FQN_ERP_CUSTOMERS, (Entity) null));
        }
    }

    @NeonBeeDeployable(namespace = NeonBeeDeployable.NEONBEE_NAMESPACE, autoDeploy = false)
    public static class TestEntityVerticle2 extends TestEntityVerticle1 {}

    @NeonBeeDeployable(namespace = NeonBeeDeployable.NEONBEE_NAMESPACE, autoDeploy = false)
    public static class TestEntityVerticle3 extends TestEntityVerticle1 {}

    @Test
    @DisplayName("Test that the EntityVerticles that are not deployed are deployed by the RedeployEntitiesJob")
    void testRedeployEntitiesJob(
            @NeonBeeInstanceConfiguration(clustered = true, activeProfiles = { ALL },
                    clusterManager = INFINISPAN) NeonBee node1,
            @NeonBeeInstanceConfiguration(clustered = true, activeProfiles = { ALL },
                    clusterManager = INFINISPAN) NeonBee node2,
            VertxTestContext testContext) {
        ((NeonBeeOptions.Mutable) node1.getOptions()).setDisableJobScheduling(false);

        class RedeployEntitiesTestJob extends RedeployEntitiesJob {

            @Override
            public Future<?> execute(DataContext context) {
                return super.execute(context)
                        .compose(o -> {
                            Registry<String> entityRegistry = node1.getEntityRegistry();
                            ClusterEntityRegistry clusterEntityRegistry = ((ClusterEntityRegistry) entityRegistry);
                            return clusterEntityRegistry
                                    .getClusteringInformation(ClusterHelper.getClusterNodeId(vertx));
                        })
                        .onSuccess(clusteringInformation -> testContext.verify(() -> {
                            assertThat(clusteringInformation).isNotNull();
                            assertThat(clusteringInformation.size()).isEqualTo(6);
                            Set<String> deployedSet = clusteringInformation.stream()
                                    .map(o -> (JsonObject) o)
                                    .map(jo -> jo.getString(ClusterEntityRegistry.QUALIFIED_NAME_KEY))
                                    .map(s -> s.replaceAll("-\\d*", ""))
                                    .collect(Collectors.toSet());
                            assertThat(deployedSet).containsExactly(
                                    "neonbee/_" + TestEntityVerticle1.class.getSimpleName(),
                                    "neonbee/_" + TestEntityVerticle2.class.getSimpleName(),
                                    "neonbee/_" + TestEntityVerticle3.class.getSimpleName());
                            testContext.completeNow();
                        }))
                        .onFailure(testContext::failNow);
            }

            @Override
            boolean filterByAutoDeployAndProfiles(Class<? extends Verticle> verticleClass,
                    Collection<NeonBeeProfile> activeProfiles) {
                return Set.of(
                        TestEntityVerticle1.class,
                        TestEntityVerticle2.class,
                        TestEntityVerticle3.class)
                        .contains(verticleClass);
            }
        }

        node1.getVertx()
                .deployVerticle(new TestEntityVerticle1(), new DeploymentOptions())
                .compose(s -> {
                    Promise<Object> promise = Promise.promise();
                    node1.getVertx().setTimer(1000, event -> promise.complete());
                    return promise.future();
                })
                .compose(s -> node1.getVertx().deployVerticle(new RedeployEntitiesTestJob(), new DeploymentOptions()))
                .onFailure(testContext::failNow);
    }
}
