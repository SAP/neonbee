package io.neonbee.cluster.spi;

import io.neonbee.NeonBeeOptions;
import io.vertx.core.Future;
import io.vertx.core.spi.cluster.ClusterManager;

/**
 * Service provider interface for cluster manager implementations. Implementations of this interface will be discovered
 * via Java's ServiceLoader.
 */
public interface ClusterManagerProvider {
    /**
     * Get the type identifier for this cluster manager.
     *
     * @return the type identifier.
     */
    String getType();

    /**
     * Create a new cluster manager instance.
     *
     * @param options The NeonBee options.
     * @return a Future holding the created ClusterManager.
     */
    Future<ClusterManager> create(NeonBeeOptions options);
}
