package io.neonbee.internal.cluster;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.neonbee.NeonBee;
import io.neonbee.hook.HookContext;
import io.neonbee.internal.cluster.coordinator.ClusterCleanupCoordinatorHook;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class ClusterCleanupCoordinatorHookTest {

    private ClusterCleanupCoordinatorHook hook;

    private NeonBee mockNeonBee;

    private Vertx vertx;

    private String originalEnvVar;

    @BeforeEach
    void setUp(Vertx vertx) {
        this.vertx = vertx;
        this.hook = new ClusterCleanupCoordinatorHook();
        this.mockNeonBee = mock(NeonBee.class);
        when(mockNeonBee.getVertx()).thenReturn(vertx);

        // Store original system property value
        originalEnvVar =
                System.getProperty("NEONBEE_PERSISTENT_CLUSTER_CLEANUP");
    }

    @AfterEach
    void tearDown() {
        // Restore original system property
        if (originalEnvVar != null) {
            System.setProperty(
                    "NEONBEE_PERSISTENT_CLUSTER_CLEANUP",
                    originalEnvVar);
        } else {
            System.clearProperty("NEONBEE_PERSISTENT_CLUSTER_CLEANUP");
        }

        // Clear any cached coordinators
        ClusterHelper.removeCachedCoordinator(vertx);
    }

    @Test
    @DisplayName("AFTER_STARTUP: Should skip initialization when not clustered")
    void afterStartupNotClusteredSkipsInitialization(
            VertxTestContext testContext) {
        // Given: Non-clustered Vert.x (real Vert.x instance is not clustered by default)

        Promise<Void> promise = Promise.promise();
        promise.future().onComplete(testContext.succeedingThenComplete());

        // When: Hook is called
        hook.initializeCoordinator(
                mockNeonBee,
                mock(HookContext.class),
                promise);

        // Then: Promise should complete successfully
        testContext.verify(() -> {
            assertThat(promise.future().succeeded()).isTrue();
            // Verify no coordinator was created
            assertThat(ClusterHelper.getCachedCoordinator(vertx)).isNull();
        });
    }

    @Test
    @DisplayName("AFTER_STARTUP: Should skip initialization when system property disabled")
    void afterStartupSystemPropertyDisabledSkipsInitialization(
            VertxTestContext testContext) {
        // Given: Clustered Vert.x but system property disabled
        System.setProperty("NEONBEE_PERSISTENT_CLUSTER_CLEANUP", "false");

        Promise<Void> promise = Promise.promise();
        promise.future().onComplete(testContext.succeedingThenComplete());

        // When: Hook is called
        hook.initializeCoordinator(
                mockNeonBee,
                mock(HookContext.class),
                promise);

        // Then: Promise should complete successfully
        testContext.verify(() -> {
            assertThat(promise.future().succeeded()).isTrue();
            // Verify no coordinator was created
            assertThat(ClusterHelper.getCachedCoordinator(vertx)).isNull();
        });
    }

    @Test
    @DisplayName("BEFORE_SHUTDOWN: Should skip shutdown when not clustered")
    void beforeShutdownNotClusteredSkipsShutdown(VertxTestContext testContext) {
        // Given: Non-clustered Vert.x (real Vert.x instance is not clustered by default)

        Promise<Void> promise = Promise.promise();
        promise.future().onComplete(testContext.succeedingThenComplete());

        // When: Hook is called
        hook.shutdownCoordinator(mockNeonBee, mock(HookContext.class), promise);

        // Then: Promise should complete successfully
        testContext.verify(() -> {
            assertThat(promise.future().succeeded()).isTrue();
        });
    }

    @Test
    @DisplayName("BEFORE_SHUTDOWN: Should complete when no coordinator exists")
    void beforeShutdownNoCoordinatorCompletesSuccessfully(
            VertxTestContext testContext) {
        // Given: Clustered Vert.x but no coordinator cached

        Promise<Void> promise = Promise.promise();
        promise.future().onComplete(testContext.succeedingThenComplete());

        // When: Hook is called
        hook.shutdownCoordinator(mockNeonBee, mock(HookContext.class), promise);

        // Then: Promise should complete successfully
        testContext.verify(() -> {
            assertThat(promise.future().succeeded()).isTrue();
        });
    }
}
