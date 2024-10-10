package io.neonbee.internal.hazelcast;

import io.neonbee.internal.cluster.entity.ClusterEntityRegistry;
import io.vertx.core.Vertx;

/**
 * A special registry implementation that stores cluster information's.
 * <p>
 * This implementation stores note specific entries for the registered {@link io.neonbee.entity.EntityVerticle}. The
 * cluster information is stored in a {@link ReplicatedWriteSafeRegistry}.
 */
public class ReplicatedClusterEntityRegistry extends ClusterEntityRegistry {

    /**
     * Create a new instance of {@link ReplicatedClusterEntityRegistry}.
     *
     * @param vertx        the {@link Vertx} instance
     * @param registryName the name of the map registry
     */
    public ReplicatedClusterEntityRegistry(Vertx vertx, String registryName) {
        super(vertx,
                new ReplicatedWriteSafeRegistry<>(vertx, registryName),
                new ReplicatedWriteSafeRegistry<>(vertx, registryName + "#ClusteringInformation"));
    }
}
