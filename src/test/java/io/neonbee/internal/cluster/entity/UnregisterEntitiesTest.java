package io.neonbee.internal.cluster.entity;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.NeonBeeInstanceConfiguration.ClusterManager.HAZELCAST;
import static io.neonbee.NeonBeeInstanceConfiguration.ClusterManager.INFINISPAN;
import static io.neonbee.NeonBeeProfile.WEB;
import static io.vertx.core.Future.succeededFuture;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.olingo.commons.api.edm.FullQualifiedName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.neonbee.NeonBee;
import io.neonbee.NeonBeeExtension;
import io.neonbee.NeonBeeInstanceConfiguration;
import io.neonbee.entity.EntityVerticle;
import io.neonbee.internal.cluster.ClusterHelper;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxTestContext;

@ExtendWith({ NeonBeeExtension.class })
class UnregisterEntitiesTest {

    static final String SHARED_ENTITY_MAP_NAME = "entityVerticles[%s]";

    @Test
    @Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
    @DisplayName("test unregistering entity models (Infinispan cluster)) ")
    void testInfinispanUnregisteringEntities(@NeonBeeInstanceConfiguration(activeProfiles = WEB, clustered = true,
            clusterManager = INFINISPAN) NeonBee web, VertxTestContext testContext) {
        testUnregisteringEntities(web, testContext);
    }

    @Test
    @Timeout(value = 2000, timeUnit = TimeUnit.SECONDS)
    @DisplayName("test unregistering entity models (Hazelcast cluster)")
    void testHazelcastUnregisteringEntities(@NeonBeeInstanceConfiguration(activeProfiles = WEB, clustered = true,
            clusterManager = HAZELCAST) NeonBee web, VertxTestContext testContext) {
        testUnregisteringEntities(web, testContext);
    }

    private void testUnregisteringEntities(NeonBee web, VertxTestContext testContext) {
        assertThat(isClustered(web)).isTrue();

        Vertx vertx = web.getVertx();
        String clusterNodeId = ClusterHelper.getClusterNodeId(vertx);

        ClusterEntityRegistry registry = (ClusterEntityRegistry) web.getEntityRegistry();
        EntityVerticleUnregisterImpl entityVerticle = new EntityVerticleUnregisterImpl();
        vertx.deployVerticle(entityVerticle)
                .compose(unused -> registry.clusteringInformation.get(ClusterHelper.getClusterNodeId(web.getVertx())))
                .onSuccess(jsonArray -> testContext.verify(() -> {
                    assertThat(jsonArray).hasSize(2);

                    List<JsonObject> jsonObjectList = jsonArray.stream().map(JsonObject.class::cast).sorted(
                            (o1, o2) -> CharSequence.compare(o1.getString("entityName"), o2.getString("entityName")))
                            .collect(Collectors.toList());

                    assertThat(jsonObjectList.get(0))
                            .isEqualTo(JsonObject.of("qualifiedName", entityVerticle.getQualifiedName(), "entityName",
                                    sharedEntityMapName(EntityVerticleUnregisterImpl.FQN_ERP_CUSTOMERS)));

                    assertThat(jsonObjectList.get(1))
                            .isEqualTo(JsonObject.of("qualifiedName", entityVerticle.getQualifiedName(), "entityName",
                                    sharedEntityMapName(EntityVerticleUnregisterImpl.FQN_SALES_ORDERS)));
                }))
                .compose(unused -> EntityVerticle.getVerticlesForEntityType(vertx,
                        EntityVerticleUnregisterImpl.FQN_ERP_CUSTOMERS))
                .compose(list -> EntityVerticle
                        .getVerticlesForEntityType(vertx, EntityVerticleUnregisterImpl.FQN_SALES_ORDERS).map(list2 -> {
                            list.addAll(list2);
                            return list;
                        }))
                .onSuccess(list -> testContext.verify(() -> {
                    assertThat(list).hasSize(2);
                })).compose(unused -> UnregisterEntityVerticlesHook.unregister(web, clusterNodeId))
                .compose(unused -> registry.get(sharedEntityMapName(EntityVerticleUnregisterImpl.FQN_ERP_CUSTOMERS)))
                .onSuccess(jsonArray -> testContext.verify(() -> {
                    assertThat(jsonArray).isEqualTo(new JsonArray());
                })).compose(unused -> registry.clusteringInformation.get(clusterNodeId))
                .onSuccess(object -> testContext.verify(() -> {
                    assertThat(object).isNull();
                    testContext.completeNow();
                }))
                .compose(unused -> EntityVerticle.getVerticlesForEntityType(vertx,
                        EntityVerticleUnregisterImpl.FQN_ERP_CUSTOMERS))
                .compose(list -> EntityVerticle
                        .getVerticlesForEntityType(vertx, EntityVerticleUnregisterImpl.FQN_SALES_ORDERS).map(list2 -> {
                            list.addAll(list2);
                            return list;
                        }))
                .onSuccess(list -> testContext.verify(() -> {
                    assertThat(list).isEmpty();
                }))

                .onFailure(testContext::failNow);
    }

    static String sharedEntityMapName(FullQualifiedName entityTypeName) {
        return String.format(SHARED_ENTITY_MAP_NAME, entityTypeName.getFullQualifiedNameAsString());
    }

    private boolean isClustered(NeonBee neonBee) {
        return ClusterHelper.getClusterManager(neonBee.getVertx()).isPresent();
    }

    public static class EntityVerticleUnregisterImpl extends EntityVerticle {
        static final FullQualifiedName FQN_ERP_CUSTOMERS = new FullQualifiedName("ERP", "Customers");

        static final FullQualifiedName FQN_SALES_ORDERS = new FullQualifiedName("Sales", "Orders");

        @Override
        public Future<Set<FullQualifiedName>> entityTypeNames() {
            return succeededFuture(Set.of(FQN_ERP_CUSTOMERS, FQN_SALES_ORDERS));
        }
    }
}
