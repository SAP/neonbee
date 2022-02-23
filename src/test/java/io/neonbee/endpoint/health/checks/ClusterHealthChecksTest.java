package io.neonbee.endpoint.health.checks;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.endpoint.health.checks.ClusterHealthChecks.CLUSTER_PROCEDURE_NAME;
import static io.neonbee.endpoint.health.checks.ClusterHealthChecks.NODE_PROCEDURE_NAME;
import static java.util.stream.Collectors.toList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.List;
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
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import com.hazelcast.cluster.Cluster;
import com.hazelcast.cluster.ClusterState;
import com.hazelcast.cluster.impl.MemberImpl;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.LifecycleService;
import com.hazelcast.partition.PartitionService;

import io.neonbee.NeonBee;
import io.neonbee.endpoint.health.NeonBeeHealth;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.ext.healthchecks.CheckResult;
import io.vertx.ext.healthchecks.Status;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;

@ExtendWith(VertxExtension.class)
class ClusterHealthChecksTest {
    private HazelcastClusterManager mockedManager;

    private HazelcastInstance mockedHazelcast;

    private PartitionService mockedPartitionService;

    @BeforeEach
    void setUp() {
        mockedManager = mock(HazelcastClusterManager.class);
        mockedHazelcast = mock(HazelcastInstance.class);
        mockedPartitionService = mock(PartitionService.class);
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("should register all cluster health checks")
    void testRegister(Vertx vertx, VertxTestContext testContext) {
        NeonBee mockedNeonBee = mock(NeonBee.class);
        when(mockedNeonBee.getVertx()).thenReturn(vertx);

        try (MockedStatic<NeonBee> neonBee = mockStatic(NeonBee.class)) {
            neonBee.when(NeonBee::get).thenReturn(mockedNeonBee);
            NeonBeeHealth health = new NeonBeeHealth(vertx).enableClusteredChecks(mockedManager).setTimeout(12);

            ClusterHealthChecks.register(health).onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                assertThat(health.timeout).isEqualTo(12000L);
                assertThat(health.clusterManager).isSameInstanceAs(mockedManager);

                health.healthChecks.checkStatus().onComplete(testContext.succeeding(status -> testContext.verify(() -> {
                    assertThat(status.getChecks().stream().map(CheckResult::getId).collect(toList()))
                            .isEqualTo(List.of(CLUSTER_PROCEDURE_NAME, NODE_PROCEDURE_NAME));
                    testContext.completeNow();
                })));
            })));
        }
    }

    private static Stream<Arguments> provideStatus() {
        return Stream.of(Arguments.of(true, true), Arguments.of(true, false), Arguments.of(false, true),
                Arguments.of(false, false));
    }

    @ParameterizedTest
    @MethodSource("provideStatus")
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("should set health status to UP, when cluster state is healthy")
    void testCreateClusterCheckUp(boolean clusterUp, boolean serviceUp, Vertx vertx, VertxTestContext testContext) {
        LifecycleService mockedLifecycleService = mock(LifecycleService.class);
        when(mockedPartitionService.isClusterSafe()).thenReturn(clusterUp);
        when(mockedHazelcast.getPartitionService()).thenReturn(mockedPartitionService);
        when(mockedLifecycleService.isRunning()).thenReturn(serviceUp);
        when(mockedHazelcast.getLifecycleService()).thenReturn(mockedLifecycleService);
        when(mockedManager.getHazelcastInstance()).thenReturn(mockedHazelcast);

        Cluster mockedCluster = mock(Cluster.class);
        when(mockedCluster.getClusterState()).thenReturn(ClusterState.ACTIVE);
        when(mockedCluster.getMembers()).thenReturn(Set.of(new MemberImpl()));
        when(mockedHazelcast.getCluster()).thenReturn(mockedCluster);

        Promise<Status> p = Promise.promise();
        ClusterHealthChecks.createClusterCheck(mockedManager, vertx).handle(p);

        p.future().onComplete(testContext.succeeding(status -> testContext.verify(() -> {
            assertThat(status.isOk()).isEqualTo(clusterUp && serviceUp);
            assertThat(status.getData().getInteger("clusterSize")).isEqualTo(1);
            assertThat(status.getData().getString("clusterState")).isEqualTo("ACTIVE");
            assertThat(status.getData().getString("lifecycleServiceState"))
                    .isEqualTo(serviceUp ? "ACTIVE" : "INACTIVE");
            testContext.completeNow();
        })));
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("should set health status according to local member health")
    void testCreateLocalMemberCheckUp(boolean up, Vertx vertx, VertxTestContext testContext) {
        when(mockedPartitionService.isLocalMemberSafe()).thenReturn(up);
        when(mockedHazelcast.getPartitionService()).thenReturn(mockedPartitionService);
        when(mockedManager.getHazelcastInstance()).thenReturn(mockedHazelcast);

        Promise<Status> p = Promise.promise();
        ClusterHealthChecks.createLocalMemberCheck(mockedManager, vertx).handle(p);

        p.future().onComplete(testContext.succeeding(status -> testContext.verify(() -> {
            assertThat(status.isOk()).isEqualTo(up);
            testContext.completeNow();
        })));
    }
}
