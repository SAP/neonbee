package io.neonbee.internal.registry.clustered;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.NeonBeeInstanceConfiguration.ClusterManager.INFINISPAN;
import static io.neonbee.NeonBeeProfile.CORE;
import static io.neonbee.hook.HookType.BEFORE_SHUTDOWN;
import static io.neonbee.hook.HookType.CLUSTER_NODE_ID;
import static io.neonbee.internal.helper.AsyncHelper.allComposite;
import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

import io.neonbee.NeonBee;
import io.neonbee.NeonBeeExtension;
import io.neonbee.NeonBeeInstanceConfiguration;
import io.neonbee.hook.HookType;
import io.neonbee.internal.cluster.ClusterHelper;
import io.neonbee.internal.registry.SelfCleaningRegistry;
import io.neonbee.internal.registry.SelfCleaningRegistryController;
import io.neonbee.test.helper.ReflectionHelper;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.junit5.VertxTestContext;

class SelfCleaningRegistryTest extends NeonBeeExtension.TestBase {
    private static final String CUSTOM_VALUE_KEY_NODE_1 = "customValuesNode1";

    private static final String CUSTOM_VALUE_KEY_NODE_2 = "customValuesNode2";

    private static final String CUSTOM_VALUE_KEY_NODE_3 = "customValuesNode3";

    private static final List<String> CUSTOM_VALUES = List.of("value1", "value2");

    private static final String SHARED_VALUE_KEY = "sharedValues";

    private static final List<String> SHARED_VALUES_NODE_1 = List.of("node1-value1", "node1-value2");

    private static final List<String> SHARED_VALUES_NODE_2 = List.of("node2-value1", "node2-value2");

    private static final List<String> SHARED_VALUES_NODE_3 = List.of("node3-value1", "node3-value2");

    private static final String REGISTRY_NAME = "clusteredRegistry";

    private SelfCleaningRegistry<String> registryNode1;

    private SelfCleaningRegistry<String> registryNode2;

    private SelfCleaningRegistry<String> registryNode3;

    @Test
    @DisplayName("test SelfCleaningRegistry with Infinispan")
    void cycleTestInfinispan(
            @NeonBeeInstanceConfiguration(activeProfiles = CORE, clustered = true,
                    clusterManager = INFINISPAN) NeonBee node1,
            @NeonBeeInstanceConfiguration(activeProfiles = CORE, clustered = true,
                    clusterManager = INFINISPAN) NeonBee node2,
            @NeonBeeInstanceConfiguration(activeProfiles = CORE, clustered = true,
                    clusterManager = INFINISPAN) NeonBee node3,
            VertxTestContext testContext) {
        // 1. register values for every node
        createRegistries(node1, node2, node3).compose(v -> fillRegistries()).compose(v -> {
            // 2. verify registration
            return verifyAllValuesRegistered(testContext);
        }).compose(v -> {
            // 3. Shutdown node1
            return node1.getVertx().close();
        }).compose(v -> {
            // 4. verify that all registrations from node1 are gone
            return verifyRemovalOfNode1Values(testContext);
        }).compose(v -> {
            // 5. Execute node left hook with clusterId from node2 (because we can't simulate a crash)
            String node2Id = new SelfCleaningRegistryController(node2.getVertx()).getNodeId();
            // We need to trigger this hook from the current cluster leader
            // in a real world scenario node 2 is crashed, so it can't be the leader, but in our
            // test node 2 still exists, so we have to check which node is the leader.
            NeonBee leaderNode = ClusterHelper.isLeader(node2.getVertx()) ? node2 : node3;
            return leaderNode.getHookRegistry().executeHooks(HookType.NODE_LEFT, Map.of(CLUSTER_NODE_ID, node2Id));
        }).compose(v -> {
            // 6. verify that all registrations from node2 are gone
            return verifyRemovalOfNode2Values(testContext);
        }).onComplete(testContext.succeedingThenComplete());
    }

    @Test
    @DisplayName("test SelfCleaningRegistry with Fake ClusterManager")
    @SuppressWarnings("PMD.AvoidCatchingThrowable")
    void cycleTestFakeClusterManager(
            @NeonBeeInstanceConfiguration(activeProfiles = CORE, clustered = true) NeonBee node1,
            @NeonBeeInstanceConfiguration(activeProfiles = CORE, clustered = true) NeonBee node2,
            @NeonBeeInstanceConfiguration(activeProfiles = CORE, clustered = true) NeonBee node3,
            VertxTestContext testContext) {
        // 1. register values for every node
        createRegistries(node1, node2, node3).compose(v -> fillRegistries()).compose(v -> {
            // 2. verify registration
            return verifyAllValuesRegistered(testContext);
        }).compose(v -> {
            // 3. Shutdown node1
            // With FakeClusterManager we can only simulate it because otherwise NODE_LEFT hook will be triggered on
            // every node. And NODE_LEFT hook calls ClusterHelper.isLeader() which results in an exception with
            // FakeClusterManager.
            return node1.getHookRegistry().executeHooks(BEFORE_SHUTDOWN);
        }).compose(v -> {
            // 4. verify that all registrations from node1 are gone
            return verifyRemovalOfNode1Values(testContext);
        }).compose(v -> {
            // 5. Execute node left hook with clusterId from node2 (because we can't simulate a crash)
            String node2Id = new SelfCleaningRegistryController(node2.getVertx()).getNodeId();
            try {
                // As described in 3. NODE_LEFT hook calls method ClusterHelper.isLeader(), which results in an error,
                // because ClusterHelper isn't aware of FakeClusterManager. But fortunately it directly returns true,
                // if Vert.x isn't in cluster mode, before the problematic code gets executed.

                // To trick the ClusterHelper, we set VertxImpl's "clusterManager" field to null and restore it right
                // after the hook completes.
                VertxTestContext.ExecutionBlock restoreClusterManager =
                        ReflectionHelper.setValueOfPrivateField(node3.getVertx(), "clusterManager", null);
                return node3.getHookRegistry().executeHooks(HookType.NODE_LEFT, Map.of(CLUSTER_NODE_ID, node2Id))
                        .compose(v1 -> {
                            try {
                                restoreClusterManager.apply();
                                return succeededFuture();
                            } catch (Throwable e) {
                                return failedFuture(e);
                            }
                        });
            } catch (NoSuchFieldException | IllegalAccessException e) {
                return failedFuture(e);
            }
        }).compose(v -> {
            // 6. verify that all registrations from node2 are gone
            return verifyRemovalOfNode2Values(testContext);
        }).onComplete(testContext.succeedingThenComplete());
    }

    private Future<Void> verifyAllValuesRegistered(VertxTestContext testContext) {
        List<String> allSharedValues = ImmutableList.<String>builder().addAll(SHARED_VALUES_NODE_1)
                .addAll(SHARED_VALUES_NODE_2).addAll(SHARED_VALUES_NODE_3).build();

        Map<String, List<String>> expected = Map.of(
                CUSTOM_VALUE_KEY_NODE_1, CUSTOM_VALUES,
                CUSTOM_VALUE_KEY_NODE_2, CUSTOM_VALUES,
                CUSTOM_VALUE_KEY_NODE_3, CUSTOM_VALUES,
                SHARED_VALUE_KEY, allSharedValues);
        return verifyEntries(registryNode1, expected, testContext);
    }

    private Future<Void> verifyRemovalOfNode1Values(VertxTestContext testContext) {
        List<String> allSharedValues =
                ImmutableList.<String>builder().addAll(SHARED_VALUES_NODE_2).addAll(SHARED_VALUES_NODE_3).build();

        Map<String, List<String>> expected = Map.of(
                CUSTOM_VALUE_KEY_NODE_2, CUSTOM_VALUES,
                CUSTOM_VALUE_KEY_NODE_3, CUSTOM_VALUES,
                SHARED_VALUE_KEY, allSharedValues);
        return verifyEntries(registryNode2, expected, testContext);
    }

    private Future<Void> verifyRemovalOfNode2Values(VertxTestContext testContext) {
        Map<String, List<String>> expected = Map.of(
                CUSTOM_VALUE_KEY_NODE_3, CUSTOM_VALUES,
                SHARED_VALUE_KEY, SHARED_VALUES_NODE_3);
        return verifyEntries(registryNode3, expected, testContext);
    }

    private Future<Void> createRegistries(NeonBee node1, NeonBee node2, NeonBee node3) {
        return CompositeFuture.all(
                SelfCleaningRegistry.<String>create(node1.getVertx(), REGISTRY_NAME)
                        .onSuccess(reg -> registryNode1 = reg),
                SelfCleaningRegistry.<String>create(node2.getVertx(), REGISTRY_NAME)
                        .onSuccess(reg -> registryNode2 = reg),
                SelfCleaningRegistry.<String>create(node3.getVertx(), REGISTRY_NAME)
                        .onSuccess(reg -> registryNode3 = reg))
                .mapEmpty();
    }

    private Future<Void> fillRegistries() {
        List<Future<?>> registrationFutures = new ArrayList<>();
        registrationFutures.add(registryNode1.register(CUSTOM_VALUE_KEY_NODE_1, CUSTOM_VALUES));
        registrationFutures.add(registryNode1.register(SHARED_VALUE_KEY, SHARED_VALUES_NODE_1));
        registrationFutures.add(registryNode2.register(CUSTOM_VALUE_KEY_NODE_2, CUSTOM_VALUES));
        registrationFutures.add(registryNode2.register(SHARED_VALUE_KEY, SHARED_VALUES_NODE_2));
        registrationFutures.add(registryNode3.register(CUSTOM_VALUE_KEY_NODE_3, CUSTOM_VALUES));
        registrationFutures.add(registryNode3.register(SHARED_VALUE_KEY, SHARED_VALUES_NODE_3));
        return allComposite(registrationFutures).mapEmpty();
    }

    private Future<Void> verifyEntries(SelfCleaningRegistry<String> registry, Map<String, List<String>> expected,
            VertxTestContext testContext) {
        List<Future<Void>> entriesValidated = new ArrayList<>();
        expected.forEach((key, valueList) -> entriesValidated.add(registry.get(key).compose(values -> {
            testContext.verify(() -> assertThat(values).containsExactlyElementsIn(valueList));
            return succeededFuture();
        })));
        return allComposite(entriesValidated).mapEmpty();
    }
}
