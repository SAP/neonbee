package io.neonbee.internal.cluster.entity;

import static io.neonbee.hook.HookType.CLUSTER_NODE_ID;
import static io.vertx.core.Future.succeededFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.neonbee.NeonBee;
import io.neonbee.cluster.ClusterRegistry;
import io.neonbee.hook.Hook;
import io.neonbee.hook.HookContext;
import io.neonbee.hook.HookType;
import io.neonbee.internal.cluster.ClusterHelper;
import io.neonbee.registry.Registry;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;

/**
 * Hooks for unregistering verticle models.
 */
public class UnregisterEntityVerticlesHook {

    private static final Logger LOGGER = LoggerFactory.getLogger(UnregisterEntityVerticlesHook.class);

    /**
     * This method is called when a NeonBee instance shutdown gracefully.
     *
     * @param neonBee     the {@link NeonBee} instance
     * @param hookContext the {@link HookContext}
     * @param promise     {@link Promise} to complete the function.
     */
    @Hook(HookType.BEFORE_SHUTDOWN)
    public void unregisterOnShutdown(NeonBee neonBee, HookContext hookContext, Promise<Void> promise) {
        LOGGER.info("Unregistering models on shutdown");
        Vertx vertx = neonBee.getVertx();
        String clusterNodeId = ClusterHelper.getClusterNodeId(vertx);
        unregister(neonBee, clusterNodeId).onComplete(promise)
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
    public static Future<Void> unregister(NeonBee neonBee, String clusterNodeId) {
        if (!neonBee.getVertx().isClustered()) {
            return succeededFuture();
        }

        Registry<String> registry = neonBee.getEntityRegistry();
        if (!(registry instanceof ClusterRegistry<String>)) {
            LOGGER.warn("Running in clustered mode but not using a ClusterRegistry.");
            return succeededFuture();
        }

        ClusterRegistry<String> clusterEntityRegistry = (ClusterRegistry) registry;

        LOGGER.info("Unregistering entity verticle models for node ID {} ...", clusterNodeId);
        Future<Void> unregisterFuture = clusterEntityRegistry.unregisterNode(clusterNodeId);

        return unregisterFuture
                .onSuccess(
                        unused -> LOGGER.info("Unregistered entity verticle models for node ID {} ...", clusterNodeId))
                .onFailure(cause -> LOGGER.error("Failed to unregistered entity verticle models for node ID {} ...",
                        clusterNodeId, cause));
    }

    /**
     * This method is called when a NeonBee node has left the cluster.
     *
     * @param neonBee     the {@link NeonBee} instance
     * @param hookContext the {@link HookContext}
     * @param promise     {@link Promise} to completed the function.
     */
    @Hook(HookType.NODE_LEFT)
    public void cleanup(NeonBee neonBee, HookContext hookContext, Promise<Void> promise) {
        String clusterNodeId = hookContext.get(CLUSTER_NODE_ID);
        LOGGER.info("Cleanup qualified names for node {}", clusterNodeId);
        if (ClusterHelper.isLeader(neonBee.getVertx())) {
            LOGGER.info("Cleaning registered qualified names ...");
            unregister(neonBee, clusterNodeId).onComplete(promise)
                    .onSuccess(unused -> LOGGER.info("Qualified names successfully cleaned up"))
                    .onFailure(ignoredCause -> LOGGER.error("Failed to cleanup qualified names"));
        }
    }

}
