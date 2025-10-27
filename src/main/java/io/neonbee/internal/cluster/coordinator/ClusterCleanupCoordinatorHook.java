package io.neonbee.internal.cluster.coordinator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.neonbee.NeonBee;
import io.neonbee.hook.Hook;
import io.neonbee.hook.HookContext;
import io.neonbee.hook.HookType;
import io.neonbee.internal.cluster.ClusterHelper;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;

/**
 * Hook for initializing the ClusterCleanupCoordinator after NeonBee startup. This ensures the coordinator is ready to
 * handle node left events immediately.
 */
public final class ClusterCleanupCoordinatorHook {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            ClusterCleanupCoordinatorHook.class);

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    public ClusterCleanupCoordinatorHook() {
        // Utility class - do not instantiate
    }

    /**
     * This method is called after NeonBee has been initialized successfully. It initializes the
     * ClusterCleanupCoordinator if running in clustered mode and persistent cluster cleanup is enabled.
     *
     * @param neonBee     the {@link NeonBee} instance
     * @param hookContext the {@link HookContext}
     * @param promise     {@link Promise} to complete the function.
     */
    @Hook(HookType.AFTER_STARTUP)
    public static void initializeCoordinator(
            NeonBee neonBee,
            HookContext hookContext,
            Promise<Void> promise) {
        Vertx vertx = neonBee.getVertx();

        // Only initialize if running in clustered mode
        if (!vertx.isClustered()) {
            promise.complete();
            return;
        }

        LOGGER.info("Initializing ClusterCleanupCoordinator after startup");
        ClusterHelper.getOrCreateClusterCleanupCoordinatorImmediate(vertx);
        promise.complete();
    }

    /**
     * This method is called before NeonBee shutdown. It stops the ClusterCleanupCoordinator to ensure proper cleanup.
     *
     * @param neonBee     the {@link NeonBee} instance
     * @param hookContext the {@link HookContext}
     * @param promise     {@link Promise} to complete the function.
     */
    @Hook(HookType.BEFORE_SHUTDOWN)
    public static void shutdownCoordinator(
            NeonBee neonBee,
            HookContext hookContext,
            Promise<Void> promise) {
        Vertx vertx = neonBee.getVertx();

        // Only cleanup if running in clustered mode
        if (!vertx.isClustered()) {
            promise.complete();
            return;
        }

        LOGGER.info("Stopping ClusterCleanupCoordinator before shutdown");

        // Get the coordinator from the cache and stop it
        ClusterCleanupCoordinator coordinator = ClusterHelper.getCachedCoordinator(
                vertx);
        if (coordinator != null) {
            coordinator
                    .stop()
                    .onSuccess(unused -> {
                        LOGGER.info(
                                "ClusterCleanupCoordinator stopped successfully");
                        // Remove from cache
                        ClusterHelper.removeCachedCoordinator(vertx);
                        promise.complete();
                    })
                    .onFailure(cause -> {
                        LOGGER.error(
                                "Failed to stop ClusterCleanupCoordinator",
                                cause);
                        // Still remove from cache even if stop failed
                        ClusterHelper.removeCachedCoordinator(vertx);
                        promise.complete(); // Don't fail shutdown for this
                    });
        } else {
            LOGGER.warn("No ClusterCleanupCoordinator found to stop");
            promise.complete();
        }
    }
}
