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
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
final class ClusterCleanupCoordinatorHookTest {

    private ListAppender<ILoggingEvent> listAppender;

    private Logger logger;

    @BeforeEach
    void setupLogger() {
        logger =
                (Logger) LoggerFactory.getLogger(
                        ClusterCleanupCoordinatorHook.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(listAppender);
        System.clearProperty("NEONBEE_PERSISTENT_CLUSTER_CLEANUP");
    }

    @Test
    void testInitializeCoordinatorNotClustered(
            Vertx vertx,
            VertxTestContext ctx) {
        NeonBee neonBee = mock(NeonBee.class);
        when(neonBee.getVertx()).thenReturn(vertx);

        Promise<Void> promise = Promise.promise();
        ClusterCleanupCoordinatorHook.initializeCoordinator(
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
                                    .contains("Not running in clustered mode")))
                                            .isTrue();
            ctx.completeNow();
        });
    }

    @Test
    void testInitializeCoordinatorSuccessful(VertxTestContext ctx) {
        System.setProperty("NEONBEE_PERSISTENT_CLUSTER_CLEANUP", "true");
        Vertx clustered = mock(Vertx.class);
        when(clustered.isClustered()).thenReturn(true);
        NeonBee neonBee = mock(NeonBee.class);
        when(neonBee.getVertx()).thenReturn(clustered);

        ClusterCleanupCoordinator mockCoordinator = mock(
                ClusterCleanupCoordinator.class);
        try (
                MockedStatic<ClusterHelper> mocked = mockStatic(ClusterHelper.class)) {
            mocked
                    .when(() -> ClusterHelper.getOrCreateClusterCleanupCoordinator(
                            clustered))
                    .thenReturn(Future.succeededFuture(mockCoordinator));

            Promise<Void> promise = Promise.promise();
            ClusterCleanupCoordinatorHook.initializeCoordinator(
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
                                                                    "ClusterCleanupCoordinator initialized successfully")))
                                                                            .isTrue();
                                });
                                ctx.completeNow();
                            }));
        }
    }

    @Test
    void testInitializeCoordinatorFailure(VertxTestContext ctx) {
        System.setProperty("NEONBEE_PERSISTENT_CLUSTER_CLEANUP", "true");
        Vertx clustered = mock(Vertx.class);
        when(clustered.isClustered()).thenReturn(true);
        NeonBee neonBee = mock(NeonBee.class);
        when(neonBee.getVertx()).thenReturn(clustered);

        Throwable cause = new RuntimeException("Boom");
        try (
                MockedStatic<ClusterHelper> mocked = mockStatic(ClusterHelper.class)) {
            mocked
                    .when(() -> ClusterHelper.getOrCreateClusterCleanupCoordinator(
                            clustered))
                    .thenReturn(Future.failedFuture(cause));

            Promise<Void> promise = Promise.promise();
            ClusterCleanupCoordinatorHook.initializeCoordinator(
                    neonBee,
                    mock(HookContext.class),
                    promise);

            promise
                    .future()
                    .onComplete(
                            ctx.failing(throwable -> {
                                ctx.verify(() -> {
                                    assertThat(promise.future().failed()).isTrue();
                                    assertThat(promise.future().cause())
                                            .isSameInstanceAs(cause);
                                    assertThat(
                                            listAppender.list
                                                    .stream()
                                                    .anyMatch(e -> e
                                                            .getFormattedMessage()
                                                            .contains(
                                                                    "Failed to initialize ClusterCleanupCoordinator")))
                                                                            .isTrue();
                                });
                                ctx.completeNow();
                            }));
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

    @Test
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

    @Test
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
