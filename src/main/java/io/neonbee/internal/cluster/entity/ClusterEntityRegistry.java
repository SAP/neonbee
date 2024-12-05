package io.neonbee.internal.cluster.entity;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.annotations.VisibleForTesting;

import io.neonbee.entity.EntityVerticle;
import io.neonbee.internal.Registry;
import io.neonbee.internal.WriteLockRegistry;
import io.neonbee.internal.WriteSafeRegistry;
import io.neonbee.internal.cluster.ClusterHelper;
import io.vertx.codegen.annotations.Nullable;
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

    final WriteLockRegistry<Object> entityRegistry;

    private final Vertx vertx;

    /**
     * Create a new instance of {@link ClusterEntityRegistry}.
     *
     * @param vertx        the {@link Vertx} instance
     * @param registryName the name of the map registry
     */
    public ClusterEntityRegistry(Vertx vertx, String registryName) {
        this.entityRegistry = new WriteLockRegistry<>(vertx, registryName);
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

                    // The clustering information map should look like this:
                    //
                    // 6dbe2f47-8e2a-4b41-b019-007b16157f87 ->
                    // [{
                    // "qualifiedName":"unregisterentitiestest/_ErpSalesEntityVerticle-644622197",
                    // "entityName":"entityVerticles[Sales.Orders]"
                    // },{
                    // "qualifiedName":"unregisterentitiestest/_ErpSalesEntityVerticle-644622197",
                    // "entityName":"entityVerticles[ERP.Customers]"
                    // },{
                    // "qualifiedName":"unregisterentitiestest/_MarketSalesEntityVerticle-133064210",
                    // "entityName":"entityVerticles[Sales.Orders]"
                    // },{
                    // "qualifiedName":"unregisterentitiestest/_MarketSalesEntityVerticle-133064210",
                    // "entityName":"entityVerticles[Market.Products]"
                    // }]
                    //
                    // d4f78582-14d4-493f-8a74-03d0e49c566b ->
                    // [{
                    // "qualifiedName":"unregisterentitiestest/_ErpSalesEntityVerticle-644622197",
                    // "entityName":"entityVerticles[Sales.Orders]"
                    // },{
                    // "qualifiedName":"unregisterentitiestest/_ErpSalesEntityVerticle-644622197",
                    // "entityName":"entityVerticles[ERP.Customers]"
                    // }]

                    // The entity registry map should look like this:
                    //
                    // entityVerticles[Sales.Orders] ->
                    // [
                    // "unregisterentitiestest/_ErpSalesEntityVerticle-644622197",
                    // "unregisterentitiestest/_MarketSalesEntityVerticle-133064210"
                    // ]
                    //
                    // entityVerticles[Market.Products] ->
                    // [
                    // "unregisterentitiestest/_MarketSalesEntityVerticle-133064210"
                    // ]
                    // entityVerticles[ERP.Customers] ->
                    // [
                    // "unregisterentitiestest/_ErpSalesEntityVerticle-644622197"
                    // ]
                    return buildEntryMap(map)
                            .compose(entryMap -> entityRegistry.lock()
                                    .compose(lock -> lock.execute(() -> createEntityMap(entryMap))))
                            .compose(cf -> removeClusteringInformation(clusterNodeId));
                });
    }

    private Future<Void> createEntityMap(Map<String, List<String>> entryMap) {
        return entityRegistry.getSharedMap()
                .compose(asyncMap -> asyncMap.clear()
                        .map(v -> entryMap.entrySet()
                                .stream()
                                .map(entry -> asyncMap
                                        .put(entry.getKey(), new JsonArray(entry.getValue())))
                                .collect(Collectors.toList()))
                        .map(Future::all)
                        .mapEmpty());
    }

    private Future<@Nullable Map<String, List<String>>> buildEntryMap(Map<String, Object> map) {
        return vertx.executeBlocking(() -> map.values()
                .stream()
                .map(JsonArray.class::cast)
                .flatMap(JsonArray::stream)
                .map(JsonObject.class::cast)
                .collect(Collectors.toMap(
                        jo -> jo.getString(ENTITY_NAME_KEY),
                        jo -> List.of(jo.getString(QUALIFIED_NAME_KEY)),
                        (l1, l2) -> Stream.of(l1, l2).flatMap(List::stream).toList())));
    }
}
