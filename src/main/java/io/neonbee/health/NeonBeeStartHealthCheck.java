package io.neonbee.health;

import java.util.function.Function;

import io.neonbee.NeonBee;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.ext.healthchecks.Status;

/**
 * A health check in order for NeonBee to only become healthy if the booting procedure succeeded.
 */
public class NeonBeeStartHealthCheck extends AbstractHealthCheck {
    /**
     * Name of the health check.
     */
    public static final String NAME = "neonbee.start";

    /**
     * Constructs an instance of {@link NeonBeeStartHealthCheck}.
     *
     * @param neonBee the current NeonBee instance
     */
    public NeonBeeStartHealthCheck(NeonBee neonBee) {
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
        return neonBee -> healthCheckPromise -> {
            healthCheckPromise.complete(new Status().setOk(neonBee.isStarted()));
        };
    }
}
