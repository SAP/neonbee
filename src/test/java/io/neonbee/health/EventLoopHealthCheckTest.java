package io.neonbee.health;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.test.helper.OptionsHelper.defaultOptions;

import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.google.common.truth.Truth8;

import io.neonbee.NeonBee;
import io.neonbee.NeonBeeMockHelper;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.healthchecks.HealthChecks;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class EventLoopHealthCheckTest {
    private EventLoopHealthCheck healthCheck;

    private HealthChecks checks;

    @BeforeEach
    void setUp(Vertx vertx) {
        NeonBeeMockHelper.registerNeonBeeMock(vertx, defaultOptions());
        checks = HealthChecks.create(vertx);
        healthCheck = new EventLoopHealthCheck(NeonBee.get(vertx));

        assertThat(healthCheck.isGlobal()).isFalse();
        assertThat(healthCheck.getId()).contains("eventloop");
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("should set health check to up if number of pending tasks is below the configured threshold")
    void testCreateProcedureHealthy(Vertx vertx, VertxTestContext testContext) {
        checks.register(EventLoopHealthCheck.NAME, healthCheck.createProcedure().apply(NeonBee.get(vertx)));
        checks.checkStatus(EventLoopHealthCheck.NAME)
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    assertThat(result.getData().size()).isEqualTo(1);
                    assertThat(result.getData().getJsonObject("blockedEventLoops")).isEqualTo(new JsonObject());
                    assertThat(result.getUp()).isTrue();
                    testContext.completeNow();
                })));
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("should recognize if something blocks the event loop")
    void testDetectionOfBlockedEventLoop(Vertx vertx, VertxTestContext testContext) {
        checks.register(EventLoopHealthCheck.NAME, healthCheck.createProcedure().apply(NeonBee.get(vertx)));
        blockEventLoopThreads(vertx, () -> checks.checkStatus(EventLoopHealthCheck.NAME)
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    assertThat(result.getUp()).isFalse();
                    JsonObject data = result.getData().getJsonObject("blockedEventLoops");
                    assertThat(data.size()).isEqualTo(1);
                    Optional<String> firstEntry = data.getMap().keySet().stream().findFirst();
                    Truth8.assertThat(firstEntry).isPresent();
                    assertThat(firstEntry.get()).startsWith("vert.x-eventloop-thread-");
                    testContext.completeNow();
                }))));
    }

    /**
     * Allows running code while blocking an event-loop thread for 1s. This will generate 9 pending tasks and 1 task
     * that is currently under processing.
     *
     * @param vertx    the current Vert.x instance
     * @param runnable the runnable which contains the workload that should be executed while the event-loop is blocked
     */
    private static void blockEventLoopThreads(Vertx vertx, Runnable runnable) {
        ScheduledExecutorService executorService =
                Executors.newSingleThreadScheduledExecutor(Executors.defaultThreadFactory());
        Future<?> unusedVerificationFuture = executorService.schedule(runnable, 100, TimeUnit.MILLISECONDS);

        Context context = vertx.getOrCreateContext();
        AtomicInteger scheduleCounter = new AtomicInteger(10);
        Future<?> unusedBlockingFuture = executorService.scheduleAtFixedRate(() -> {
            if (scheduleCounter.get() > 0) {
                context.runOnContext(unused -> {
                    try {
                        Thread.sleep(100L); // blocking event-loop thread
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                });
                scheduleCounter.getAndDecrement();
            } else {
                executorService.shutdown();
            }
        }, 0, 5, TimeUnit.MILLISECONDS);
    }
}
