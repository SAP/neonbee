package io.neonbee.internal.hazelcast;

import static io.neonbee.NeonBeeInstanceConfiguration.ClusterManager.HAZELCAST;
import static io.neonbee.test.base.NeonBeeTestBase.LONG_RUNNING_TEST;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;

import io.neonbee.NeonBee;
import io.neonbee.NeonBeeExtension;
import io.neonbee.NeonBeeInstanceConfiguration;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxTestContext;

@Tag(LONG_RUNNING_TEST)
@Execution(ExecutionMode.SAME_THREAD)
@ExtendWith(NeonBeeExtension.class)
@Isolated
class ReplicatedDataAccessorTest {

    @Test
    void getAsyncMap(
            @NeonBeeInstanceConfiguration(clustered = true, activeProfiles = {},
                    clusterManager = HAZELCAST) NeonBee neonBee,
            VertxTestContext testContext) {
        var dataAccessor = new ReplicatedDataAccessor(neonBee.getVertx(), ReplicatedDataAccessorTest.class);

        dataAccessor.<String, String>getAsyncMap("testMap")
                .onSuccess(asyncMap -> testContext.verify(() -> {
                    assertInstanceOf(ReplicatedAsyncMap.class, asyncMap);
                    testContext.completeNow();

                })).onFailure(testContext::failNow);
    }

    @Test
    void getAsyncMapHandler(
            @NeonBeeInstanceConfiguration(clustered = true, activeProfiles = {},
                    clusterManager = HAZELCAST) NeonBee neonBee,
            VertxTestContext testContext) {
        var dataAccessor = new ReplicatedDataAccessor(neonBee.getVertx(), ReplicatedDataAccessorTest.class);

        dataAccessor.<String, String>getAsyncMap("testMap", asyncMap -> testContext.verify(() -> {
            if (asyncMap.succeeded()) {
                assertInstanceOf(ReplicatedAsyncMap.class, asyncMap.result());
                testContext.completeNow();
            } else {
                testContext.failNow(asyncMap.cause());
            }
        }));
    }

    @Test
    void getAsyncMapWithoutHazelcast(
            VertxTestContext testContext) {
        Vertx vertx = Vertx.vertx();
        ReplicatedDataAccessor dataAccessor = new ReplicatedDataAccessor(vertx, ReplicatedDataAccessorTest.class);

        dataAccessor.<String, String>getAsyncMap("testMap")
                .onSuccess(asyncMap -> testContext.verify(() -> {
                    assertNotNull(asyncMap);
                    // Since Hazelcast is null, it should delegate to super.getAsyncMap
                    assertFalse(asyncMap instanceof ReplicatedAsyncMap);
                    testContext.completeNow();
                })).onFailure(testContext::failNow)
                .compose(event -> vertx.close());
    }

    @Test
    void getClusterWideMap(
            @NeonBeeInstanceConfiguration(clustered = true, activeProfiles = {},
                    clusterManager = HAZELCAST) NeonBee neonBee,
            VertxTestContext testContext) {
        ReplicatedDataAccessor dataAccessor =
                new ReplicatedDataAccessor(neonBee.getVertx(), ReplicatedDataAccessorTest.class);

        dataAccessor.<String, String>getClusterWideMap("clusterMap")
                .onSuccess(asyncMap -> testContext.verify(() -> {
                    assertInstanceOf(ReplicatedAsyncMap.class, asyncMap);
                    testContext.completeNow();
                }))
                .onFailure(testContext::failNow);
    }

    @Test
    void getClusterWideMapHandler(
            @NeonBeeInstanceConfiguration(clustered = true, activeProfiles = {},
                    clusterManager = HAZELCAST) NeonBee neonBee,
            VertxTestContext testContext) {
        ReplicatedDataAccessor dataAccessor =
                new ReplicatedDataAccessor(neonBee.getVertx(), ReplicatedDataAccessorTest.class);

        dataAccessor.<String, String>getClusterWideMap("clusterMap", asyncMap -> testContext.verify(() -> {
            if (asyncMap.succeeded()) {
                assertInstanceOf(ReplicatedAsyncMap.class, asyncMap.result());
                testContext.completeNow();
            } else {
                testContext.failNow(asyncMap.cause());
            }
        }));
    }

}
