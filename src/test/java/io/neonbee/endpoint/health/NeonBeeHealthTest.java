package io.neonbee.endpoint.health;

import static com.google.common.truth.Truth.assertThat;
import static java.util.stream.Collectors.toList;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Vertx;
import io.vertx.ext.healthchecks.CheckResult;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;

@ExtendWith(VertxExtension.class)
class NeonBeeHealthTest {

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    void testStart(Vertx vertx, VertxTestContext testContext) {
        NeonBeeHealth health = new NeonBeeHealth(vertx).setTimeout(3);
        health.start().onComplete(testContext.succeeding(checks -> testContext.verify(() -> {
            health.healthChecks.checkStatus(testContext.succeeding(checkResult -> testContext.verify(() -> {
                assertThat(checkResult.getChecks().size()).isEqualTo(1);
                assertThat(checkResult.getChecks().stream().map(CheckResult::getId).collect(toList()))
                        .isEqualTo(List.of("physical-memory"));
                testContext.completeNow();
            })));
        })));
    }

    @Test
    void testSetTimeout(Vertx vertx) {
        NeonBeeHealth health = new NeonBeeHealth(vertx);
        assertThat(health.timeout).isEqualTo(0L);
        assertThat(health.setTimeout(5).setTimeout(5).timeout).isEqualTo(5000L);
    }

    @Test
    void testEnableClusteredChecks(Vertx vertx) {
        NeonBeeHealth health = new NeonBeeHealth(vertx);
        HazelcastClusterManager mockedClusterManager = mock(HazelcastClusterManager.class);

        assertThat(health.clustered).isFalse();
        assertThat(health.clusterManager).isNull();
        health.enableClusteredChecks(mockedClusterManager);
        assertThat(health.clustered).isTrue();
        assertThat(health.clusterManager).isSameInstanceAs(mockedClusterManager);
    }
}
