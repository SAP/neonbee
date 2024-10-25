package io.neonbee.internal.cluster.entity;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.NeonBeeInstanceConfiguration.ClusterManager.HAZELCAST;
import static io.neonbee.NeonBeeInstanceConfiguration.ClusterManager.INFINISPAN;
import static io.neonbee.NeonBeeProfile.WEB;
import static io.neonbee.test.base.NeonBeeTestBase.LONG_RUNNING_TEST;
import static io.vertx.core.Future.succeededFuture;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

import org.apache.olingo.commons.api.edm.FullQualifiedName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Isolated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

import io.neonbee.NeonBee;
import io.neonbee.NeonBeeDeployable;
import io.neonbee.NeonBeeExtension;
import io.neonbee.NeonBeeInstanceConfiguration;
import io.neonbee.data.DataVerticle;
import io.neonbee.entity.EntityVerticle;
import io.neonbee.internal.cluster.ClusterHelper;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Timer;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.ext.cluster.infinispan.InfinispanClusterManager;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxTestContext;

@Tag(LONG_RUNNING_TEST)
@ExtendWith({ NeonBeeExtension.class })
@Isolated
class UnregisterEntitiesTest {

    static final String SHARED_ENTITY_MAP_NAME = "entityVerticles[%s]";

    private static final Logger LOGGER = LoggerFactory.getLogger(UnregisterEntitiesTest.class);

    public static final String TEST_NAMESPACE = "unregisterentitiestest";

    @Test
    @DisplayName("test unregistering entity models in a single node Infinispan cluster")
    void testInfinispanUnregisteringEntitiesSingleNode(
            @NeonBeeInstanceConfiguration(activeProfiles = WEB, clustered = true,
                    clusterManager = INFINISPAN) NeonBee web,
            VertxTestContext testContext) {
        testUnregisteringEntitiesSingleNode(web, testContext);
    }

    @Test
    @DisplayName("test unregistering entity models in a single node Hazelcast cluster")
    void testHazelcastUnregisteringEntitiesSingleNode(
            @NeonBeeInstanceConfiguration(activeProfiles = WEB, clustered = true,
                    clusterManager = HAZELCAST) NeonBee web,
            VertxTestContext testContext) {
        testUnregisteringEntitiesSingleNode(web, testContext);
    }

    private void testUnregisteringEntitiesSingleNode(NeonBee web, VertxTestContext testContext) {
        assertThat(isClustered(web)).isTrue();

        Vertx vertx = web.getVertx();
        String clusterNodeId = ClusterHelper.getClusterNodeId(vertx);

        Checkpoint checkpoint = testContext.checkpoint(5);

        ClusterEntityRegistry registry = (ClusterEntityRegistry) web.getEntityRegistry();
        ErpSalesEntityVerticle entityVerticle = new ErpSalesEntityVerticle();
        vertx.deployVerticle(entityVerticle)
                .compose(unused -> registry.clusteringInformation.get(ClusterHelper.getClusterNodeId(web.getVertx())))
                .onSuccess(jsonArray -> testContext.verify(() -> {
                    assertThat(jsonArray).hasSize(2);

                    List<JsonObject> jsonObjectList = jsonArray.stream().map(JsonObject.class::cast).sorted(
                            (o1, o2) -> CharSequence.compare(o1.getString("entityName"), o2.getString("entityName")))
                            .toList();

                    assertThat(jsonObjectList.get(0))
                            .isEqualTo(JsonObject.of("qualifiedName", entityVerticle.getQualifiedName(), "entityName",
                                    sharedEntityMapName(ErpSalesEntityVerticle.FQN_ERP_CUSTOMERS)));

                    assertThat(jsonObjectList.get(1))
                            .isEqualTo(JsonObject.of("qualifiedName", entityVerticle.getQualifiedName(), "entityName",
                                    sharedEntityMapName(ErpSalesEntityVerticle.FQN_SALES_ORDERS)));
                    checkpoint.flag();
                }))
                .compose(unused -> EntityVerticle.getVerticlesForEntityType(vertx,
                        ErpSalesEntityVerticle.FQN_ERP_CUSTOMERS))
                .compose(list1 -> EntityVerticle
                        .getVerticlesForEntityType(vertx, ErpSalesEntityVerticle.FQN_SALES_ORDERS)
                        .map(list2 -> ImmutableList.<String>builder().addAll(list1).addAll(list2).build()))
                .onSuccess(list -> testContext.verify(() -> {
                    assertThat(list).hasSize(2);
                    checkpoint.flag();
                })).compose(unused -> UnregisterEntityVerticlesHook.unregister(web, clusterNodeId))
                .compose(unused -> registry.get(sharedEntityMapName(ErpSalesEntityVerticle.FQN_ERP_CUSTOMERS)))
                .onSuccess(jsonArray -> testContext.verify(() -> {
                    assertThat(jsonArray).isEqualTo(new JsonArray());
                    checkpoint.flag();
                })).compose(unused -> registry.clusteringInformation.get(clusterNodeId))
                .onSuccess(object -> testContext.verify(() -> {
                    assertThat(object).isNull();
                    checkpoint.flag();
                }))
                .compose(unused -> EntityVerticle.getVerticlesForEntityType(vertx,
                        ErpSalesEntityVerticle.FQN_ERP_CUSTOMERS))
                .compose(list1 -> EntityVerticle
                        .getVerticlesForEntityType(vertx, ErpSalesEntityVerticle.FQN_SALES_ORDERS)
                        .map(list2 -> ImmutableList.<String>builder().addAll(list1).addAll(list2).build()))
                .onSuccess(list -> testContext.verify(() -> {
                    assertThat(list).isEmpty();
                    checkpoint.flag();
                }))
                .onFailure(testContext::failNow);
    }

    @Test
    @DisplayName("test unregistering entity models in a multi node Infinispan cluster")
    void testInfinispanUnregisteringEntitiesMultiNode(
            @NeonBeeInstanceConfiguration(activeProfiles = WEB, clustered = true,
                    clusterManager = INFINISPAN) NeonBee neonBee1,
            @NeonBeeInstanceConfiguration(activeProfiles = WEB, clustered = true,
                    clusterManager = INFINISPAN) NeonBee neonBee2,
            VertxTestContext testContext) {
        waitForClusterToForm(neonBee1.getVertx(), () -> infinispanMembersJoined(neonBee1.getVertx(), 2))
                .onSuccess(unused -> testUnregisteringEntitiesMultiNode(neonBee1, neonBee2, testContext))
                .onFailure(testContext::failNow);
    }

    /**
     * Check if the number of Hazelcast members has joined the cluster.
     *
     * @param vertx          the vertx instance
     * @param membershipSize the expected number of members
     * @return true if the number of members has joined the cluster
     */
    boolean infinispanMembersJoined(Vertx vertx, int membershipSize) {
        return ClusterHelper.getInfinispanClusterManager(vertx)
                .map(InfinispanClusterManager::getNodes)
                .map(List::size)
                .map(size -> size == membershipSize)
                .orElse(Boolean.FALSE);
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.MINUTES)
    @DisplayName("test unregistering entity models in a multi node Hazelcast cluster")
    void testHazelcastUnregisteringEntitiesMultiNode(
            @NeonBeeInstanceConfiguration(activeProfiles = WEB, clustered = true,
                    clusterManager = HAZELCAST, clusterConfigFile = "hazelcast-localtcp.xml") NeonBee neonBee1,
            @NeonBeeInstanceConfiguration(activeProfiles = WEB, clustered = true,
                    clusterManager = HAZELCAST, clusterConfigFile = "hazelcast-localtcp.xml") NeonBee neonBee2,
            VertxTestContext testContext) {
        waitForClusterToForm(neonBee1.getVertx(), () -> hazelcastMembersJoined(neonBee1.getVertx(), 2))
                .onSuccess(unused -> testUnregisteringEntitiesMultiNode(neonBee1, neonBee2, testContext))
                .onFailure(testContext::failNow);
    }

    /**
     * Wait for the Hazelcast cluster to form.
     *
     * @param vertx the vertx instance
     * @return a future that completes when the cluster has formed
     */
    private Future<Void> waitForClusterToForm(Vertx vertx, BooleanSupplier cluserFormed) {
        long startTime = System.currentTimeMillis();
        Promise<Void> promise = Promise.promise();
        Timer timer = vertx.timer(1, TimeUnit.MINUTES);
        vertx.setPeriodic(100, id -> { // Check every 100 ms
            if (cluserFormed.getAsBoolean()) {
                vertx.cancelTimer(id);
                LOGGER.info("The cluster has formed after {} ms.", System.currentTimeMillis() - startTime);
                promise.complete();
            } else if (timer.isComplete()) { // fail after ~60 seconds
                vertx.cancelTimer(id);
                promise.fail(
                        new IllegalStateException("Cluster did not form in time. (Waited for "
                                + (System.currentTimeMillis() - startTime) + " ms)."));
            } else {
                LOGGER.info("The cluster has not yet formed after {} ms.", System.currentTimeMillis() - startTime);
            }
        });
        return promise.future();
    }

    /**
     * Check if the number of Hazelcast members has joined the cluster.
     *
     * @param vertx          the vertx instance
     * @param membershipSize the expected number of members
     * @return true if the number of members has joined the cluster
     */
    boolean hazelcastMembersJoined(Vertx vertx, int membershipSize) {
        return ClusterHelper.getHazelcastClusterManager(vertx)
                .map(clusterManager -> clusterManager.getHazelcastInstance().getCluster().getMembers()
                        .size() == membershipSize)
                .orElse(Boolean.FALSE);
    }

    private void testUnregisteringEntitiesMultiNode(NeonBee node1, NeonBee node2, VertxTestContext testContext) {
        assertThat(isClustered(node1)).isTrue();

        Checkpoint checkpoint = testContext.checkpoint(3);
        ClusterEntityRegistry n1Registry = (ClusterEntityRegistry) node1.getEntityRegistry();

        ErpSalesEntityVerticle erpSales = new ErpSalesEntityVerticle();
        ErpSalesEntityVerticle erpSales2 = new ErpSalesEntityVerticle();
        MarketSalesEntityVerticle makedSales = new MarketSalesEntityVerticle();

        String clusterNode1Id = ClusterHelper.getClusterNodeId(node1.getVertx());
        String clusterNode2Id = ClusterHelper.getClusterNodeId(node2.getVertx());
        LOGGER.info("NeonBee ID: {}; Node 1 ID: {}", node1.getNodeId(), clusterNode1Id);
        LOGGER.info("NeonBee ID: {}; Node 2 ID: {}", node2.getNodeId(), clusterNode2Id);

        // 1. deploy the same EntitiyVerticle on two NeonBee nodes.
        Future.all(
                node1.getVertx().deployVerticle(erpSales),
                node1.getVertx().deployVerticle(makedSales),
                node2.getVertx().deployVerticle(erpSales2))
                .compose(event -> printMaps(n1Registry, "After EntityVerticles deployed"))

                // 2. verify the content of ClusterEntityRegistry#clusteringInformation and
                // ClusterEntityRegistry#entityRegistry
                .compose(unused -> {
                    Future<Map<String, Object>> clusterInfoEntries =
                            n1Registry.clusteringInformation.getSharedMap().compose(AsyncMap::entries);
                    Future<Map<String, Object>> entityRegistryEntries =
                            n1Registry.entityRegistry.getSharedMap().compose(AsyncMap::entries);

                    return Future.all(clusterInfoEntries, entityRegistryEntries)
                            .onSuccess(compositeFuture -> testContext.verify(() -> {
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
                                verifyClusterInformation(
                                        clusterInfoEntries.result(),
                                        Map.of(
                                                clusterNode1Id, List.of(erpSales, makedSales),
                                                clusterNode2Id, List.of(erpSales)));

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
                                verifyEntityRegistry(entityRegistryEntries.result(), Set.of(erpSales, makedSales));
                                checkpoint.flag();
                            }));
                })

                // 3. unregister node2 executed by node1
                .compose(unused -> n1Registry.unregisterNode(ClusterHelper.getClusterNodeId(node2.getVertx())))
                .compose(event -> printMaps(n1Registry, "After unregister node 2"))

                // 4. verify the content of ClusterEntityRegistry#clusteringInformation and
                // ClusterEntityRegistry#entityRegistry
                .compose(unused -> {
                    Future<Map<String, Object>> clusterInfoEntries =
                            n1Registry.clusteringInformation.getSharedMap().compose(AsyncMap::entries);
                    Future<Map<String, Object>> entityRegistryEntries =
                            n1Registry.entityRegistry.getSharedMap().compose(AsyncMap::entries);

                    return Future.all(clusterInfoEntries, entityRegistryEntries)
                            .onSuccess(compositeFuture -> testContext.verify(() -> {
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
                                verifyClusterInformation(
                                        clusterInfoEntries.result(),
                                        Map.of(clusterNode1Id, List.of(erpSales, makedSales)));

                                // The entity registry map should look like this:
                                //
                                // entityVerticles[Sales.Orders] ->
                                // [
                                // "unregisterentitiestest/_ErpSalesEntityVerticle-644622197",
                                // "unregisterentitiestest/_MarketSalesEntityVerticle-133064210"
                                // ]
                                // entityVerticles[Market.Products] ->
                                // [
                                // "unregisterentitiestest/_MarketSalesEntityVerticle-133064210"
                                // ]
                                // entityVerticles[ERP.Customers] ->
                                // [
                                // "unregisterentitiestest/_ErpSalesEntityVerticle-644622197"
                                // ]
                                verifyEntityRegistry(entityRegistryEntries.result(), Set.of(erpSales, makedSales));
                                checkpoint.flag();
                            }));
                })

                // 5. unregister node1
                .compose(unused -> n1Registry.unregisterNode(ClusterHelper.getClusterNodeId(node1.getVertx())))
                .compose(event -> printMaps(n1Registry, "After unregister node 1"))

                // 6. verify the content of ClusterEntityRegistry#clusteringInformation and
                // ClusterEntityRegistry#entityRegistry
                .compose(unused -> {
                    Future<Map<String, Object>> clusterInfoEntries =
                            n1Registry.clusteringInformation.getSharedMap().compose(AsyncMap::entries);
                    Future<Map<String, Object>> entityRegistryEntries =
                            n1Registry.entityRegistry.getSharedMap().compose(AsyncMap::entries);

                    return Future.all(clusterInfoEntries, entityRegistryEntries)
                            .onSuccess(compositeFuture -> testContext.verify(() -> {
                                assertThat(clusterInfoEntries.result()).isEmpty();

                                // The entity registry map should look like this:
                                // entityVerticles[Sales.Orders] -> []
                                // entityVerticles[Market.Products] -> []
                                // entityVerticles[ERP.Customers] -> []
                                long entityVerticlesCount = entityRegistryEntries.result().values()
                                        .stream()
                                        .map(JsonArray.class::cast)
                                        .flatMap(JsonArray::stream)
                                        .count();
                                assertThat(entityVerticlesCount).isEqualTo(0);
                                checkpoint.flag();
                            }));
                })
                .onFailure(testContext::failNow);
    }

    private void verifyEntityRegistry(Map<String, Object> entityRegistryEntries,
            Set<EntityVerticle> deployedEntityVerticles) {

        Map<String, Set<String>> deployedEntityRegistryMap = new HashMap<>();
        for (EntityVerticle entityVerticle : deployedEntityVerticles) {
            for (FullQualifiedName entityTypeName : entityVerticle.entityTypeNames().result()) {
                String sharedEntityMapName = sharedEntityMapName(entityTypeName);

                String qualifiedName = DataVerticle.createQualifiedName(
                        TEST_NAMESPACE,
                        EntityVerticle.getName(entityVerticle.getClass()));

                Set<String> qualifiedNames =
                        deployedEntityRegistryMap.computeIfAbsent(sharedEntityMapName, k -> new HashSet<>());
                qualifiedNames.add(qualifiedName);
            }
        }

        assertThat(entityRegistryEntries).hasSize(deployedEntityRegistryMap.size());

        for (Map.Entry<String, Set<String>> entry : deployedEntityRegistryMap.entrySet()) {
            String entityName = entry.getKey();
            Set<String> qualifiedNames = entry.getValue();
            JsonArray jsonArray = (JsonArray) entityRegistryEntries.get(entityName);
            assertThat(jsonArray).containsExactly(qualifiedNames.toArray());
        }
    }

    private void verifyClusterInformation(Map<String, Object> clusteringInformationMap,
            Map<String, List<EntityVerticle>> deployedEntityVerticles) {
        assertThat(clusteringInformationMap).hasSize(deployedEntityVerticles.size());
        assertThat(clusteringInformationMap.keySet()).containsExactly(deployedEntityVerticles.keySet().toArray());

        for (Map.Entry<String, List<EntityVerticle>> e : deployedEntityVerticles.entrySet()) {
            String nodeId = e.getKey();
            List<EntityVerticle> entityVerticles = e.getValue();
            Object o = clusteringInformationMap.get(nodeId);
            assertThat(o).isInstanceOf(JsonArray.class);
            JsonArray jsonArray = (JsonArray) o;

            int entityNamesCount = entityVerticles.stream()
                    .map(EntityVerticle::entityTypeNames)
                    .map(Future::result)
                    .map(Set::size)
                    .reduce(0, Integer::sum);
            assertThat(jsonArray.size()).isEqualTo(entityNamesCount);

            Map<String, Set<String>> deployedEntityVerticleMap = entityVerticles.stream()
                    .collect(Collectors.toMap(
                            entityVerticle -> DataVerticle.createQualifiedName(
                                    TEST_NAMESPACE,
                                    EntityVerticle.getName(entityVerticle.getClass())),
                            entityVerticle -> entityVerticle.entityTypeNames()
                                    .result()
                                    .stream()
                                    .map(UnregisterEntitiesTest::sharedEntityMapName)
                                    .collect(Collectors.toSet())));

            Map<String, Set<String>> clusterInfromationEntityVerticleMap = jsonArray.stream()
                    .map(JsonObject.class::cast)
                    .collect(Collectors.groupingBy(
                            jsonObject -> jsonObject.getString(ClusterEntityRegistry.QUALIFIED_NAME_KEY),
                            Collectors.mapping(
                                    jsonObject -> jsonObject.getString(ClusterEntityRegistry.ENTITY_NAME_KEY),
                                    Collectors.toSet())));

            assertThat(clusterInfromationEntityVerticleMap.keySet())
                    .containsExactly(deployedEntityVerticleMap.keySet().toArray());
            for (Map.Entry<String, Set<String>> entry : deployedEntityVerticleMap.entrySet()) {
                assertThat(clusterInfromationEntityVerticleMap).containsKey(entry.getKey());
                Set<String> entityNames = clusterInfromationEntityVerticleMap.get(entry.getKey());
                assertThat(entityNames).containsExactly(entry.getValue().toArray());
            }
        }
    }

    static String sharedEntityMapName(FullQualifiedName entityTypeName) {
        return String.format(SHARED_ENTITY_MAP_NAME, entityTypeName.getFullQualifiedNameAsString());
    }

    private boolean isClustered(NeonBee neonBee) {
        return ClusterHelper.getClusterManager(neonBee.getVertx()).isPresent();
    }

    /**
     * Print the content of the Clustering Information and Entity Registry map.
     *
     * @param registry   the cluster entity registry
     * @param identifier an identifier for the printed content
     * @return a future that completes when the content of the shared maps has been printed
     */
    private Future<Map<String, Object>> printMaps(ClusterEntityRegistry registry, String identifier) {
        return registry.clusteringInformation.getSharedMap()
                .compose(AsyncMap::entries)
                .onSuccess(entires -> {
                    LOGGER.info("##### Clustering Information {}:", identifier);
                    entires.forEach((k, v) -> LOGGER.info("##### {}: {} -> {}", identifier, k, v));
                })
                .compose(unused -> registry.entityRegistry.getSharedMap())
                .compose(AsyncMap::entries)
                .onSuccess(entires -> {
                    LOGGER.info("##### Entity Registry {}:", identifier);
                    entires.forEach((k, v) -> LOGGER.info("##### {}: {} -> {} ", identifier, k, v));
                });
    }

    @NeonBeeDeployable(namespace = TEST_NAMESPACE, autoDeploy = false)
    public static class ErpSalesEntityVerticle extends EntityVerticle {
        static final FullQualifiedName FQN_ERP_CUSTOMERS = new FullQualifiedName("ERP", "Customers");

        static final FullQualifiedName FQN_SALES_ORDERS = new FullQualifiedName("Sales", "Orders");

        @Override
        public Future<Set<FullQualifiedName>> entityTypeNames() {
            return succeededFuture(Set.of(FQN_ERP_CUSTOMERS, FQN_SALES_ORDERS));
        }
    }

    @NeonBeeDeployable(namespace = TEST_NAMESPACE, autoDeploy = false)
    public static class MarketSalesEntityVerticle extends EntityVerticle {
        static final FullQualifiedName FQN_TEST_PRODUCTS = new FullQualifiedName("Market", "Products");

        static final FullQualifiedName FQN_SALES_ORDERS = new FullQualifiedName("Sales", "Orders");

        @Override
        public Future<Set<FullQualifiedName>> entityTypeNames() {
            return succeededFuture(Set.of(FQN_TEST_PRODUCTS, FQN_SALES_ORDERS));
        }
    }
}
