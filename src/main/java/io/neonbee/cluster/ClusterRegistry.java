package io.neonbee.cluster;

import io.neonbee.registry.Registry;
import io.vertx.core.Future;

/**
 * Interface for an asynchronous registry implementation.
 *
 * @param <T> the type of data this registry stores
 */
public interface ClusterRegistry<T> extends Registry<T> {

    /**
     * Unregister all registered entities for a node by ID.
     *
     * @param clusterNodeId the ID of the cluster node
     * @return the future
     */
    Future<Void> unregisterNode(String clusterNodeId);
}
