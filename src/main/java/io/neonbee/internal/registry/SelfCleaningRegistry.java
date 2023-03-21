package io.neonbee.internal.registry;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import io.vertx.core.Future;
import io.vertx.core.Vertx;

public final class SelfCleaningRegistry<T> extends WriteSafeRegistry<T> {
    static final String READ_ONLY_MAP_SUFFIX = "#readOnlyMap";

    static final String NODE_ID_SEPARATOR = "[###]";

    private final Registry<T> readOnlyRegistry;

    private final String nodeSuffix;

    private final SelfCleaningRegistryController controller;

    /**
     * Creates a new {@link SelfCleaningRegistry} that stores the registered values by cluster node and removes them,
     * when the node leaves the cluster.
     *
     * @param vertx        the related {@link Vertx} instance
     * @param registryName the name of the map registry
     * @param <T>          The type for the registry values. Only JSON data types fully supported.
     * @return a succeeded or failed future depending on the success of the registry creation.
     */
    public static <T> Future<SelfCleaningRegistry<T>> create(Vertx vertx, String registryName) {
        SelfCleaningRegistryController controller = new SelfCleaningRegistryController(vertx);
        return controller.addRegistry(registryName).map(new SelfCleaningRegistry<>(vertx, registryName, controller));
    }

    private SelfCleaningRegistry(Vertx vertx, String registryName, SelfCleaningRegistryController controller) {
        super(vertx, registryName);
        this.readOnlyRegistry = new WriteSafeRegistry<>(vertx, registryName + READ_ONLY_MAP_SUFFIX);
        this.controller = controller;
        this.nodeSuffix = NODE_ID_SEPARATOR + controller.getNodeId();
    }

    @Override
    public Future<Void> register(String key, Collection<T> values) {
        return super.register(addNodeSuffix(key), values, () -> controller.refreshReadOnlyMap(registryName));
    }

    @Override
    public Future<Void> unregister(String key, Collection<T> values) {
        return super.unregister(addNodeSuffix(key), values, () -> controller.refreshReadOnlyMap(registryName));
    }

    @Override
    public Future<List<T>> get(String key) {
        return readOnlyRegistry.get(key);
    }

    @Override
    public Future<Set<String>> getKeys() {
        return readOnlyRegistry.getKeys();
    }

    private String addNodeSuffix(String key) {
        return key + nodeSuffix;
    }
}
