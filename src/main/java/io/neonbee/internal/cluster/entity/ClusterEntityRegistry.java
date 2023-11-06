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

    private final Vertx vertx;

    private final WriteSafeRegistry<Object> entityRegistry;

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
        return Future.all(entityRegistry.unregister(sharedMapKey, value), clusteringInformation
                .unregister(getClusterNodeId(), clusterRegistrationInformation(sharedMapKey, value)))
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
        return clusteringInformation.getSharedMap().compose(AsyncMap::entries).compose(map -> {
            JsonArray registeredEntities = ((JsonArray) map.remove(clusterNodeId)).copy();
            List<Future<?>> futureList = new ArrayList<>(registeredEntities.size());
            for (Object o : registeredEntities) {
                if (remove(map, o)) {
                    JsonObject jo = (JsonObject) o;
                    String entityName = jo.getString(ENTITY_NAME_KEY);
                    String qualifiedName = jo.getString(QUALIFIED_NAME_KEY);
                    futureList.add(unregister(entityName, qualifiedName));
                }
            }
            return Future.join(futureList).mapEmpty();
        }).compose(cf -> removeClusteringInformation(clusterNodeId));
    }

    private boolean remove(Map<String, Object> map, Object o) {
        for (Map.Entry<String, Object> node : map.entrySet()) {
            JsonArray ja = (JsonArray) node.getValue();
            if (ja.contains(o)) {
                return false;
            }
        }
        return true;
    }
}
