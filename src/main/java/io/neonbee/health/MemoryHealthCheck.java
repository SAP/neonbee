package io.neonbee.health;

import java.util.function.Function;

import com.google.common.annotations.VisibleForTesting;
import com.hazelcast.memory.MemorySize;

import io.neonbee.NeonBee;
import io.neonbee.health.internal.MemoryStats;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.healthchecks.Status;

public class MemoryHealthCheck extends AbstractHealthCheck {

    /**
     * Name of the health check.
     */
    public static final String NAME = "os.memory";

    @VisibleForTesting
    static final String CRITICAL_THRESHOLD_PERCENTAGE_KEY = "criticalThresholdPercentage";

    private static final Integer DEFAULT_CRITICAL_THRESHOLD_PERCENTAGE = 90;

    private static final double PERCENTAGE_MULTIPLIER = 100d;

    @VisibleForTesting
    MemoryStats memoryStats;

    /**
     * Constructs an instance of {@link MemoryHealthCheck}.
     *
     * @param neonBee the current NeonBee instance
     */
    public MemoryHealthCheck(NeonBee neonBee) {
        super(neonBee);
        memoryStats = new MemoryStats();
    }

    @Override
    public String getId() {
        return NAME;
    }

    @Override
    public boolean isGlobal() {
        return false;
    }

    @Override
    Function<NeonBee, Handler<Promise<Status>>> createProcedure() {
        return neonBee -> healthCheckPromise -> {
            long usedMemory = memoryStats.getUsedHeap();
            double memoryUsedOfTotalPercentage = (PERCENTAGE_MULTIPLIER * usedMemory) / memoryStats.getCommittedHeap();
            double memoryUsedOfMaxPercentage = (PERCENTAGE_MULTIPLIER * usedMemory) / memoryStats.getMaxHeap();
            boolean critical = memoryUsedOfMaxPercentage > config.getInteger(CRITICAL_THRESHOLD_PERCENTAGE_KEY,
                    DEFAULT_CRITICAL_THRESHOLD_PERCENTAGE);

            healthCheckPromise.complete(new Status().setOk(!critical).setData(
                    new JsonObject().put("freeHeapMemory", MemorySize.toPrettyString(memoryStats.getFreeHeap()))
                            .put("freePhysicalMemory", MemorySize.toPrettyString(memoryStats.getFreePhysical()))
                            .put("memoryUsedOfTotalPercentage", printPercentage(memoryUsedOfTotalPercentage))
                            .put("memoryUsedOfMaxPercentage", printPercentage(memoryUsedOfMaxPercentage))));
        };
    }

    private static String printPercentage(double percentage) {
        return String.format("%.2f%%", percentage);
    }
}
