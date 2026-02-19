package io.neonbee.internal.cluster;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.hazelcast.cluster.Member;
import com.hazelcast.core.HazelcastInstance;

import io.neonbee.test.helper.ReflectionHelper;
import io.neonbee.test.helper.SystemHelper;
import io.vertx.core.Vertx;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.ext.cluster.infinispan.InfinispanClusterManager;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;

@Isolated("This test class modifies static state including LEADER_CACHE and system properties")
@DisplayName("ClusterHelper Tests")
class ClusterHelperTest {

    @AfterEach
    void tearDown() throws Exception {
        // Clear the static cache between tests
        resetLeaderCache();
    }

    private void resetLeaderCache() throws Exception {
        // The LEADER_CACHE is a static final AtomicReference, so we need to set its value to null
        // by calling the set method on the AtomicReference itself
        java.util.concurrent.atomic.AtomicReference<Boolean> cache = ReflectionHelper.getValueOfPrivateStaticField(
                ClusterHelper.class,
                "LEADER_CACHE");
        if (cache != null) {
            cache.set(null);
        }
    }

    @Test
    @DisplayName("isLeader returns true for non-clustered Vertx")
    void isLeaderReturnsTrueForNonClustered() {
        // Given: A non-clustered Vertx instance
        Vertx vertx = mock(Vertx.class);
        when(vertx.isClustered()).thenReturn(false);

        // When: Checking if leader
        boolean result = ClusterHelper.isLeader(vertx);

        // Then: Should return true (single node is leader)
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("isLeader returns true when Hazelcast local member is oldest")
    void isLeaderReturnsTrueWhenHazelcastLocalIsOldest() throws Exception {
        // Given: A clustered Vertx with HazelcastClusterManager
        Vertx vertx = mock(VertxInternal.class);
        when(vertx.isClustered()).thenReturn(true);

        HazelcastClusterManager hcm = mock(HazelcastClusterManager.class);
        HazelcastInstance hzInstance = mock(HazelcastInstance.class);
        com.hazelcast.cluster.Cluster cluster = mock(
                com.hazelcast.cluster.Cluster.class);

        Member localMember = mock(Member.class);
        when(localMember.localMember()).thenReturn(true);
        Set<Member> members = Set.of(localMember);
        when(hzInstance.getCluster()).thenReturn(cluster);
        when(cluster.getMembers()).thenReturn(members);
        when(hcm.getHazelcastInstance()).thenReturn(hzInstance);

        when(((VertxInternal) vertx).clusterManager()).thenReturn(hcm);

        // When: Checking if leader
        boolean result = ClusterHelper.isLeader(vertx);

        // Then: Should return true (local member is first/oldest)
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("isLeader returns false when Hazelcast local member is not oldest")
    void isLeaderReturnsFalseWhenHazelcastLocalIsNotOldest() throws Exception {
        // Given: A clustered Vertx with HazelcastClusterManager
        Vertx vertx = mock(VertxInternal.class);
        when(vertx.isClustered()).thenReturn(true);

        HazelcastClusterManager hcm = mock(HazelcastClusterManager.class);
        HazelcastInstance hzInstance = mock(HazelcastInstance.class);
        com.hazelcast.cluster.Cluster cluster = mock(
                com.hazelcast.cluster.Cluster.class);

        // Oldest member (first) is not local, local member is second
        Member oldestMember = mock(Member.class);
        when(oldestMember.localMember()).thenReturn(false);

        Member localMember = mock(Member.class);
        when(localMember.localMember()).thenReturn(true);

        // Use LinkedHashSet to preserve order - first element (oldestMember) is leader
        LinkedHashSet<Member> members = new LinkedHashSet<>();
        members.add(oldestMember);
        members.add(localMember);

        when(hzInstance.getCluster()).thenReturn(cluster);
        when(cluster.getMembers()).thenReturn(members);
        when(hcm.getHazelcastInstance()).thenReturn(hzInstance);

        when(((VertxInternal) vertx).clusterManager()).thenReturn(hcm);

        // When: Checking if leader
        boolean result = ClusterHelper.isLeader(vertx);

        // Then: Should return false (local member is not oldest)
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("isLeader returns true when Infinispan is coordinator")
    void isLeaderReturnsTrueWhenInfinispanIsCoordinator() throws Exception {
        // Given: A clustered Vertx with InfinispanClusterManager
        Vertx vertx = mock(VertxInternal.class);
        when(vertx.isClustered()).thenReturn(true);

        InfinispanClusterManager icm = mock(InfinispanClusterManager.class);
        org.infinispan.manager.EmbeddedCacheManager cacheManager = mock(
                org.infinispan.manager.EmbeddedCacheManager.class);
        when(cacheManager.isCoordinator()).thenReturn(true);
        when(icm.getCacheContainer()).thenReturn(cacheManager);
        when(((VertxInternal) vertx).clusterManager()).thenReturn(icm);

        // When: Checking if leader
        boolean result = ClusterHelper.isLeader(vertx);

        // Then: Should return true (is coordinator)
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("isLeader returns false when Infinispan is not coordinator")
    void isLeaderReturnsFalseWhenInfinispanIsNotCoordinator() throws Exception {
        // Given: A clustered Vertx with InfinispanClusterManager
        Vertx vertx = mock(VertxInternal.class);
        when(vertx.isClustered()).thenReturn(true);

        InfinispanClusterManager icm = mock(InfinispanClusterManager.class);
        org.infinispan.manager.EmbeddedCacheManager cacheManager = mock(
                org.infinispan.manager.EmbeddedCacheManager.class);
        when(cacheManager.isCoordinator()).thenReturn(false);
        when(icm.getCacheContainer()).thenReturn(cacheManager);
        when(((VertxInternal) vertx).clusterManager()).thenReturn(icm);

        // When: Checking if leader
        boolean result = ClusterHelper.isLeader(vertx);

        // Then: Should return false (not coordinator)
        assertThat(result).isFalse();
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @DisplayName("isLeader respects NEONBEE_CLUSTER_LEADER system property")
    void isLeaderRespectsClusterLeaderSystemProperty(boolean leaderValue)
            throws Exception {
        // Given: A clustered Vertx with generic ClusterManager
        Vertx vertx = mock(VertxInternal.class);
        when(vertx.isClustered()).thenReturn(true);

        ClusterManager cm = mock(ClusterManager.class);
        when(((VertxInternal) vertx).clusterManager()).thenReturn(cm);

        // Set system property
        String originalValue = System.getProperty("NEONBEE_CLUSTER_LEADER");
        try {
            System.setProperty(
                    "NEONBEE_CLUSTER_LEADER",
                    String.valueOf(leaderValue));

            // When: Checking if leader
            boolean result = ClusterHelper.isLeader(vertx);

            // Then: Should return the configured value
            assertThat(result).isEqualTo(leaderValue);
        } finally {
            if (originalValue != null) {
                System.setProperty("NEONBEE_CLUSTER_LEADER", originalValue);
            } else {
                System.clearProperty("NEONBEE_CLUSTER_LEADER");
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @DisplayName("isLeader respects NEONBEE_CLUSTER_LEADER environment variable")
    void isLeaderRespectsClusterLeaderEnvironmentVariable(boolean leaderValue)
            throws Exception {
        // Given: A clustered Vertx with generic ClusterManager
        Vertx vertx = mock(VertxInternal.class);
        when(vertx.isClustered()).thenReturn(true);

        ClusterManager cm = mock(ClusterManager.class);
        when(((VertxInternal) vertx).clusterManager()).thenReturn(cm);

        // Set environment variable (system property not set)
        SystemHelper.withEnvironment(
                Map.of("NEONBEE_CLUSTER_LEADER", String.valueOf(leaderValue)),
                () -> {
                    boolean result = ClusterHelper.isLeader(vertx);
                    assertThat(result).isEqualTo(leaderValue);
                });
    }

    @Test
    @DisplayName("isLeader returns false when NEONBEE_CLUSTER_LEADER is not set")
    void isLeaderReturnsFalseWhenClusterLeaderNotSet() throws Exception {
        // Given: A clustered Vertx with generic ClusterManager, no system property or env var set
        Vertx vertx = mock(VertxInternal.class);
        when(vertx.isClustered()).thenReturn(true);

        ClusterManager cm = mock(ClusterManager.class);
        when(((VertxInternal) vertx).clusterManager()).thenReturn(cm);

        // Clear any existing value
        System.clearProperty("NEONBEE_CLUSTER_LEADER");
        SystemHelper.withEnvironment(
                Map.of(), // empty environment
                () -> {
                    // When: Checking if leader
                    boolean result = ClusterHelper.isLeader(vertx);

                    // Then: Should return false
                    assertThat(result).isFalse();
                });
    }

    @Test
    @DisplayName("isLeader caches the result after first call")
    void isLeaderCachesResult() throws Exception {
        // Given: A non-clustered Vertx instance
        Vertx vertx = mock(Vertx.class);
        when(vertx.isClustered()).thenReturn(false);

        // When: Calling isLeader multiple times
        boolean firstResult = ClusterHelper.isLeader(vertx);
        boolean secondResult = ClusterHelper.isLeader(vertx);
        boolean thirdResult = ClusterHelper.isLeader(vertx);

        // Then: All results should be the same (cached)
        assertThat(firstResult).isTrue();
        assertThat(secondResult).isEqualTo(firstResult);
        assertThat(thirdResult).isEqualTo(firstResult);

        // Verify cache was used
        Object cachedValue = ReflectionHelper.getValueOfPrivateStaticField(
                ClusterHelper.class,
                "LEADER_CACHE");
        assertThat(cachedValue).isNotNull();
    }

    @Test
    @DisplayName("isLeader returns false when no cluster manager is configured")
    void isLeaderReturnsFalseWhenNoClusterManager() throws Exception {
        // Given: A clustered Vertx but no ClusterManager
        Vertx vertx = mock(VertxInternal.class);
        when(vertx.isClustered()).thenReturn(true);
        when(((VertxInternal) vertx).clusterManager()).thenReturn(null);

        // When: Checking if leader
        boolean result = ClusterHelper.isLeader(vertx);

        // Then: Should return false
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("isLeader falls back through cluster manager chain")
    void isLeaderFallsBackThroughChain() throws Exception {
        // Given: A clustered Vertx with generic ClusterManager (not Hazelcast, not Infinispan)
        Vertx vertx = mock(VertxInternal.class);
        when(vertx.isClustered()).thenReturn(true);

        ClusterManager genericManager = mock(ClusterManager.class);
        when(((VertxInternal) vertx).clusterManager())
                .thenReturn(genericManager);

        // Set environment variable as fallback
        SystemHelper.withEnvironment(
                Map.of("NEONBEE_CLUSTER_LEADER", "true"),
                () -> {
                    // When: Checking if leader
                    boolean result = ClusterHelper.isLeader(vertx);

                    // Then: Should return true from environment variable
                    assertThat(result).isTrue();
                });
    }

    @Test
    @DisplayName("system property takes precedence over environment variable")
    void isLeaderSystemPropertyTakesPrecedence() throws Exception {
        // Given: A clustered Vertx with generic ClusterManager
        Vertx vertx = mock(VertxInternal.class);
        when(vertx.isClustered()).thenReturn(true);

        ClusterManager cm = mock(ClusterManager.class);
        when(((VertxInternal) vertx).clusterManager()).thenReturn(cm);

        String originalValue = System.getProperty("NEONBEE_CLUSTER_LEADER");
        try {
            System.setProperty("NEONBEE_CLUSTER_LEADER", "true");

            // Set environment variable with different value
            SystemHelper.withEnvironment(
                    Map.of("NEONBEE_CLUSTER_LEADER", "false"),
                    () -> {
                        // When: Checking if leader
                        boolean result = ClusterHelper.isLeader(vertx);

                        // Then: Should return true (system property value)
                        assertThat(result).isTrue();
                    });
        } finally {
            if (originalValue != null) {
                System.setProperty("NEONBEE_CLUSTER_LEADER", originalValue);
            } else {
                System.clearProperty("NEONBEE_CLUSTER_LEADER");
            }
        }
    }
}
