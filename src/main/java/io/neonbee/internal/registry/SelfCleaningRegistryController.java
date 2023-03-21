package io.neonbee.internal.registry;

import static io.neonbee.internal.helper.AsyncHelper.allComposite;
import static io.neonbee.internal.registry.SelfCleaningRegistry.NODE_ID_SEPARATOR;
import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;

import io.neonbee.internal.SharedDataAccessor;
import io.neonbee.internal.cluster.ClusterHelper;
import io.neonbee.internal.helper.SharedDataHelper;
import io.neonbee.logging.LoggingFacade;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.shareddata.AsyncMap;

public class SelfCleaningRegistryController extends WriteSafeRegistry<String> {
    private static final LoggingFacade LOGGER = LoggingFacade.create();

    private static final String REGISTRY_NAME = "SelfCleaningRegistryControllerRegistry";

    private static final String DUMMY_NODE_ID = "dummyNodeId";

    private final SharedDataAccessor selfCleaningRegistryAccessor;

    private final SharedDataAccessor readOnlyMapAccessor;

    private final String nodeId;

    /**
     * Creates a new SelfCleaningRegistryController instance.
     *
     * @param vertx the related Vert.x instance
     */
    public SelfCleaningRegistryController(Vertx vertx) {
        super(vertx, REGISTRY_NAME);
        this.selfCleaningRegistryAccessor = new SharedDataAccessor(vertx, SelfCleaningRegistry.class);
        this.readOnlyMapAccessor = new SharedDataAccessor(vertx, WriteSafeRegistry.class);
        if (vertx.isClustered()) {
            this.nodeId = ClusterHelper.getClusterNodeId(vertx);
        } else {
            // Isn't clustered node id is irrelevant
            this.nodeId = DUMMY_NODE_ID;
        }
    }

    /**
     * Method to determine the cluster node id.
     *
     * @return The cluster node id, or a dummy id in case Vert.x isn't clustered.
     */
    public String getNodeId() {
        if (vertx.isClustered()) {
            return ClusterHelper.getClusterNodeId(vertx);
        } else {
            // Isn't clustered node id is irrelevant
            return DUMMY_NODE_ID;
        }
    }

    /**
     * Refreshes the read only map of the passed registry.
     *
     * @param registryName The name of the registry
     * @return a succeeded or failed future depending on the success of the refresh.
     */
    public Future<Void> refreshReadOnlyMap(String registryName) {
        LOGGER.debug("Begin to refresh read only map of registry \"{}\" at node \"{}\"", registryName, nodeId);
        Future<Void> refreshedFuture = modifyReadOnlyMap(registryName, () -> getEntriesOfRegistry(registryName)
                .map(this::accumulateValues)
                .compose(accumulatedValues -> setReadOnlyValues(registryName, accumulatedValues)));

        return refreshedFuture
                .onFailure(t -> LOGGER.debug("Failed to refresh readOnlyMap for registry \"{}\"", registryName, t))
                .onSuccess(v -> LOGGER.debug("Refresh readOnlyMap for registry \"{}\" finished.", registryName));
    }

    /**
     * Loops over every registry and removes all entries related to the passed node.
     *
     * @param nodeId The nodeId to clean up
     * @return a succeeded or failed future depending on the success of the cleanup.
     */
    public Future<Void> cleanUpAllRegistriesForNode(String nodeId) {
        return getKeys().compose(registryNames -> {
            List<Future<Void>> cleanedRegistries = registryNames.stream()
                    .map(regName -> removeAllKeysForNode(nodeId, regName)
                            .compose(v -> refreshReadOnlyMap(regName)))
                    .collect(toList());
            return allComposite(cleanedRegistries).mapEmpty();
        });
    }

    /**
     * Registers the passed registry so that it can be cleaned up.
     *
     * @param registryName The name of the registry to add
     * @return a succeeded or failed future depending on the success of the registration.
     */
    public Future<Void> addRegistry(String registryName) {
        return register(registryName, nodeId);
    }

    private Future<Void> removeAllKeysForNode(String nodeId, String registryName) {
        LOGGER.debug("Begin to remove all values for node \"{}\" in registry \"{}\"", nodeId, registryName);

        Future<List<String>> entriesToRemove = getEntriesOfRegistry(registryName).map(entries -> {
            return entries.keySet().stream().filter(entry -> entry.endsWith(nodeId)).collect(toList());
        });

        Future<Void> valuesRemovedFuture =
                entriesToRemove.compose(entries -> getRegistryMap(registryName).compose(registryMap -> {
                    return allComposite(entries.stream().map(registryMap::remove).collect(toList()));
                })).mapEmpty();

        return valuesRemovedFuture
                .onFailure(t -> LOGGER.debug("Failed to remove all values for node \"{}\" in registry \"{}\" finished",
                        nodeId, registryName, t))
                .onSuccess(v -> LOGGER.debug("Remove all values for node \"{}\" in registry \"{}\" finished", nodeId,
                        registryName));
    }

    private Future<Void> modifyReadOnlyMap(String registryName, Supplier<Future<Void>> action) {
        return SharedDataHelper.lock(vertx, getReadOnlyMapName(registryName), action);
    }

    private Future<Map<String, JsonArray>> getEntriesOfRegistry(String registryName) {
        return getRegistryMap(registryName).compose(AsyncMap::entries);
    }

    private Future<AsyncMap<String, JsonArray>> getRegistryMap(String registryName) {
        return selfCleaningRegistryAccessor.getAsyncMap(registryName);
    }

    private Future<AsyncMap<String, JsonArray>> getReadOnlyMap(String registryName) {
        return readOnlyMapAccessor.getAsyncMap(getReadOnlyMapName(registryName));
    }

    private Future<Void> setReadOnlyValues(String registryName, Map<String, JsonArray> accumulatedValues) {
        return getReadOnlyMap(registryName).compose(readOnlyMap -> readOnlyMap.clear().compose(v -> {
            List<Future<Void>> written = new ArrayList<>();
            accumulatedValues.forEach((key, values) -> written.add(readOnlyMap.put(key, values)));
            return allComposite(written).mapEmpty();
        }));
    }

    /**
     * Loops over the entries of the related registry and merges the node separated values.
     *
     * <pre>
     * "Users[###]Node1": ["Foo"]
     * "Users[###]Node2": ["Bar"]
     *
     * becomes
     *
     * "Users: ["Foo", "Bar"]
     * </pre>
     *
     *
     * @param nodeSeparatedValues map with unmerged entries.
     * @return a map with merged entries.
     */
    private Map<String, JsonArray> accumulateValues(Map<String, JsonArray> nodeSeparatedValues) {
        Map<String, JsonArray> accumulatedValues = new HashMap<>();
        nodeSeparatedValues.forEach((currentKeyWithSuffix, valuesOfEntry) -> {
            String keyWithoutSuffix = removeNodeSuffix(currentKeyWithSuffix);
            JsonArray values = accumulatedValues.computeIfAbsent(keyWithoutSuffix, s -> new JsonArray());
            valuesOfEntry.stream().filter(Predicate.not(values::contains)).forEach(values::add);
        });
        return accumulatedValues;
    }

    private String removeNodeSuffix(String key) {
        return key.substring(0, key.indexOf(NODE_ID_SEPARATOR));
    }

    private String getReadOnlyMapName(String registryName) {
        return registryName + SelfCleaningRegistry.READ_ONLY_MAP_SUFFIX;
    }
}
