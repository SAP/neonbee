package io.neonbee.internal.cluster.entity;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.NeonBeeInstanceConfiguration.ClusterManager.HAZELCAST;
import static io.neonbee.NeonBeeInstanceConfiguration.ClusterManager.INFINISPAN;
import static io.neonbee.NeonBeeProfile.WEB;
import static io.neonbee.test.base.NeonBeeTestBase.LONG_RUNNING_TEST;
import static io.vertx.core.Future.succeededFuture;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import org.apache.olingo.commons.api.edm.FullQualifiedName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Isolated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.neonbee.NeonBee;
import io.neonbee.NeonBeeDeployable;
import io.neonbee.NeonBeeExtension;
import io.neonbee.NeonBeeInstanceConfiguration;
import io.neonbee.entity.EntityVerticle;
import io.neonbee.internal.cluster.ClusterHelper;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Timer;
import io.vertx.core.Vertx;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.ext.cluster.infinispan.InfinispanClusterManager;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxTestContext;

@Tag(LONG_RUNNING_TEST)
@ExtendWith({ NeonBeeExtension.class })
@Isolated
@SuppressWarnings("deprecation")
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
        // Entity verticles no longer write to ClusterEntityRegistry — they register Vert.x event bus
        // consumers instead. Verify the registry stays empty after deployment.
        ClusterEntityRegistry registry = (ClusterEntityRegistry) web.getEntityRegistry();
        ErpSalesEntityVerticle entityVerticle = new ErpSalesEntityVerticle();
        vertx.deployVerticle(entityVerticle)
                .compose(unused -> registry.clusteringInformation.get(ClusterHelper.getClusterNodeId(web.getVertx())))
                .onSuccess(jsonArray -> testContext.verify(() -> {
                    assertThat(jsonArray).isNull();
                    testContext.completeNow();
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

        ClusterEntityRegistry n1Registry = (ClusterEntityRegistry) node1.getEntityRegistry();
        ErpSalesEntityVerticle erpSales = new ErpSalesEntityVerticle();
        MarketSalesEntityVerticle makedSales = new MarketSalesEntityVerticle();
        ErpSalesEntityVerticle erpSales2 = new ErpSalesEntityVerticle();

        // Entity verticles no longer write to ClusterEntityRegistry — verify registry stays empty
        // across both nodes after deployment.
        Future.all(
                node1.getVertx().deployVerticle(erpSales),
                node1.getVertx().deployVerticle(makedSales),
                node2.getVertx().deployVerticle(erpSales2))
                .compose(unused -> n1Registry.clusteringInformation.getSharedMap().compose(AsyncMap::entries))
                .onSuccess(entries -> testContext.verify(() -> {
                    assertThat(entries).isEmpty();
                    testContext.completeNow();
                }))
                .onFailure(testContext::failNow);
    }

    static String sharedEntityMapName(FullQualifiedName entityTypeName) {
        return String.format(SHARED_ENTITY_MAP_NAME, entityTypeName.getFullQualifiedNameAsString());
    }

    private boolean isClustered(NeonBee neonBee) {
        return ClusterHelper.getClusterManager(neonBee.getVertx()).isPresent();
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
