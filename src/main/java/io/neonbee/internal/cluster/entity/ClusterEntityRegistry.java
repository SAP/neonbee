package io.neonbee.internal.cluster.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.common.annotations.VisibleForTesting;

import io.neonbee.entity.EntityVerticle;
import io.neonbee.internal.Registry;
import io.neonbee.internal.WriteSafeRegistry;
import io.neonbee.internal.cluster.ClusterHelper;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.AsyncMap;

/**
 * A special registry implementation that stores cluster information's.
 * <p>
 * This implementation stores note specific entries for the registered {@link EntityVerticle}. The cluster information
 * is stored in a JsonObject:
 *
 * <pre>
 * {
 *     "qualifiedName": "value",
 *     "entityName": "key"
 * }
 * </pre>
 */
public class ClusterEntityRegistry implements Registry<String> {

    /**
     * The key for the qualified name.
     */
    public static final String QUALIFIED_NAME_KEY = "qualifiedName";

    /**
     * The key for the entity name.
     */
    public static final String ENTITY_NAME_KEY = "entityName";

    @VisibleForTesting
    final WriteSafeRegistry<JsonObject> clusteringInformation;

    final WriteSafeRegistry<Object> entityRegistry;

    private final Vertx vertx;

    /**
     * Create a new instance of {@link ClusterEntityRegistry}.
     *
     * @param vertx        the {@link Vertx} instance
     * @param registryName the name of the map registry
     */
    public ClusterEntityRegistry(Vertx vertx, String registryName) {
        this.entityRegistry = new WriteSafeRegistry<>(vertx, registryName);
        this.clusteringInformation = new WriteSafeRegistry<>(vertx, registryName + "#ClusteringInformation");
        this.vertx = vertx;
    }

    @VisibleForTesting
    static JsonObject clusterRegistrationInformation(String sharedMapKey, String value) {
        return JsonObject.of(QUALIFIED_NAME_KEY, value, ENTITY_NAME_KEY, sharedMapKey);
    }

    @Override
    public Future<Void> register(String sharedMapKey, String value) {
        return Future
                .all(entityRegistry.register(sharedMapKey, value),
                        clusteringInformation.register(getClusterNodeId(),
                                clusterRegistrationInformation(sharedMapKey, value)))
                .mapEmpty();
    }

    @Override
    public Future<Void> unregister(String sharedMapKey, String value) {
        return Future.all(
                entityRegistry.unregister(sharedMapKey, value),
                clusteringInformation.unregister(
                        getClusterNodeId(),
                        clusterRegistrationInformation(sharedMapKey, value)))
                .mapEmpty();
    }

    /**
     * Get the cluster node ID.
     *
     * @return the ID of the cluster node
     */
    @VisibleForTesting
    String getClusterNodeId() {
        return ClusterHelper.getClusterNodeId(vertx);
    }

    @Override
    public Future<JsonArray> get(String sharedMapKey) {
        return entityRegistry.get(sharedMapKey);
    }

    /**
     * Get the clustering information for the provided cluster ID from the registry.
     *
     * @param clusterNodeId the ID of the cluster node
     * @return the future
     */
    public Future<JsonArray> getClusteringInformation(String clusterNodeId) {
        return clusteringInformation.get(clusterNodeId);
    }

    /**
     * Remove the entry for a node ID.
     *
     * @param clusterNodeId the ID of the cluster node
     * @return the future
     */
    Future<Void> removeClusteringInformation(String clusterNodeId) {
        return clusteringInformation.getSharedMap().compose(map -> map.remove(clusterNodeId)).mapEmpty();
    }

    /**
     * Unregister all registered entities for a node by ID.
     *
     * @param clusterNodeId the ID of the cluster node
     * @return the future
     */
    public Future<Void> unregisterNode(String clusterNodeId) {
        return clusteringInformation.getSharedMap()
                .compose(AsyncMap::entries)
                .compose(map -> {
                    JsonArray registeredEntities = (JsonArray) map.remove(clusterNodeId);

                    if (registeredEntities == null) {
                        // If no entities are registered, return a completed future
                        return Future.succeededFuture();
                    }
                    registeredEntities = registeredEntities.copy();
                    List<Future<?>> futureList = new ArrayList<>(registeredEntities.size());
                    for (Object o : registeredEntities) {
                        if (shouldRemove(map, o)) {
                            JsonObject jo = (JsonObject) o;
                            String entityName = jo.getString(ENTITY_NAME_KEY);
                            String qualifiedName = jo.getString(QUALIFIED_NAME_KEY);
                            futureList.add(entityRegistry.unregister(entityName, qualifiedName));
                        }
                    }
                    return Future.join(futureList).mapEmpty();
                }).compose(cf -> removeClusteringInformation(clusterNodeId));
    }

    /**
     * Check if the provided object should be removed from the map.
     *
     * @param map the map
     * @param o   the object
     * @return true if the object should be removed
     */
    private boolean shouldRemove(Map<String, Object> map, Object o) {
        for (Map.Entry<String, Object> node : map.entrySet()) {
            JsonArray ja = (JsonArray) node.getValue();
            // Iterate over the JsonArray to determine if it contains the specified object.
            // JsonArray#contains cannot be used directly because the types of objects in the JsonArray may differ from
            // those returned by the iterator. This discrepancy occurs because JsonArray.Iter#next automatically wraps
            // standard Java types into their corresponding Json types.
            for (Object object : ja) {
                if (object.equals(o)) {
                    return false;
                }
            }
        }
        return true;
    }
}
