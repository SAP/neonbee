package io.neonbee.internal.registry;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.hazelcast.partition.PartitionService;

import io.neonbee.internal.cluster.hazelcast.HazelcastMigration;
import io.vertx.core.Future;

/**
 * This Registry adds a Hazelcast-specific behavior.
 * <p>
 * This wrapper delays the method register and unregisterNode method calls when the cluster is not in a safe state and
 * executes the methods when the Hazelcast cluster partition migration process is complete.
 */
public class HazelcastClusterSafeRegistry<T> implements Registry<T> {

    private final HazelcastMigration hazelcastMigration;

    private final Registry<T> registry;

    /**
     * Create a new instance of . This is Registry delegates all calls to the underlining registry, when the cluster is
     * in a save state.
     *
     * @param registry         the underlining registry
     * @param partitionService the {@link PartitionService}
     */
    public HazelcastClusterSafeRegistry(Registry<T> registry, PartitionService partitionService) {
        this.registry = registry;
        hazelcastMigration = new HazelcastMigration(partitionService);
    }

    @Override
    public Future<Void> register(String key, T value) {
        return this.hazelcastMigration
                .onReplicaMigrationFinished("register key \"" + key + "\", value: \"" + value + "\"")
                .compose(unused -> this.registry.register(key, value));
    }

    @Override
    public Future<Void> register(String key, Collection<T> values) {
        return this.hazelcastMigration
                .onReplicaMigrationFinished("register key \"" + key + "\", values: \"" + values + "\"")
                .compose(unused -> this.registry.register(key, values));
    }

    @Override
    public Future<Void> unregister(String key, T value) {
        return this.hazelcastMigration
                .onReplicaMigrationFinished("unregister key \"" + key + "\", value: \"" + value + "\"")
                .compose(unused -> this.registry.unregister(key, value));
    }

    @Override
    public Future<Void> unregister(String key, Collection<T> values) {
        return this.hazelcastMigration
                .onReplicaMigrationFinished("unregister key \"" + key + "\", values: \"" + values + "\"")
                .compose(unused -> this.registry.unregister(key, values));
    }

    @Override
    public Future<List<T>> get(String key) {
        return this.registry.get(key);
    }

    @Override
    public Future<Optional<T>> getAny(String key) {
        return this.registry.getAny(key);
    }

    @Override
    public Future<Set<String>> getKeys() {
        return this.registry.getKeys();
    }
}
