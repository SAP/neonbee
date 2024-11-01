package io.neonbee.internal.hazelcast;

import static io.neonbee.NeonBeeInstanceConfiguration.ClusterManager.HAZELCAST;
import static io.neonbee.test.base.NeonBeeTestBase.LONG_RUNNING_TEST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;

import com.hazelcast.replicatedmap.ReplicatedMap;

import io.neonbee.NeonBee;
import io.neonbee.NeonBeeExtension;
import io.neonbee.NeonBeeInstanceConfiguration;
import io.neonbee.internal.cluster.ClusterHelper;
import io.vertx.core.Future;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxTestContext;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;

@Tag(LONG_RUNNING_TEST)
@Execution(ExecutionMode.SAME_THREAD)
@ExtendWith(NeonBeeExtension.class)
@Isolated
class ReplicatedAsyncMapTest {

    private static <K, V> ReplicatedAsyncMap<K, V> getHazelcastReplicatedMap(NeonBee neonBee) {
        ReplicatedMap<K, V> replicatedMap = ClusterHelper.getHazelcastClusterManager(neonBee.getVertx())
                .map(HazelcastClusterManager::getHazelcastInstance)
                .map(hcm -> hcm.<K, V>getReplicatedMap("test-map"))
                .orElseThrow(() -> new IllegalStateException("Failed to get Hazelcast Replicated Map instance"));
        return new ReplicatedAsyncMap<>(neonBee.getVertx(), replicatedMap);
    }

    @Test
    void testPutAndGet(
            @NeonBeeInstanceConfiguration(clustered = true, activeProfiles = {},
                    clusterManager = HAZELCAST) NeonBee neonBee,
            VertxTestContext testContext) {
        ReplicatedAsyncMap<String, String> replicatedAsyncMap = getHazelcastReplicatedMap(neonBee);

        replicatedAsyncMap
                .put("key1", "value1")
                .compose(ar -> replicatedAsyncMap.get("key1"))
                .onSuccess(value -> testContext.verify(() -> {
                    assertEquals("value1", value);
                    testContext.completeNow();
                }))
                .onFailure(testContext::failNow);
    }

    @Test
    void testPutIfAbsent(
            @NeonBeeInstanceConfiguration(clustered = true, activeProfiles = {},
                    clusterManager = HAZELCAST) NeonBee neonBee,
            VertxTestContext testContext) {
        ReplicatedAsyncMap<String, String> replicatedAsyncMap = getHazelcastReplicatedMap(neonBee);

        Checkpoint checkpoint = testContext.checkpoint(2);
        replicatedAsyncMap
                .putIfAbsent("key2", "value2")
                .onSuccess(returnedValue -> testContext.verify(() -> {
                    assertNull(returnedValue);
                    checkpoint.flag();
                }))
                .compose(o -> replicatedAsyncMap.putIfAbsent("key2", "value3"))
                .onSuccess(returnedValue -> testContext.verify(() -> {
                    assertEquals("value2", returnedValue);
                    checkpoint.flag();
                }))
                .onFailure(testContext::failNow);
    }

    @Test
    void testRemove(
            @NeonBeeInstanceConfiguration(clustered = true, activeProfiles = {},
                    clusterManager = HAZELCAST) NeonBee neonBee,
            VertxTestContext testContext) {
        ReplicatedAsyncMap<String, String> replicatedAsyncMap = getHazelcastReplicatedMap(neonBee);

        Checkpoint checkpoint = testContext.checkpoint(2);
        replicatedAsyncMap.put("key3", "value3")
                .compose(ar -> replicatedAsyncMap.remove("key3"))
                .onSuccess(removedValue -> testContext.verify(() -> {
                    assertEquals("value3", removedValue);
                    checkpoint.flag();
                }))
                .compose(o -> replicatedAsyncMap.get("key3"))
                .onSuccess(returnedValue -> testContext.verify(() -> {
                    assertNull(returnedValue);
                    checkpoint.flag();
                }))
                .onFailure(testContext::failNow);
    }

    @Test
    void testReplace(
            @NeonBeeInstanceConfiguration(clustered = true, activeProfiles = {},
                    clusterManager = HAZELCAST) NeonBee neonBee,
            VertxTestContext testContext) {
        ReplicatedAsyncMap<String, String> replicatedAsyncMap = getHazelcastReplicatedMap(neonBee);

        Checkpoint checkpoint = testContext.checkpoint(2);
        replicatedAsyncMap.put("key4", "value4")
                .compose(o -> replicatedAsyncMap.replace("key4", "newValue4"))
                .onSuccess(returnedValue -> testContext.verify(() -> {
                    assertEquals("value4", returnedValue);
                    checkpoint.flag();
                }))
                .compose(o -> replicatedAsyncMap.get("key4"))
                .onSuccess(returnedValue -> testContext.verify(() -> {
                    assertEquals("newValue4", returnedValue);
                    checkpoint.flag();
                }))
                .onFailure(testContext::failNow);
    }

    @Test
    void testReplaceIfPresent(
            @NeonBeeInstanceConfiguration(clustered = true, activeProfiles = {},
                    clusterManager = HAZELCAST) NeonBee neonBee,
            VertxTestContext testContext) {
        ReplicatedAsyncMap<String, String> replicatedAsyncMap = getHazelcastReplicatedMap(neonBee);

        var checkpoint = testContext.checkpoint(3);
        replicatedAsyncMap.put("key5", "value5")
                .compose(o -> replicatedAsyncMap.replaceIfPresent("key5", null, "value5"))
                .onSuccess(replaced -> testContext.verify(() -> {
                    assertFalse(replaced);
                    checkpoint.flag();
                }))
                .compose(o -> replicatedAsyncMap.replaceIfPresent("key5", "value5", "newValue5"))
                .onSuccess(replaced -> testContext.verify(() -> {
                    assertTrue(replaced);
                    checkpoint.flag();
                }))
                .compose(o -> replicatedAsyncMap.get("key5"))
                .onSuccess(returnedValue -> testContext.verify(() -> {
                    assertEquals("newValue5", returnedValue);
                    checkpoint.flag();
                }))
                .onFailure(testContext::failNow);
    }

    @Test
    void testRemoveIfPresent(
            @NeonBeeInstanceConfiguration(clustered = true, activeProfiles = {},
                    clusterManager = HAZELCAST) NeonBee neonBee,
            VertxTestContext testContext) {
        ReplicatedAsyncMap<String, String> replicatedAsyncMap = getHazelcastReplicatedMap(neonBee);

        var checkpoint = testContext.checkpoint(3);
        replicatedAsyncMap.put("key6", "value")
                .compose(o -> replicatedAsyncMap.removeIfPresent("key6", "otherValue"))
                .onSuccess(removed -> testContext.verify(() -> {
                    assertFalse(removed);
                    checkpoint.flag();
                }))
                .compose(o -> replicatedAsyncMap.removeIfPresent("key6", "value"))
                .onSuccess(removed -> testContext.verify(() -> {
                    assertTrue(removed);
                    checkpoint.flag();
                }))
                .compose(o -> replicatedAsyncMap.get("key"))
                .onSuccess(returnedValue -> testContext.verify(() -> {
                    assertNull(returnedValue);
                    checkpoint.flag();
                }))
                .onFailure(testContext::failNow);
    }

    @Test
    void testClear(
            @NeonBeeInstanceConfiguration(clustered = true, activeProfiles = {},
                    clusterManager = HAZELCAST) NeonBee neonBee,
            VertxTestContext testContext) {
        ReplicatedAsyncMap<String, String> replicatedAsyncMap = getHazelcastReplicatedMap(neonBee);

        var checkpoint = testContext.checkpoint(2);
        Future.all(
                replicatedAsyncMap.put("key7", "value7"),
                replicatedAsyncMap.put("key8", "value8"))
                .compose(o -> replicatedAsyncMap.size())
                .onSuccess(size -> testContext.verify(() -> {
                    assertEquals(2, size);
                    checkpoint.flag();
                }))
                .compose(o -> replicatedAsyncMap.clear())
                .compose(o -> replicatedAsyncMap.size())
                .onSuccess(size -> testContext.verify(() -> {
                    assertEquals(0, size);
                    checkpoint.flag();
                }))
                .onFailure(testContext::failNow);
    }

    @Test
    void testSize(
            @NeonBeeInstanceConfiguration(clustered = true, activeProfiles = {},
                    clusterManager = HAZELCAST) NeonBee neonBee,
            VertxTestContext testContext) {
        ReplicatedAsyncMap<String, String> replicatedAsyncMap = getHazelcastReplicatedMap(neonBee);

        var checkpoint = testContext.checkpoint(3);
        replicatedAsyncMap.size()
                .onSuccess(size -> testContext.verify(() -> {
                    assertEquals(0, size);
                    checkpoint.flag();
                }))
                .compose(o -> replicatedAsyncMap.put("key9", "value9"))
                .compose(o -> replicatedAsyncMap.size())
                .onSuccess(size -> testContext.verify(() -> {
                    assertEquals(1, size);
                    checkpoint.flag();
                }))
                .compose(o -> replicatedAsyncMap.remove("key9"))
                .compose(o -> replicatedAsyncMap.size())
                .onSuccess(size -> testContext.verify(() -> {
                    assertEquals(0, size);
                    checkpoint.flag();
                }))
                .onFailure(testContext::failNow);
    }

    @Test
    void testKeys(
            @NeonBeeInstanceConfiguration(clustered = true, activeProfiles = {},
                    clusterManager = HAZELCAST) NeonBee neonBee,
            VertxTestContext testContext) {
        ReplicatedAsyncMap<String, String> replicatedAsyncMap = getHazelcastReplicatedMap(neonBee);

        Future.all(
                replicatedAsyncMap.put("key10", "value10"),
                replicatedAsyncMap.put("key11", "value11"))
                .compose(o -> replicatedAsyncMap.keys())
                .onSuccess(keys -> testContext.verify(() -> {
                    assertEquals(2, keys.size());
                    assertTrue(keys.contains("key10"));
                    assertTrue(keys.contains("key11"));
                    testContext.completeNow();
                }))
                .onFailure(testContext::failNow);
    }

    @Test
    void testValues(
            @NeonBeeInstanceConfiguration(clustered = true, activeProfiles = {},
                    clusterManager = HAZELCAST) NeonBee neonBee,
            VertxTestContext testContext) {
        ReplicatedAsyncMap<String, String> replicatedAsyncMap = getHazelcastReplicatedMap(neonBee);

        Future.all(
                replicatedAsyncMap.put("key12", "value12"),
                replicatedAsyncMap.put("key13", "value13"))
                .compose(o -> replicatedAsyncMap.values())
                .onSuccess(values -> testContext.verify(() -> {
                    assertEquals(2, values.size());
                    assertTrue(values.contains("value12"));
                    assertTrue(values.contains("value13"));
                    testContext.completeNow();
                }))
                .onFailure(testContext::failNow);
    }

    @Test
    void testEntries(
            @NeonBeeInstanceConfiguration(clustered = true, activeProfiles = {},
                    clusterManager = HAZELCAST) NeonBee neonBee,
            VertxTestContext testContext) {
        ReplicatedAsyncMap<String, String> replicatedAsyncMap = getHazelcastReplicatedMap(neonBee);

        Future.all(
                replicatedAsyncMap.put("key14", "value14"),
                replicatedAsyncMap.put("key15", "value15"))
                .compose(o -> replicatedAsyncMap.entries())
                .onSuccess(entries -> testContext.verify(() -> {
                    assertEquals(2, entries.size());
                    assertEquals("value14", entries.get("key14"));
                    assertEquals("value15", entries.get("key15"));
                    testContext.completeNow();
                }))
                .onFailure(testContext::failNow);
    }
}
