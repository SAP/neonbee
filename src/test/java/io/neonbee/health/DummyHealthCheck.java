package io.neonbee.health;

import java.util.function.Function;

import io.neonbee.NeonBee;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.ext.healthchecks.Status;

public class DummyHealthCheck extends AbstractHealthCheck {
    /**
     * Name of the health check.
     */
    public static final String DUMMY_ID = "dummy";

    /**
     * Constructs an instance of {@link AbstractHealthCheck}.
     *
     * @param neonBee the current NeonBee instance
     */
    public DummyHealthCheck(NeonBee neonBee) {
        super(neonBee);
    }

    @Override
    public Function<NeonBee, Handler<Promise<Status>>> createProcedure() {
        return nb -> promise -> promise.complete(new Status().setOK());
    }

    @Override
    public String getId() {
        return DUMMY_ID;
    }

    @Override
    public boolean isGlobal() {
        return true;
    }
}
