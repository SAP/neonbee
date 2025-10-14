package io.neonbee.internal.cluster;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.neonbee.NeonBee;
import io.neonbee.hook.HookContext;
import io.neonbee.internal.cluster.coordinator.ClusterCleanupCoordinator;
import io.neonbee.internal.cluster.coordinator.ClusterCleanupCoordinatorHook;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.test.fakecluster.FakeClusterManager;

@ExtendWith(VertxExtension.class)
final class ClusterCleanupCoordinatorHookTest {

    private ListAppender<ILoggingEvent> listAppender;

    private Logger logger;

    @BeforeEach
    void setupLogger() {
        System.clearProperty("NEONBEE_PERSISTENT_CLUSTER_CLEANUP");
        logger =
                (Logger) LoggerFactory.getLogger(
                        ClusterCleanupCoordinatorHook.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
    }

    @AfterEach
    void tearDown(VertxTestContext testContext) {
        logger.detachAppender(listAppender);
        System.clearProperty("NEONBEE_PERSISTENT_CLUSTER_CLEANUP");

        ClusterCleanupCoordinator coordinator = ClusterHelper.getCachedCoordinator(Vertx.vertx());

        if (coordinator != null) {
            coordinator.stop().onComplete(ar -> testContext.completeNow());
        } else {
            testContext.completeNow();
        }
    }

    @Test
    void testInitializeCoordinatorNotClustered(
            VertxTestContext ctx) {
        FakeClusterManager clusterManager = new FakeClusterManager();
        Vertx clusteredVertx = Vertx
                .builder()
                .build();

        NeonBee neonBee = mock(NeonBee.class);

        clusterManager.init(clusteredVertx, null);
        when(neonBee.getVertx()).thenReturn(clusteredVertx);

        Promise<Void> promise = Promise.promise();
        ClusterCleanupCoordinatorHook.initializeCoordinator(
                neonBee,
                mock(HookContext.class),
                promise);

        promise
                .future()
                .onComplete(
                        ctx.succeeding(v -> {
                            ctx.verify(() -> {
                                assertThat(promise.future().succeeded()).isTrue();
                                assertThat(
                                        listAppender.list
                                                .stream()
                                                .anyMatch(e -> e
                                                        .getFormattedMessage()
                                                        .contains(
                                                                "Not running in clustered mode")))
                                                                        .isTrue();
                            });
                            ctx.completeNow();
                        }));

    }

    // @Test
    void testInitializeCoordinatorSuccessful(
            VertxTestContext ctx) {
        // Arrange
        System.setProperty("NEONBEE_PERSISTENT_CLUSTER_CLEANUP", "true");

        FakeClusterManager clusterManager = new FakeClusterManager();
        Future<Vertx> clusteredFuture = Vertx
                .builder()
                .withClusterManager(clusterManager)
                .buildClustered();

        NeonBee neonBee = mock(NeonBee.class);

        clusteredFuture
                .compose(clusteredVertx -> {
                    clusterManager.init(clusteredVertx, null);
                    when(neonBee.getVertx()).thenReturn(clusteredVertx);

                    Promise<Void> promise = Promise.promise();

                    // Act
                    ClusterCleanupCoordinatorHook.initializeCoordinator(
                            neonBee,
                            mock(HookContext.class),
                            promise);

                    // Assert
                    return promise
                            .future()
                            .onComplete(
                                    ctx.succeeding(v -> {
                                        ctx.verify(() -> assertThat(
                                                listAppender.list
                                                        .stream()
                                                        .anyMatch(e -> e
                                                                .getFormattedMessage()
                                                                .contains(
                                                                        "ClusterCleanupCoordinator initialized successfully")))
                                                                                .isTrue());
                                        ctx.completeNow();
                                    }));
                })
                .onFailure(ctx::failNow);
    }

    // @Test
    void testInitializeCoordinatorFailure(Vertx vertx, VertxTestContext ctx) {
        System.setProperty("NEONBEE_PERSISTENT_CLUSTER_CLEANUP", "true");

        NeonBee neonBee = mock(NeonBee.class);

        // Arrange
        FakeClusterManager clusterManager = new FailingClusterManager();

        Vertx.builder()
                .withClusterManager(clusterManager)
                .buildClustered()
                .onComplete(ctx.succeeding(v -> {
                    Promise<Void> promise = Promise.promise();
                    when(neonBee.getVertx()).thenReturn(v);
                    // Act
                    ClusterCleanupCoordinatorHook.initializeCoordinator(
                            neonBee,
                            mock(HookContext.class),
                            promise);

                    promise.future().onComplete(ctx.failing(throwable -> {
                        ctx.verify(() -> {
                            assertThat(promise.future().failed()).isTrue();
                        });
                        ctx.completeNow();
                    }));
                }))
                .onFailure(ctx::failNow); // If Vert.x clustering fails
    }

    public static class FailingClusterManager extends FakeClusterManager {
        @Override
        public <K, V> void getAsyncMap(String name, Promise<AsyncMap<K, V>> promise) {
            promise.fail(new RuntimeException("Boom"));
        }
    }

    @Test
    void testShutdownCoordinatorNoCoordinator(VertxTestContext ctx) {
        Vertx clustered = mock(Vertx.class);
        when(clustered.isClustered()).thenReturn(true);
        NeonBee neonBee = mock(NeonBee.class);
        when(neonBee.getVertx()).thenReturn(clustered);

        try (
                MockedStatic<ClusterHelper> mocked = mockStatic(ClusterHelper.class)) {
            mocked
                    .when(() -> ClusterHelper.getCachedCoordinator(clustered))
                    .thenReturn(null);

            Promise<Void> promise = Promise.promise();
            ClusterCleanupCoordinatorHook.shutdownCoordinator(
                    neonBee,
                    mock(HookContext.class),
                    promise);

            ctx.verify(() -> {
                assertThat(promise.future().succeeded()).isTrue();
                assertThat(
                        listAppender.list
                                .stream()
                                .anyMatch(e -> e
                                        .getFormattedMessage()
                                        .contains(
                                                "No ClusterCleanupCoordinator found to stop")))
                                                        .isTrue();
                ctx.completeNow();
            });
        }
    }

    // @Test
    void testShutdownCoordinatorSuccess(VertxTestContext ctx) {
        Vertx clustered = mock(Vertx.class);
        when(clustered.isClustered()).thenReturn(true);
        NeonBee neonBee = mock(NeonBee.class);
        when(neonBee.getVertx()).thenReturn(clustered);

        ClusterCleanupCoordinator coordinator = mock(
                ClusterCleanupCoordinator.class);
        when(coordinator.stop()).thenReturn(Future.succeededFuture());

        try (
                MockedStatic<ClusterHelper> mocked = mockStatic(ClusterHelper.class)) {
            mocked
                    .when(() -> ClusterHelper.getCachedCoordinator(clustered))
                    .thenReturn(coordinator);
            mocked
                    .when(() -> ClusterHelper.removeCachedCoordinator(clustered))
                    .thenReturn(null);

            Promise<Void> promise = Promise.promise();
            ClusterCleanupCoordinatorHook.shutdownCoordinator(
                    neonBee,
                    mock(HookContext.class),
                    promise);

            promise
                    .future()
                    .onComplete(
                            ctx.succeeding(result -> {
                                ctx.verify(() -> {
                                    assertThat(
                                            listAppender.list
                                                    .stream()
                                                    .anyMatch(e -> e
                                                            .getFormattedMessage()
                                                            .contains(
                                                                    "ClusterCleanupCoordinator stopped successfully")))
                                                                            .isTrue();
                                    assertThat(promise.future().succeeded()).isTrue();
                                });
                                ctx.completeNow();
                            }));
        }
    }

    // @Test
    void testShutdownCoordinatorFailure(VertxTestContext ctx) {
        Vertx clustered = mock(Vertx.class);
        when(clustered.isClustered()).thenReturn(true);
        NeonBee neonBee = mock(NeonBee.class);
        when(neonBee.getVertx()).thenReturn(clustered);

        ClusterCleanupCoordinator coordinator = mock(
                ClusterCleanupCoordinator.class);
        when(coordinator.stop())
                .thenReturn(
                        Future.failedFuture(new RuntimeException("stop failed")));

        try (
                MockedStatic<ClusterHelper> mocked = mockStatic(ClusterHelper.class)) {
            mocked
                    .when(() -> ClusterHelper.getCachedCoordinator(clustered))
                    .thenReturn(coordinator);
            mocked
                    .when(() -> ClusterHelper.removeCachedCoordinator(clustered))
                    .thenReturn(null);

            Promise<Void> promise = Promise.promise();
            ClusterCleanupCoordinatorHook.shutdownCoordinator(
                    neonBee,
                    mock(HookContext.class),
                    promise);

            promise
                    .future()
                    .onComplete(
                            ctx.succeeding(result -> {
                                ctx.verify(() -> {
                                    assertThat(promise.future().succeeded()).isTrue();
                                    assertThat(
                                            listAppender.list
                                                    .stream()
                                                    .anyMatch(e -> e
                                                            .getFormattedMessage()
                                                            .contains(
                                                                    "Failed to stop ClusterCleanupCoordinator")))
                                                                            .isTrue();
                                });
                                ctx.completeNow();
                            }));
        }
    }
}
