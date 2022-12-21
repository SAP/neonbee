package io.neonbee.health;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.health.MemoryHealthCheck.CRITICAL_THRESHOLD_PERCENTAGE_KEY;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.neonbee.NeonBee;
import io.neonbee.config.HealthConfig;
import io.neonbee.config.NeonBeeConfig;
import io.neonbee.health.internal.MemoryStats;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.healthchecks.HealthChecks;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class MemoryHealthCheckTest {
    private NeonBee neonBee;

    private MemoryHealthCheck memoryHealthCheck;

    private HealthChecks checks;

    @BeforeEach
    void setUp(Vertx vertx) {
        checks = HealthChecks.create(vertx);
        memoryHealthCheck = new MemoryHealthCheck(NeonBee.get(vertx));
        memoryHealthCheck.memoryStats = new MemoryStats() {
            @Override
            public long getMaxHeap() {
                return 512_000_000;
            }

            @Override
            public long getCommittedHeap() {
                return 256_000_000;
            }

            @Override
            public long getFreeHeap() {
                return 128_000_000;
            }

            @Override
            public long getUsedHeap() {
                return this.getCommittedHeap() - this.getFreeHeap();
            }
        };
        neonBee = mock(NeonBee.class);

        when(neonBee.getConfig()).thenReturn(new NeonBeeConfig().setHealthConfig(new HealthConfig()));

        assertThat(memoryHealthCheck.isGlobal()).isFalse();
        assertThat(memoryHealthCheck.getId()).startsWith("os.");
    }

    @Test
    @DisplayName("should set health check to up if used memory is below configured threshold of health config")
    void testCreateProcedureHealthy(VertxTestContext testContext) {
        memoryHealthCheck.config = new JsonObject().put(CRITICAL_THRESHOLD_PERCENTAGE_KEY, 26);

        checks.register(MemoryHealthCheck.NAME, memoryHealthCheck.createProcedure().apply(neonBee));
        checks.checkStatus(MemoryHealthCheck.NAME)
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    assertThat(result.getData().size()).isEqualTo(3);
                    assertThat(result.getData().getString("freeHeapMemory")).isEqualTo("122 MB");
                    assertThat(result.getData().getString("memoryUsedOfTotalPercentage")).matches("50[,.]{1}00%");
                    assertThat(result.getData().getString("memoryUsedOfMaxPercentage")).matches("25[,.]{1}00%");
                    assertThat(result.getUp()).isTrue();
                    testContext.completeNow();
                })));
    }

    @Test
    @DisplayName("should set health check to down if used memory is above configured threshold of health config")
    void testCreateProcedureUnhealthy(VertxTestContext testContext) {
        memoryHealthCheck.config = new JsonObject().put(CRITICAL_THRESHOLD_PERCENTAGE_KEY, 24);

        checks.register(MemoryHealthCheck.NAME, memoryHealthCheck.createProcedure().apply(neonBee));
        checks.checkStatus(MemoryHealthCheck.NAME)
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    assertThat(result.getData().size()).isEqualTo(3);
                    assertThat(result.getData().getString("freeHeapMemory")).isEqualTo("122 MB");
                    assertThat(result.getData().getString("memoryUsedOfTotalPercentage")).matches("50[,.]{1}00%");
                    assertThat(result.getData().getString("memoryUsedOfMaxPercentage")).matches("25[,.]{1}00%");
                    assertThat(result.getUp()).isFalse();
                    testContext.completeNow();
                })));
    }
}
