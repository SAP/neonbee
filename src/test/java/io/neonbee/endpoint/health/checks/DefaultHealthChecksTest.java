package io.neonbee.endpoint.health.checks;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.endpoint.health.checks.DefaultHealthChecks.MEMORY_PROCEDURE_NAME;
import static java.util.stream.Collectors.toList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;

import com.hazelcast.internal.memory.MemoryStatsSupport;

import io.neonbee.NeonBee;
import io.neonbee.endpoint.health.NeonBeeHealth;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.ext.healthchecks.CheckResult;
import io.vertx.ext.healthchecks.Status;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class DefaultHealthChecksTest {

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("should register all default health checks")
    void testRegister(Vertx vertx, VertxTestContext testContext) {
        NeonBee mockedNeonBee = mock(NeonBee.class);
        when(mockedNeonBee.getVertx()).thenReturn(vertx);

        try (MockedStatic<NeonBee> neonBee = mockStatic(NeonBee.class)) {
            neonBee.when(NeonBee::get).thenReturn(mockedNeonBee);
            NeonBeeHealth health = new NeonBeeHealth(vertx).setTimeout(12);

            DefaultHealthChecks.register(health).onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                assertThat(health.timeout).isEqualTo(12000L);

                health.healthChecks.checkStatus().onComplete(testContext.succeeding(status -> testContext.verify(() -> {
                    assertThat(status.getChecks().stream().map(CheckResult::getId).collect(toList()))
                            .containsExactly(MEMORY_PROCEDURE_NAME);
                    testContext.completeNow();
                })));
            })));
        }
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("should set health status to UP, when memory sufficient")
    void testCreatePhysicalMemoryProcedureDown(VertxTestContext testContext) {
        try (MockedStatic<MemoryStatsSupport> statsSupport = mockStatic(MemoryStatsSupport.class)) {
            statsSupport.when(MemoryStatsSupport::freePhysicalMemory).thenReturn(80L);
            statsSupport.when(MemoryStatsSupport::totalPhysicalMemory).thenReturn(100L);

            Promise<Status> p = Promise.promise();
            DefaultHealthChecks.createPhysicalMemoryCheck().handle(p);
            p.future().onComplete(testContext.succeeding(res -> testContext.verify(() -> {
                assertThat(res.getData().getLong("freePhysicalMemory")).isEqualTo(80L);
                assertThat(res.getData().getLong("totalPhysicalMemory")).isEqualTo(100L);
                assertThat(res.isOk()).isTrue();
                testContext.completeNow();
            })));
        }
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("should set health status to DOWN, when memory not sufficient")
    void testCreatePhysicalMemoryProcedureUp(VertxTestContext testContext) {
        Promise<Status> p = Promise.promise();
        try (MockedStatic<MemoryStatsSupport> statsSupport = mockStatic(MemoryStatsSupport.class)) {
            statsSupport.when(MemoryStatsSupport::freePhysicalMemory).thenReturn(1L);
            statsSupport.when(MemoryStatsSupport::totalPhysicalMemory).thenReturn(100L);

            DefaultHealthChecks.createPhysicalMemoryCheck().handle(p);
            p.future().onComplete(testContext.succeeding(res -> testContext.verify(() -> {
                assertThat(res.getData().getLong("freePhysicalMemory")).isEqualTo(1L);
                assertThat(res.getData().getLong("totalPhysicalMemory")).isEqualTo(100L);
                assertThat(res.isOk()).isFalse();
                testContext.completeNow();
            })));
        }
    }
}
