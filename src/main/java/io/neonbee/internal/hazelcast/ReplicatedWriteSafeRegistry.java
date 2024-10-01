package io.neonbee.internal.hazelcast;

import io.neonbee.internal.WriteSafeRegistry;
import io.vertx.core.Vertx;

/**
 * A special registry implementation that stores cluster information's.
 * <p>
 * This implementation stores note specific entries for the registered {@link io.neonbee.entity.EntityVerticle}. The
 * cluster information is stored using a {@link ReplicatedDataAccessor}.
 *
 * @param <T> the type of data this registry stores
 */
public class ReplicatedWriteSafeRegistry<T> extends WriteSafeRegistry<T> {

    /**
     * Create a new instance of {@link ReplicatedWriteSafeRegistry}.
     *
     * @param vertx        the {@link Vertx} instance
     * @param registryName the name of the map registry
     */
    ReplicatedWriteSafeRegistry(Vertx vertx, String registryName) {
        super(registryName, new ReplicatedDataAccessor(vertx, WriteSafeRegistry.class));
    }
}
