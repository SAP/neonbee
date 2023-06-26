package io.neonbee.internal.registry;

import static io.neonbee.hook.HookType.CLUSTER_NODE_ID;

import com.hazelcast.core.HazelcastInstance;

import io.neonbee.NeonBee;
import io.neonbee.hook.Hook;
import io.neonbee.hook.HookContext;
import io.neonbee.hook.HookType;
import io.neonbee.internal.cluster.ClusterHelper;
import io.neonbee.logging.LoggingFacade;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;

public class SelfCleaningRegistryHook {
    private static final LoggingFacade LOGGER = LoggingFacade.create();

    /**
     * This method is called when a NeonBee instance shutdown gracefully.
     *
     * @param neonBee     the {@link NeonBee} instance
     * @param hookContext the {@link HookContext}
     * @param promise     {@link Promise} to complete the function.
     */
    @Hook(HookType.BEFORE_SHUTDOWN)
    public void unregisterOnShutdown(NeonBee neonBee, HookContext hookContext, Promise<Void> promise) {
        SelfCleaningRegistryController controller = getController(neonBee.getVertx());

        String nodeId = controller.getNodeId();
        LOGGER.debug("Execute BEFORE_SHUTDOWN hook for SelfCleaningRegistry on node \"{}\"", nodeId);
        controller.cleanUpAllRegistriesForNode(nodeId).onSuccess(
                v -> LOGGER.debug("Finished BEFORE_SHUTDOWN hook for SelfCleaningRegistry on node \"{}\"", nodeId))
                .onComplete(promise);
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
        if (ClusterHelper.isLeader(neonBee.getVertx())) {
            String nodeId = hookContext.get(CLUSTER_NODE_ID);
            SelfCleaningRegistryController controller = getController(neonBee.getVertx());

            String currentNodeId = controller.getNodeId();
            LOGGER.debug("Execute NODE_LEFT hook for SelfCleaningRegistry on node \"{}\" for node \"{}\"",
                    currentNodeId, nodeId);
            controller.cleanUpAllRegistriesForNode(nodeId)
                    .onSuccess(v -> LOGGER.debug(
                            "Finished NODE_LEFT hook for SelfCleaningRegistry on node \"{}\" for node \"{}\"",
                            currentNodeId, nodeId))
                    .onComplete(promise);
        } else {
            promise.complete();
        }
    }

    private static SelfCleaningRegistryController getController(Vertx vertx) {
        return ClusterHelper.getHazelcastClusterManager(vertx)
                .map(HazelcastClusterManager::getHazelcastInstance)
                .map(HazelcastInstance::getPartitionService)
                .map(partitionService -> (SelfCleaningRegistryController) new HazelcastClusterSafeRegistryController(
                        vertx, partitionService))
                .orElseGet(() -> new SelfCleaningRegistryController(vertx));
    }
}
