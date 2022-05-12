package io.neonbee.health;

import static io.neonbee.internal.helper.ConfigHelper.readConfig;
import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;

import java.util.function.Function;

import com.google.common.annotations.VisibleForTesting;

import io.neonbee.NeonBee;
import io.neonbee.health.internal.HealthCheck;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.healthchecks.CheckResult;
import io.vertx.ext.healthchecks.Status;

public abstract class AbstractHealthCheck implements HealthCheck {

    @VisibleForTesting
    static final long DEFAULT_RETENTION_TIME = 0L;

    /**
     * The health check config as {@link JsonObject}.
     */
    public JsonObject config;

    private final NeonBee neonBee;

    /**
     * Constructs an instance of {@link AbstractHealthCheck}.
     *
     * @param neonBee the current NeonBee instance
     */
    public AbstractHealthCheck(NeonBee neonBee) {
        this.neonBee = neonBee;
    }

    /**
     * Creates a health check procedure.
     *
     * @return a function which returns a handler with a Status
     */
    abstract Function<NeonBee, Handler<Promise<Status>>> createProcedure();

    @Override
    public long getRetentionTime() {
        return DEFAULT_RETENTION_TIME;
    }

    @Override
    public Future<CheckResult> result() {
        return failedFuture(new HealthCheckException(
                "Abstract health check must be registered in a health check registry, first."));
    }

    /**
     * Registers the health check to a passed {@link HealthCheckRegistry}.
     *
     * @param registry the health check registry
     * @return the {@link HealthCheck} for fluent use
     */
    public Future<HealthCheck> register(HealthCheckRegistry registry) {
        return readConfig(neonBee.getVertx(), this.getClass().getName()).compose(c -> {
            this.config = c;
            try {
                return succeededFuture(
                        isGlobal() ? registry.registerGlobalCheck(getId(), getRetentionTime(), createProcedure(), c)
                                : registry.registerNodeCheck(getId(), getRetentionTime(), createProcedure(), c));
            } catch (HealthCheckException e) {
                return failedFuture(e);
            }
        });
    }
}
