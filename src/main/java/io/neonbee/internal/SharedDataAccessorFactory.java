package io.neonbee.internal;

import io.neonbee.NeonBee;
import io.neonbee.config.NeonBeeConfig;
import io.neonbee.internal.cluster.ClusterHelper;
import io.neonbee.internal.hazelcast.ReplicatedDataAccessor;
import io.vertx.core.Vertx;

/**
 * Factory to create a {@link SharedDataAccessor} based on the configuration.
 */
public class SharedDataAccessorFactory {
    private final NeonBee neonBee;

    /**
     * Create a new instance of {@link SharedDataAccessorFactory}.
     */
    public SharedDataAccessorFactory() {
        this.neonBee = NeonBee.get();
    }

    /**
     * Create a new instance of {@link SharedDataAccessorFactory}.
     *
     * @param vertx the Vert.x instance
     */
    public SharedDataAccessorFactory(Vertx vertx) {
        this.neonBee = NeonBee.get(vertx);
    }

    /**
     * Create a new instance of {@link SharedDataAccessorFactory}.
     *
     * @param neonBee the NeonBee instance
     */
    public SharedDataAccessorFactory(NeonBee neonBee) {
        this.neonBee = neonBee;
    }

    private boolean useHazelcastReplicatedMaps() {
        NeonBeeConfig config = neonBee.getConfig();
        return config.isUseReplicatedMaps() && ClusterHelper.getHazelcastClusterManager(neonBee.getVertx()).isPresent();
    }

    /**
     * Get a {@link SharedDataAccessor} based on the configuration.
     *
     * @param accessClass the class to access the shared data
     * @return the shared data accessor
     */
    public SharedDataAccessor getSharedDataAccessor(Class<?> accessClass) {
        if (useHazelcastReplicatedMaps()) {
            return new ReplicatedDataAccessor(neonBee.getVertx(), accessClass);
        } else {
            return new SharedDataAccessor(neonBee.getVertx(), accessClass);
        }
    }
}
