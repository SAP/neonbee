package io.neonbee.internal.cluster.entity;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.NeonBeeInstanceConfiguration.ClusterManager.HAZELCAST;
import static io.neonbee.NeonBeeInstanceConfiguration.ClusterManager.INFINISPAN;
import static io.neonbee.NeonBeeProfile.WEB;
import static io.vertx.core.Future.succeededFuture;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

import org.apache.olingo.commons.api.edm.FullQualifiedName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

@ExtendWith({ NeonBeeExtension.class })
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
        EntityVerticleUnregisterImpl entityVerticle = new EntityVerticleUnregisterImpl();
        vertx.deployVerticle(entityVerticle)
                .compose(unused -> registry.clusteringInformation.get(ClusterHelper.getClusterNodeId(web.getVertx())))
                .onSuccess(jsonArray -> testContext.verify(() -> {
                    assertThat(jsonArray).hasSize(2);

                    List<JsonObject> jsonObjectList = jsonArray.stream().map(JsonObject.class::cast).sorted(
                            (o1, o2) -> CharSequence.compare(o1.getString("entityName"), o2.getString("entityName")))
                            .toList();

                    assertThat(jsonObjectList.get(0))
                            .isEqualTo(JsonObject.of("qualifiedName", entityVerticle.getQualifiedName(), "entityName",
                                    sharedEntityMapName(EntityVerticleUnregisterImpl.FQN_ERP_CUSTOMERS)));

                    assertThat(jsonObjectList.get(1))
                            .isEqualTo(JsonObject.of("qualifiedName", entityVerticle.getQualifiedName(), "entityName",
                                    sharedEntityMapName(EntityVerticleUnregisterImpl.FQN_SALES_ORDERS)));
                    checkpoint.flag();
                }))
                .compose(unused -> EntityVerticle.getVerticlesForEntityType(vertx,
                        EntityVerticleUnregisterImpl.FQN_ERP_CUSTOMERS))
                .compose(list1 -> EntityVerticle
                        .getVerticlesForEntityType(vertx, EntityVerticleUnregisterImpl.FQN_SALES_ORDERS).map(list2 -> {
                            return ImmutableList.<String>builder().addAll(list1).addAll(list2).build();
                        }))
                .onSuccess(list -> testContext.verify(() -> {
                    assertThat(list).hasSize(2);
                    checkpoint.flag();
                })).compose(unused -> UnregisterEntityVerticlesHook.unregister(web, clusterNodeId))
                .compose(unused -> registry.get(sharedEntityMapName(EntityVerticleUnregisterImpl.FQN_ERP_CUSTOMERS)))
                .onSuccess(jsonArray -> testContext.verify(() -> {
                    assertThat(jsonArray).isEqualTo(new JsonArray());
                    checkpoint.flag();
                })).compose(unused -> registry.clusteringInformation.get(clusterNodeId))
                .onSuccess(object -> testContext.verify(() -> {
                    assertThat(object).isNull();
                    checkpoint.flag();
                }))
                .compose(unused -> EntityVerticle.getVerticlesForEntityType(vertx,
                        EntityVerticleUnregisterImpl.FQN_ERP_CUSTOMERS))
                .compose(list1 -> EntityVerticle
                        .getVerticlesForEntityType(vertx, EntityVerticleUnregisterImpl.FQN_SALES_ORDERS).map(list2 -> {
                            return ImmutableList.<String>builder().addAll(list1).addAll(list2).build();
                        }))
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
    @DisplayName("test unregistering entity models in a single node Hazelcast cluster")
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
                        new IllegalStateException("Cluster did not form in time. The waited for "
                                + (System.currentTimeMillis() - startTime) + " ms."));
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

        EntityVerticleUnregisterImpl n1EntityVerticle = new EntityVerticleUnregisterImpl();
        EntityVerticleUnregisterImpl n2EntityVerticle = new EntityVerticleUnregisterImpl();

        String clusterNode1Id = ClusterHelper.getClusterNodeId(node1.getVertx());
        String clusterNode2Id = ClusterHelper.getClusterNodeId(node2.getVertx());

        // 1. deploy the same EntitiyVerticle on two NeonBee nodes.
        Future.all(
                node1.getVertx().deployVerticle(n1EntityVerticle),
                node2.getVertx().deployVerticle(n2EntityVerticle))

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
                                // 1d0a0395-eb9b-43ab-b222-f2112eb3bbce -> [{
                                // "qualifiedName":"unregisterentitiestest/_EntityVerticleUnregisterImpl-1739039304",
                                // "entityName":"entityVerticles[Sales.Orders]"
                                // },{
                                // "qualifiedName":"unregisterentitiestest/_EntityVerticleUnregisterImpl-1739039304",
                                // "entityName":"entityVerticles[ERP.Customers]"
                                // }]
                                //
                                // 7c883162-bc91-4c83-9043-0e2c3717da57 -> [{
                                // "qualifiedName":"unregisterentitiestest/_EntityVerticleUnregisterImpl-1739039304",
                                // "entityName":"entityVerticles[Sales.Orders]"
                                // },{
                                // "qualifiedName":"unregisterentitiestest/_EntityVerticleUnregisterImpl-1739039304",
                                // "entityName":"entityVerticles[ERP.Customers]"
                                // }]
                                verifyClusterInformation(clusterInfoEntries.result(),
                                        Set.of(clusterNode1Id, clusterNode2Id));

                                // The entity registry map should look like this:
                                //
                                // entityVerticles[Sales.Orders] ->
                                // ["unregisterentitiestest/_EntityVerticleUnregisterImpl-1739039304"]
                                //
                                // entityVerticles[ERP.Customers] ->
                                // ["unregisterentitiestest/_EntityVerticleUnregisterImpl-1739039304"]
                                verifyEntityRegistry(entityRegistryEntries.result());
                                checkpoint.flag();
                            }));
                })

                // 3. unregister node2 executed by node1
                .compose(unused -> n1Registry.unregisterNode(ClusterHelper.getClusterNodeId(node2.getVertx())))

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
                                // 1d0a0395-eb9b-43ab-b222-f2112eb3bbce -> [{
                                // "qualifiedName":"unregisterentitiestest/_EntityVerticleUnregisterImpl-1739039304",
                                // "entityName":"entityVerticles[Sales.Orders]"
                                // },{
                                // "qualifiedName":"unregisterentitiestest/_EntityVerticleUnregisterImpl-1739039304",
                                // "entityName":"entityVerticles[ERP.Customers]"
                                // }]
                                verifyClusterInformation(clusterInfoEntries.result(), Set.of(clusterNode1Id));

                                // The entity registry map should look like this:
                                //
                                // entityVerticles[Sales.Orders] ->
                                // ["unregisterentitiestest/_EntityVerticleUnregisterImpl-1739039304"]
                                //
                                // entityVerticles[ERP.Customers] ->
                                // ["unregisterentitiestest/_EntityVerticleUnregisterImpl-1739039304"]
                                verifyEntityRegistry(entityRegistryEntries.result());
                                checkpoint.flag();
                            }));
                })

                // 5. unregister node1
                .compose(unused -> n1Registry.unregisterNode(ClusterHelper.getClusterNodeId(node1.getVertx())))

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

    private void verifyEntityRegistry(Map<String, Object> entityRegistryEntries) {
        Set<FullQualifiedName> entityTypeNames = Set.of(
                EntityVerticleUnregisterImpl.FQN_ERP_CUSTOMERS,
                EntityVerticleUnregisterImpl.FQN_SALES_ORDERS);

        assertThat(entityRegistryEntries).hasSize(entityTypeNames.size());

        Set<String> stringEntityTypeNames = entityTypeNames.stream()
                .map(UnregisterEntitiesTest::sharedEntityMapName)
                .collect(Collectors.toSet());

        assertThat(entityRegistryEntries.keySet())
                .containsExactly(stringEntityTypeNames.toArray());

        String qualifiedName = DataVerticle.createQualifiedName(
                TEST_NAMESPACE,
                EntityVerticle.getName(EntityVerticleUnregisterImpl.class));

        entityRegistryEntries.values()
                .stream()
                .map(JsonArray.class::cast)
                .flatMap(JsonArray::stream)
                .forEach(value -> assertThat(value).isEqualTo(qualifiedName));
    }

    private void verifyClusterInformation(Map<String, Object> actual, Set<String> nodeIds) {
        assertThat(actual).hasSize(nodeIds.size());
        assertThat(actual.keySet()).containsExactly(nodeIds.toArray());

        String qualifiedName = DataVerticle.createQualifiedName(
                TEST_NAMESPACE,
                EntityVerticle.getName(EntityVerticleUnregisterImpl.class));

        for (String nodeId : nodeIds) {
            JsonArray node1Info = (JsonArray) actual.get(nodeId);
            String qualifiedName0 = node1Info.getJsonObject(0).getString(ClusterEntityRegistry.QUALIFIED_NAME_KEY);
            String qualifiedName1 = node1Info.getJsonObject(1).getString(ClusterEntityRegistry.QUALIFIED_NAME_KEY);

            assertThat(qualifiedName0).isEqualTo(qualifiedName);
            assertThat(qualifiedName1).isEqualTo(qualifiedName);

            String entityName0 = node1Info.getJsonObject(0).getString(ClusterEntityRegistry.ENTITY_NAME_KEY);
            String entityName1 = node1Info.getJsonObject(1).getString(ClusterEntityRegistry.ENTITY_NAME_KEY);

            assertThat(Set.of(entityName0, entityName1)).containsExactly(
                    sharedEntityMapName(EntityVerticleUnregisterImpl.FQN_ERP_CUSTOMERS),
                    sharedEntityMapName(EntityVerticleUnregisterImpl.FQN_SALES_ORDERS));
        }
    }

    static String sharedEntityMapName(FullQualifiedName entityTypeName) {
        return String.format(SHARED_ENTITY_MAP_NAME, entityTypeName.getFullQualifiedNameAsString());
    }

    private boolean isClustered(NeonBee neonBee) {
        return ClusterHelper.getClusterManager(neonBee.getVertx()).isPresent();
    }

    @NeonBeeDeployable(namespace = TEST_NAMESPACE, autoDeploy = false)
    public static class EntityVerticleUnregisterImpl extends EntityVerticle {
        static final FullQualifiedName FQN_ERP_CUSTOMERS = new FullQualifiedName("ERP", "Customers");

        static final FullQualifiedName FQN_SALES_ORDERS = new FullQualifiedName("Sales", "Orders");

        @Override
        public Future<Set<FullQualifiedName>> entityTypeNames() {
            return succeededFuture(Set.of(FQN_ERP_CUSTOMERS, FQN_SALES_ORDERS));
        }
    }
}
