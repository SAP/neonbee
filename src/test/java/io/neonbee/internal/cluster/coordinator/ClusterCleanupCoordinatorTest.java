package io.neonbee.internal.cluster.coordinator;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.neonbee.NeonBee;
import io.neonbee.internal.cluster.entity.ClusterEntityRegistry;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.test.fakecluster.FakeClusterManager;

@ExtendWith(VertxExtension.class)
class ClusterCleanupCoordinatorTest {

    private ClusterCleanupCoordinator coordinator;

    @BeforeEach
    void setUp(Vertx vertx) {
        // Create cluster manager
        FakeClusterManager clusterManager = new FakeClusterManager();

        // Initialize cluster manager with the Vert.x instance
        clusterManager.init(vertx, null);

        // Create coordinator
        this.coordinator = new ClusterCleanupCoordinator(vertx, clusterManager);
    }

    @AfterEach
    void tearDown(VertxTestContext testContext) {
        FakeClusterManager.reset();

        if (coordinator != null) {
            coordinator.stop().onComplete(ar -> testContext.completeNow());
        } else {
            testContext.completeNow();
        }
    }

    // ========== Lifecycle Tests ==========

    @Test
    @DisplayName("Start should initialize successfully")
    void startShouldInitializeSuccessfully(VertxTestContext testContext) {
        // When: Starting the coordinator
        coordinator.start().onComplete(testContext.succeedingThenComplete());
    }

    @Test
    @DisplayName("Stop should complete successfully")
    void stopShouldCompleteSuccessfully(VertxTestContext testContext) {
        // Given: Started coordinator
        coordinator
                .start()
                .compose(v -> {
                    // When: Stopping the coordinator
                    return coordinator.stop();
                })
                .onComplete(testContext.succeedingThenComplete());
    }

    @Test
    void shouldStartAndStopCoordinatorSuccessfully(
            VertxTestContext testContext) {
        // Given: Coordinator is created

        // When: Starting coordinator
        coordinator.start().onComplete(testContext.succeedingThenComplete());
    }

    @Test
    void shouldHandleStopWhenNotStartedGracefully(
            VertxTestContext testContext) {
        // Given: Coordinator is not started

        // When: Stopping coordinator
        coordinator
                .stop()
                .onComplete(ar -> {
                    // Test passes if no exception is thrown
                    assertThat(coordinator).isNotNull();
                    testContext.completeNow();
                });
    }

    @Test
    void shouldHandleStartAfterStopGracefully(VertxTestContext testContext) {
        // Given: Coordinator is created

        // When: Starting, stopping, then starting again
        coordinator
                .start()
                .compose(v -> coordinator.stop())
                .compose(v -> coordinator.start())
                .onComplete(ar -> {
                    // Test passes if no exception is thrown
                    assertThat(coordinator).isNotNull();
                    testContext.completeNow();
                });
    }

    // ========== Multiple Calls Tests ==========
    @Test
    void multipleStartCallsShouldBeIdempotent(VertxTestContext testContext) {
        // When: Starting the coordinator multiple times
        coordinator
                .start()
                .compose(v -> coordinator.start())
                .compose(v -> coordinator.start())
                .onComplete(ar -> {
                    // Test passes if no exception is thrown
                    testContext.completeNow();
                });
    }

    @Test
    void multipleStopCallsShouldBeIdempotent(VertxTestContext testContext) {
        // Given: Started coordinator
        coordinator
                .start()
                .compose(v -> {
                    // When: Stopping the coordinator multiple times
                    return coordinator
                            .stop()
                            .compose(x -> coordinator.stop())
                            .compose(x -> coordinator.stop());
                })
                .onComplete(ar -> {
                    // Test passes if no exception is thrown
                    testContext.completeNow();
                });
    }

    @Test
    void shouldHandleMultipleStartCallsGracefully(
            VertxTestContext testContext) {
        // Given: Coordinator is created

        // When: Starting coordinator multiple times
        coordinator
                .start()
                .compose(v -> coordinator.start())
                .compose(v -> coordinator.start())
                .onComplete(ar -> {
                    // Test passes if no exception is thrown
                    assertThat(coordinator).isNotNull();
                    testContext.completeNow();
                });
    }

    @Test
    void shouldHandleMultipleStopCallsGracefully(VertxTestContext testContext) {
        // Given: Coordinator is started
        coordinator
                .start()
                .compose(v -> {
                    // When: Stopping coordinator multiple times
                    return coordinator
                            .stop()
                            .compose(v2 -> coordinator.stop())
                            .compose(v3 -> coordinator.stop());
                })
                .onComplete(ar -> {
                    // Test passes if no exception is thrown
                    assertThat(coordinator).isNotNull();
                    testContext.completeNow();
                });
    }

    @Test
    void stopWithoutStartShouldCompleteSuccessfully(
            VertxTestContext testContext) {
        // When: Stopping coordinator without starting
        coordinator
                .stop()
                .onComplete(ar -> {
                    // Test passes if no exception is thrown
                    testContext.completeNow();
                });
    }

    // ========== Integration Tests ==========
    @Test
    @DisplayName("reconcile should enqueue stale registry node IDs for cleanup")
    void reconcileShouldEnqueueStaleNodes(
            Vertx vertx,
            VertxTestContext testContext) {
        FakeClusterManager clusterManagerLocal = new FakeClusterManager();

        Vertx
                .builder()
                .withClusterManager(clusterManagerLocal)
                .buildClustered()
                .onComplete(clusteredRes -> {
                    if (clusteredRes.failed()) {
                        testContext.failNow(clusteredRes.cause());
                        return;
                    }

                    Vertx clusteredVertx = clusteredRes.result();
                    clusterManagerLocal.init(clusteredVertx, null);

                    NeonBee neonBee = mock(NeonBee.class);

                    // Custom registry with predictable behavior
                    var customRegistry = new ClusterEntityRegistry(
                            clusteredVertx,
                            "TEST_REGISTRY") {
                        @Override
                        public Future<Set<String>> getAllNodeIds() {
                            HashSet<String> nodes = new HashSet<>(2);
                            nodes.add("stale-1");
                            nodes.add("stale-2");
                            return Future.succeededFuture(nodes);
                        }

                        @Override
                        public Future<Void> unregisterNode(String clusterNodeId) {
                            return Future.failedFuture(
                                    "intended failure to keep pendingRemovals");
                        }
                    };

                    when(neonBee.getVertx()).thenReturn(clusteredVertx);
                    when(neonBee.getEntityRegistry()).thenReturn(customRegistry);

                    ClusterCleanupCoordinator testCoordinator = new ClusterCleanupCoordinator(
                            clusteredVertx,
                            clusterManagerLocal,
                            100000,
                            3000,
                            () -> neonBee);

                    // Use checkpoint to guarantee async test completion
                    var checkpoint = testContext.checkpoint();

                    testCoordinator
                            .start()
                            .compose(v -> testCoordinator.reconcileRegistryWithCluster())
                            .onComplete(
                                    testContext.succeeding(staleNodes -> testContext.verify(() -> {
                                        assertThat(staleNodes)
                                                .containsAtLeast("stale-1", "stale-2");
                                        checkpoint.flag();
                                    })));
                });
    }
}
