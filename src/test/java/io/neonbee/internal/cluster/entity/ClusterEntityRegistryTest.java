package io.neonbee.internal.cluster.entity;

import static com.google.common.truth.Truth.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class ClusterEntityRegistryTest {

    private static final String REGISTRY_NAME = "CLUSTER_REGISTRY_NAME";

    private static final String KEY = "key";

    private static final String VALUE = "value";

    @Test
    @DisplayName("register value in registry")
    void register(Vertx vertx, VertxTestContext context) {
        ClusterEntityRegistry registry = new TestClusterEntityRegistry(vertx, REGISTRY_NAME);
        registry.register(KEY, VALUE).compose(unused -> registry.get(KEY)).onSuccess(mapValue -> context.verify(() -> {
            assertThat(mapValue).isEqualTo(new JsonArray().add(VALUE));
            context.completeNow();
        })).onFailure(context::failNow);
    }

    @Test
    @DisplayName("unregister value from registry")
    void unregister(Vertx vertx, VertxTestContext context) {
        ClusterEntityRegistry registry = new TestClusterEntityRegistry(vertx, REGISTRY_NAME);
        registry.register(KEY, VALUE).compose(unused -> registry.unregister(KEY, "value2"))
                .compose(unused -> registry.get(KEY)).onSuccess(mapValue -> context.verify(() -> {
                    assertThat(mapValue).isEqualTo(new JsonArray().add(VALUE));
                })).compose(unused -> registry.unregister(KEY, VALUE)).compose(unused -> registry.get(KEY))
                .onSuccess(mapValue -> context.verify(() -> {
                    assertThat(mapValue).isEqualTo(new JsonArray());
                    context.completeNow();
                })).onFailure(context::failNow);
    }

    @Test
    @DisplayName("get value from registry")
    void get(Vertx vertx, VertxTestContext context) {
        ClusterEntityRegistry registry = new TestClusterEntityRegistry(vertx, REGISTRY_NAME);
        registry.register(KEY, VALUE).compose(unused -> registry.get(KEY)).onSuccess(jsonArray -> context.verify(() -> {
            assertThat(jsonArray).isNotNull();
            assertThat(jsonArray.contains(VALUE)).isTrue();
            context.completeNow();
        })).onFailure(context::failNow);
    }

    @Test
    @DisplayName("get the clustering information from the registry")
    void getClusteringInformation(Vertx vertx, VertxTestContext context) {
        ClusterEntityRegistry registry = new TestClusterEntityRegistry(vertx, REGISTRY_NAME);
        registry.register(KEY, VALUE)
                .compose(unused -> registry.getClusteringInformation(TestClusterEntityRegistry.CLUSTER_NODE_ID))
                .onSuccess(mapValue -> context.verify(() -> {
                    assertThat(mapValue)
                            .isEqualTo(JsonArray.of(ClusterEntityRegistry.clusterRegistrationInformation(KEY, VALUE)));
                    context.completeNow();
                })).onFailure(context::failNow);
    }

    @Test
    @DisplayName("unregister all key from registry")
    void removeClusteringInformation(Vertx vertx, VertxTestContext context) {
        Checkpoint checkpoint = context.checkpoint(2);
        ClusterEntityRegistry registry = new TestClusterEntityRegistry(vertx, REGISTRY_NAME);
        registry.register(KEY, VALUE)
                .compose(unused -> registry.getClusteringInformation(TestClusterEntityRegistry.CLUSTER_NODE_ID))
                .onSuccess(mapValue -> context.verify(() -> {
                    assertThat(mapValue).isNotEmpty();
                    checkpoint.flag();
                })).compose(unused -> registry.removeClusteringInformation(TestClusterEntityRegistry.CLUSTER_NODE_ID))
                .onSuccess(v -> checkpoint.flag())
                .onFailure(context::failNow);
    }

    @Test
    @DisplayName("unregister node")
    void unregisterNode1(Vertx vertx, VertxTestContext context) {
        ClusterEntityRegistry registry = new TestClusterEntityRegistry(vertx, REGISTRY_NAME);
        registry.register(KEY, VALUE)
                .compose(unused -> registry.getClusteringInformation(TestClusterEntityRegistry.CLUSTER_NODE_ID))
                .compose(unused -> registry.unregisterNode(TestClusterEntityRegistry.CLUSTER_NODE_ID))
                .compose(unused -> registry.getClusteringInformation(TestClusterEntityRegistry.CLUSTER_NODE_ID))
                .onSuccess(mapValue -> context.verify(() -> {
                    assertThat(mapValue).isNull();
                    context.completeNow();
                })).onFailure(context::failNow);
    }

    @Test
    @DisplayName("unregister node with two nodes with same entities")
    void unregisterNode2(Vertx vertx, VertxTestContext context) {
        Checkpoint checkpoint = context.checkpoint(5);
        String clusterIdNode1 = "TEST_CLUSTER_ID_0000000000000001";
        String clusterIdNode2 = "TEST_CLUSTER_ID_0000000000000002";
        ClusterEntityRegistry registry1 = new TestClusterEntityRegistry(vertx, REGISTRY_NAME, clusterIdNode1);
        ClusterEntityRegistry registry2 = new TestClusterEntityRegistry(vertx, REGISTRY_NAME, clusterIdNode2);

        registry1.register(KEY, VALUE).compose(unused -> registry2.register(KEY, VALUE))
                .compose(unused -> registry1.get(KEY)).onSuccess(ja -> context.verify(() -> {
                    assertThat(ja).containsExactly(VALUE);
                    checkpoint.flag();
                })).compose(unused -> registry1.getClusteringInformation(clusterIdNode1))
                .compose(unused -> registry1.unregisterNode(clusterIdNode1)).compose(unused -> registry2.get(KEY))
                .onSuccess(ja -> context.verify(() -> {
                    assertThat(ja).containsExactly(VALUE);
                    checkpoint.flag();
                })).compose(unused -> registry2.getClusteringInformation(clusterIdNode1))
                .onSuccess(mapValue -> context.verify(() -> {
                    assertThat(mapValue).isNull();
                    checkpoint.flag();
                })).compose(unused -> registry2.getClusteringInformation(clusterIdNode2))
                .onSuccess(mapValue -> context.verify(() -> {
                    assertThat(mapValue)
                            .containsExactly(ClusterEntityRegistry.clusterRegistrationInformation("key", VALUE));
                    checkpoint.flag();
                })).compose(unused -> registry2.unregisterNode(clusterIdNode2)).compose(unused -> registry2.get(KEY))
                .onSuccess(ja -> context.verify(() -> {
                    assertThat(ja).isEmpty();
                    checkpoint.flag();
                })).onFailure(context::failNow);
    }

    @Test
    @DisplayName("remove key from registry")
    void remove(Vertx vertx, VertxTestContext context) {
        ClusterEntityRegistry registry = new TestClusterEntityRegistry(vertx, REGISTRY_NAME);
        registry.register(KEY, VALUE)
                .compose(unused -> registry.removeClusteringInformation(KEY))
                .onComplete(context.succeedingThenComplete());
    }

    static class TestClusterEntityRegistry extends ClusterEntityRegistry {
        static final String CLUSTER_NODE_ID = "TEST_CLUSTER_ID_0000000000000000";

        final String clusterNodeId;

        TestClusterEntityRegistry(Vertx vertx, String registryName) {
            this(vertx, registryName, CLUSTER_NODE_ID);
        }

        TestClusterEntityRegistry(Vertx vertx, String registryName, String clusterNodeId) {
            super(vertx, registryName);
            this.clusterNodeId = clusterNodeId;
        }

        @Override
        String getClusterNodeId() {
            return clusterNodeId;
        }
    }
}
