package io.neonbee.health;

import java.util.function.Function;

import com.google.common.annotations.VisibleForTesting;

import io.neonbee.NeonBee;
import io.neonbee.logging.LoggingFacade;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.SingleThreadEventExecutor;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.healthchecks.Status;

/**
 * A health check which monitors the event loop size and ensures that the
 * <a href="https://vertx.io/docs/vertx-core/java/#golden_rule">Golden Rule of Vert.x</a> is not hurt.
 */
public class EventLoopHealthCheck extends AbstractHealthCheck {

    /**
     * Name of the health check.
     */
    public static final String NAME = "eventloop.utilization";

    @VisibleForTesting
    static final String CRITICAL_EVENT_LOOP_SIZE_KEY = "criticalEventLoopSize";

    private static final int DEFAULT_CRITICAL_EVENT_LOOP_SIZE = 5;

    private static final LoggingFacade LOGGER = LoggingFacade.create();

    /**
     * Constructs an instance of {@link AbstractHealthCheck}.
     *
     * @param neonBee the current NeonBee instance
     */
    public EventLoopHealthCheck(NeonBee neonBee) {
        super(neonBee);
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
    public Function<NeonBee, Handler<Promise<Status>>> createProcedure() {
        return nb -> healthCheckPromise -> {
            int threshold = config.getInteger(CRITICAL_EVENT_LOOP_SIZE_KEY, DEFAULT_CRITICAL_EVENT_LOOP_SIZE);
            JsonObject criticalEventLoops = getCriticalEventLoops(nb, threshold);
            healthCheckPromise.complete(new Status().setOk(criticalEventLoops.isEmpty())
                    .setData(new JsonObject().put("blockedEventLoops", criticalEventLoops)));
        };
    }

    @SuppressWarnings("PMD.DoNotUseThreads")
    private JsonObject getCriticalEventLoops(NeonBee neonBee, int threshold) {
        JsonObject busyEventLoops = new JsonObject();
        for (EventExecutor elg : neonBee.getVertx().nettyEventLoopGroup()) {
            SingleThreadEventExecutor singleEventExecutor = ((SingleThreadEventExecutor) elg);

            if (singleEventExecutor.pendingTasks() > threshold) {
                busyEventLoops.put(singleEventExecutor.threadProperties().name(), singleEventExecutor.pendingTasks());

                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("[{}] Pending Tasks of \"{}\": {}", neonBee.getNodeId(),
                            singleEventExecutor.threadProperties().name(), singleEventExecutor.pendingTasks());
                }
            }
        }
        return busyEventLoops;
    }
}
