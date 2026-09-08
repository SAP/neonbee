package io.neonbee.cluster;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.test.helper.DeploymentHelper.deployVerticle;

import java.util.Set;
import java.util.concurrent.TimeUnit;

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
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxTestContext;

/**
 * Regression test for the send timeout being dropped on the inner FQN-consumer hop of {@code AbstractEntityVerticle}.
 * <p>
 * Entity verticles register a consumer at the shared FQN address {@code EntityVerticle[<FQN>]} which forwards the
 * request to the co-located {@code DataVerticle[<name>]} instance. The outer hop carries the configured
 * {@link io.neonbee.config.NeonBeeConfig#getEventBusTimeout() eventBusTimeout}, but if the FQN consumer re-issues the
 * inner hop with a freshly constructed {@link io.vertx.core.eventbus.DeliveryOptions} it silently falls back to
 * Vert.x's hardcoded 30s default reply timeout - so a {@code retrieveData} that legitimately takes longer than 30s but
 * less than the configured timeout is abandoned after 30s with a
 * {@code (RECIPIENT_FAILURE,-1) Timed out after 30000(ms)} even though the configured budget had not been exhausted.
 * <p>
 * This test configures {@code eventBusTimeout} to {@value #EVENT_BUS_TIMEOUT_SECONDS}s and makes the served entity
 * reply only after {@value #REPLY_DELAY_SECONDS}s - between the 30s default and the configured budget. It therefore
 * FAILS on the regressed code (30s timeout on the inner hop) and PASSES once the inner hop inherits the configured
 * timeout.
 * <p>
 * It is deliberately slow (~{@value #REPLY_DELAY_SECONDS}s) because the bug can only be reproduced with a real reply
 * arriving between the 30s Vert.x default and the configured timeout; Vert.x 4.5.x exposes no API to read the inner
 * hop's send timeout without waiting for it to fire.
 */
class EntityVerticleTimeoutClusterTest extends NeonBeeExtension.TestBase {

    static final FullQualifiedName ENTITY_FQN = new FullQualifiedName("cluster.test", "SlowEntity");

    private static final String PROPERTY = "served";

    /** Configured event bus timeout - above the 30s Vert.x default so the regression window exists. */
    private static final int EVENT_BUS_TIMEOUT_SECONDS = 40;

    /** Reply delay - between the 30s default and the configured 40s budget. */
    private static final long REPLY_DELAY_SECONDS = 33;

    private NeonBee localNode;

    @BeforeEach
    @DisplayName("Set up two cluster nodes with a raised event bus timeout and deploy the slow entity verticle")
    void setUp(@NeonBeeInstanceConfiguration(clustered = true, activeProfiles = {}) NeonBee localNode,
            @NeonBeeInstanceConfiguration(clustered = true, activeProfiles = {}) NeonBee remoteNode,
            VertxTestContext testContext) {
        this.localNode = localNode;
        // Raise the configured event bus timeout on both nodes above the 30s Vert.x default. deliveryOptions() reads
        // this value per request, so mutating the live config before firing requests is sufficient.
        localNode.getConfig().setEventBusTimeout(EVENT_BUS_TIMEOUT_SECONDS);
        remoteNode.getConfig().setEventBusTimeout(EVENT_BUS_TIMEOUT_SECONDS);
        deployVerticle(localNode.getVertx(), new SlowEntityVerticle())
                .compose(v -> deployVerticle(remoteNode.getVertx(), new SlowEntityVerticle()))
                .onComplete(testContext.succeedingThenComplete());
    }

    @Test
    @Timeout(value = 60, timeUnit = TimeUnit.SECONDS)
    @DisplayName("an entity reply arriving after 30s but within the configured timeout is not abandoned")
    void testReplyWithinConfiguredTimeoutSucceeds(VertxTestContext testContext) {
        EntityVerticle
                .requestEntity(localNode.getVertx(), new DataRequest(ENTITY_FQN, new DataQuery()),
                        new DataContextImpl())
                .onComplete(testContext.succeeding(entityWrapper -> {
                    testContext.verify(() -> {
                        // Reaching a successful reply at all is the assertion: on the regressed code the inner FQN hop
                        // times out at 30s (< REPLY_DELAY_SECONDS) and this future fails instead.
                        assertThat(entityWrapper.getEntities()).hasSize(1);
                        assertThat(entityWrapper.getEntities().get(0).getProperty(PROPERTY).getValue()).isEqualTo(true);
                    });
                    testContext.completeNow();
                }));
    }

    @NeonBeeDeployable(namespace = "cluster", autoDeploy = false)
    public static class SlowEntityVerticle extends EntityVerticle {
        @Override
        public Future<Set<FullQualifiedName>> entityTypeNames() {
            return Future.succeededFuture(Set.of(ENTITY_FQN));
        }

        @Override
        public Future<EntityWrapper> retrieveData(DataQuery query, DataContext context) {
            // Reply only after REPLY_DELAY_SECONDS - longer than the 30s Vert.x default, shorter than the configured
            // timeout - to exercise the regression window on the inner FQN-consumer hop.
            Promise<EntityWrapper> promise = Promise.promise();
            vertx.setTimer(TimeUnit.SECONDS.toMillis(REPLY_DELAY_SECONDS), timerId -> {
                Entity entity = new Entity();
                entity.addProperty(new Property(null, PROPERTY, ValueType.PRIMITIVE, true));
                promise.complete(new EntityWrapper(ENTITY_FQN, entity));
            });
            return promise.future();
        }
    }
}
