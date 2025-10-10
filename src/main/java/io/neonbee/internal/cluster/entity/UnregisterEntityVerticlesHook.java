package io.neonbee.internal.cluster.entity;

import static io.neonbee.hook.HookType.CLUSTER_NODE_ID;
import static io.vertx.core.Future.succeededFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.neonbee.NeonBee;
import io.neonbee.hook.Hook;
import io.neonbee.hook.HookContext;
import io.neonbee.hook.HookType;
import io.neonbee.internal.Registry;
import io.neonbee.internal.cluster.ClusterHelper;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;

/**
 * Hooks for unregistering verticle models.
 */
public class UnregisterEntityVerticlesHook {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            UnregisterEntityVerticlesHook.class);

    /**
     * This method is called when a NeonBee instance shutdown gracefully.
     *
     * @param neonBee     the {@link NeonBee} instance
     * @param hookContext the {@link HookContext}
     * @param promise     {@link Promise} to complete the function.
     */
    @Hook(HookType.BEFORE_SHUTDOWN)
    public void unregisterOnShutdown(
            NeonBee neonBee,
            HookContext hookContext,
            Promise<Void> promise) {
        LOGGER.info("Unregistering models on shutdown");
        Vertx vertx = neonBee.getVertx();
        String clusterNodeId = ClusterHelper.getClusterNodeId(vertx);
        unregister(neonBee, clusterNodeId)
                .onComplete(promise)
                .onSuccess(unused -> LOGGER.info("Models unregistered successfully"))
                .onFailure(ignoredCause -> LOGGER.error("Failed to unregister models on shutdown"));
    }

    /**
     * Unregister the entity qualified names for the node by ID.
     *
     * @param neonBee       the {@link NeonBee} instance
     * @param clusterNodeId the ID of the cluster node
     * @return Future
     */
    public static Future<Void> unregister(
            NeonBee neonBee,
            String clusterNodeId) {
        if (!neonBee.getVertx().isClustered()) {
            return succeededFuture();
        }

        Registry<String> registry = neonBee.getEntityRegistry();
        if (!(registry instanceof ClusterEntityRegistry)) {
            LOGGER.warn(
                    "Running in clustered mode but not using the ClusterEntityRegistry.");
            return succeededFuture();
        }

        ClusterEntityRegistry clusterEntityRegistry = (ClusterEntityRegistry) registry;

        LOGGER.info(
                "Unregistering entity verticle models for node ID {} ...",
                clusterNodeId);
        Future<Void> unregisterFuture = clusterEntityRegistry.unregisterNode(
                clusterNodeId);

        return unregisterFuture
                .onSuccess(unused -> LOGGER.info(
                        "Unregistered entity verticle models for node ID {} ...",
                        clusterNodeId))
                .onFailure(cause -> LOGGER.error(
                        "Failed to unregistered entity verticle models for node ID {} ...",
                        clusterNodeId,
                        cause));
    }

    /**
     * This method is called when a NeonBee node has left the cluster. Uses the ClusterCleanupCoordinator for
     * coordinated cleanup processing if NEONBEE_PERSISTENT_CLUSTER_CLEANUP is enabled, otherwise uses direct cleanup.
     *
     * @param neonBee     the {@link NeonBee} instance
     * @param hookContext the {@link HookContext}
     * @param promise     {@link Promise} to completed the function.
     */
    @Hook(HookType.NODE_LEFT)
    public void cleanup(
            NeonBee neonBee,
            HookContext hookContext,
            Promise<Void> promise) {
        String clusterNodeId = hookContext.get(CLUSTER_NODE_ID);
        LOGGER.info("Cleanup qualified names for node {}", clusterNodeId);

        if (ClusterHelper.isLeader(neonBee.getVertx())) {
            LOGGER.info("Cleaning registered qualified names ...");

            // Check if persistent cluster cleanup is enabled via environment variable
            boolean usePersistentCleanup = Boolean.parseBoolean(
                    System.getenv("NEONBEE_PERSISTENT_CLUSTER_CLEANUP"));

            if (usePersistentCleanup) {
                LOGGER.info(
                        "Using ClusterCleanupCoordinator for persistent cleanup processing");
                // Use the cluster cleanup coordinator for coordinated processing
                ClusterHelper
                        .getOrCreateClusterCleanupCoordinator(neonBee.getVertx())
                        .compose(coordinator -> {
                            if (coordinator != null) {
                                coordinator.addNodeLeft(clusterNodeId);
                                return succeededFuture();
                            } else {
                                // Fallback to direct cleanup if coordinator is not available
                                LOGGER.warn(
                                        "ClusterCleanupCoordinator not available, falling back to direct cleanup for node {}",
                                        clusterNodeId);
                                return unregister(neonBee, clusterNodeId);
                            }
                        })
                        .onComplete(promise)
                        .onSuccess(unused -> LOGGER.info(
                                "Qualified names successfully cleaned up via coordinator"))
                        .onFailure(ignoredCause -> LOGGER.error(
                                "Failed to cleanup qualified names via coordinator"));
            } else {
                LOGGER.info(
                        "Using direct cleanup processing (default behavior)");
                // Use the original direct cleanup logic
                unregister(neonBee, clusterNodeId)
                        .onComplete(promise)
                        .onSuccess(unused -> LOGGER.info("Qualified names successfully cleaned up"))
                        .onFailure(ignoredCause -> LOGGER.error("Failed to cleanup qualified names"));
            }
        } else {
            promise.complete(); // Not the leader, no cleanup needed
        }
    }
}
