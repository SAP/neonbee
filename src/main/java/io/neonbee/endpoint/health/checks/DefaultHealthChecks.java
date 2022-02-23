package io.neonbee.endpoint.health.checks;

import static com.hazelcast.internal.memory.MemoryStatsSupport.freePhysicalMemory;
import static com.hazelcast.internal.memory.MemoryStatsSupport.totalPhysicalMemory;

import com.google.common.annotations.VisibleForTesting;

import io.neonbee.endpoint.health.NeonBeeHealth;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.healthchecks.HealthChecks;
import io.vertx.ext.healthchecks.Status;

public final class DefaultHealthChecks {

    @VisibleForTesting
    static final String MEMORY_PROCEDURE_NAME = "physical-memory";

    /**
     * Ratio between free and total physical memory. If actual value is below, health check fails.
     */
    private static final double PHYSICAL_MEMORY_THRESHOLD = 0.1;

    private final long timeout;

    private final HealthChecks checks;

    private DefaultHealthChecks(NeonBeeHealth health) {
        this.timeout = health.timeout;
        this.checks = health.healthChecks;
    }

    /**
     * Registers all default health-checks to the {@link HealthChecks} instance of {@link NeonBeeHealth}.
     *
     * @param health the {@link NeonBeeHealth}
     * @return A succeeded Future if registering succeeds, a failed Future otherwise.
     */
    public static Future<Void> register(NeonBeeHealth health) {
        return new DefaultHealthChecks(health).registerAll().mapEmpty();
    }

    private Future<HealthChecks> registerAll() {
        return Future.future(promise -> {
            checks.register(MEMORY_PROCEDURE_NAME, timeout, createPhysicalMemoryCheck());
            promise.complete(checks);
        });
    }

    /**
     * Checks whether the current physical memory consumed plus {@link DefaultHealthChecks#PHYSICAL_MEMORY_THRESHOLD}
     * exceeds the available physical memory.
     *
     * @return a result handler which sets the {@link Status} depending on the amount of memory left.
     */
    @VisibleForTesting
    static Handler<Promise<Status>> createPhysicalMemoryCheck() {
        return promise -> {
            boolean ok = freePhysicalMemory() > Math.round(totalPhysicalMemory() * PHYSICAL_MEMORY_THRESHOLD);
            promise.complete(
                    new Status().setOk(ok).setData(new JsonObject().put("freePhysicalMemory", freePhysicalMemory())
                            .put("totalPhysicalMemory", totalPhysicalMemory())));
        };
    }
}
