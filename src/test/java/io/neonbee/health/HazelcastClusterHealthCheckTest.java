package io.neonbee.health;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.health.HazelcastClusterHealthCheck.EXPECTED_CLUSTER_SIZE_KEY;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.hazelcast.cluster.Cluster;
import com.hazelcast.cluster.ClusterState;
import com.hazelcast.cluster.impl.MemberImpl;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.LifecycleService;
import com.hazelcast.partition.PartitionService;

import io.neonbee.NeonBee;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.healthchecks.HealthChecks;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;

@ExtendWith(VertxExtension.class)
class HazelcastClusterHealthCheckTest {
    private NeonBee neonBee;

    private HealthChecks checks;

    private HazelcastClusterManager mockedManager;

    private HazelcastClusterHealthCheck clusterHealthCheck;

    PartitionService mockedPartitionService;

    HazelcastInstance mockedHazelcast;

    LifecycleService mockedLifecycleService;

    Cluster mockedCluster;

    @BeforeEach
    void setUp(Vertx vertx) {
        checks = HealthChecks.create(vertx);
        mockedManager = mock(HazelcastClusterManager.class);
        clusterHealthCheck = new HazelcastClusterHealthCheck(NeonBee.get(vertx), mockedManager);
        neonBee = mock(NeonBee.class);
        mockedPartitionService = mock(PartitionService.class);
        mockedHazelcast = mock(HazelcastInstance.class);
        mockedLifecycleService = mock(LifecycleService.class);
        mockedCluster = mock(Cluster.class);

        when(neonBee.getVertx()).thenReturn(vertx);
        when(mockedPartitionService.isClusterSafe()).thenReturn(true);
        when(mockedHazelcast.getPartitionService()).thenReturn(mockedPartitionService);
        when(mockedLifecycleService.isRunning()).thenReturn(true);
        when(mockedHazelcast.getLifecycleService()).thenReturn(mockedLifecycleService);
        when(mockedManager.getHazelcastInstance()).thenReturn(mockedHazelcast);
        when(mockedCluster.getClusterState()).thenReturn(ClusterState.ACTIVE);
        when(mockedCluster.getMembers()).thenReturn(Set.of(new MemberImpl()));
        when(mockedHazelcast.getCluster()).thenReturn(mockedCluster);

        assertThat(clusterHealthCheck.isGlobal()).isTrue();
        assertThat(clusterHealthCheck.getId()).startsWith("cluster.");
    }

    private static Stream<Arguments> provideStatus() {
        return Stream.of(Arguments.of(true, true), Arguments.of(true, false), Arguments.of(false, true),
                Arguments.of(false, false));
    }

    @ParameterizedTest
    @MethodSource("provideStatus")
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("should set health status only to UP, when cluster state is healthy")
    void testCreateProcedure(boolean clusterUp, boolean serviceUp, VertxTestContext testContext) {
        when(mockedPartitionService.isClusterSafe()).thenReturn(clusterUp);
        when(mockedHazelcast.getPartitionService()).thenReturn(mockedPartitionService);
        when(mockedLifecycleService.isRunning()).thenReturn(serviceUp);
        when(mockedHazelcast.getLifecycleService()).thenReturn(mockedLifecycleService);
        when(mockedManager.getHazelcastInstance()).thenReturn(mockedHazelcast);
        when(mockedCluster.getClusterState()).thenReturn(clusterUp ? ClusterState.ACTIVE : ClusterState.IN_TRANSITION);
        when(mockedHazelcast.getCluster()).thenReturn(mockedCluster);

        clusterHealthCheck.config = new JsonObject().put(EXPECTED_CLUSTER_SIZE_KEY, 1);

        checks.register(HazelcastClusterHealthCheck.NAME, clusterHealthCheck.createProcedure().apply(neonBee));
        checks.checkStatus(HazelcastClusterHealthCheck.NAME)
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    assertThat(result.getUp()).isEqualTo(clusterUp && serviceUp);
                    assertThat(result.getData().size()).isEqualTo(3);

                    assertThat(result.getData().getInteger("clusterSize")).isEqualTo(1);
                    assertThat(result.getData().getString("clusterState"))
                            .isEqualTo(clusterUp ? "ACTIVE" : "IN_TRANSITION");
                    assertThat(result.getData().getString("lifecycleServiceState"))
                            .isEqualTo(serviceUp ? "ACTIVE" : "INACTIVE");
                    testContext.completeNow();
                })));
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("should set health status to DOWN if cluster size does not match the expected from config")
    void testClusterSizeBelowExpected(VertxTestContext testContext) {
        clusterHealthCheck.config = new JsonObject().put(EXPECTED_CLUSTER_SIZE_KEY, 3);

        checks.register(HazelcastClusterHealthCheck.NAME, clusterHealthCheck.createProcedure().apply(neonBee));
        checks.checkStatus(HazelcastClusterHealthCheck.NAME)
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    assertThat(result.getUp()).isFalse();
                    assertThat(result.getData().getInteger("clusterSize")).isEqualTo(1);
                    testContext.completeNow();
                })));
    }
}
