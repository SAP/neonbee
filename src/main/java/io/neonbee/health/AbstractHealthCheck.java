package io.neonbee.health;

import static io.neonbee.internal.helper.ConfigHelper.readConfig;
import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;

import java.util.function.Function;

import com.google.common.annotations.VisibleForTesting;

import io.neonbee.NeonBee;
import io.neonbee.health.internal.HealthCheck;
import io.neonbee.internal.json.ImmutableJsonObject;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.healthchecks.CheckResult;
import io.vertx.ext.healthchecks.Status;

public abstract class AbstractHealthCheck implements HealthCheck {

    @VisibleForTesting
    static final long DEFAULT_RETENTION_TIME = 0L;

    @VisibleForTesting
    static final ImmutableJsonObject DEFAULT_HEALTH_CHECK_CONFIG =
            new ImmutableJsonObject(new JsonObject().put("enabled", true));

    @VisibleForTesting
    JsonObject config;

    private final NeonBee neonBee;

    /**
     * Constructs an instance of {@link AbstractHealthCheck}.
     *
     * @param neonBee the current NeonBee instance
     */
    public AbstractHealthCheck(NeonBee neonBee) {
        this.neonBee = neonBee;
        this.config = DEFAULT_HEALTH_CHECK_CONFIG.mutableCopy();
    }

    /**
     * Creates a health check procedure.
     *
     * @return a function which returns a handler with a Status
     */
    public abstract Function<NeonBee, Handler<Promise<Status>>> createProcedure();

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
        return mergeHealthCheckConfig().compose(c -> {
            try {
                return succeededFuture(isGlobal()
                        ? registry.registerGlobalCheck(getId(), getRetentionTime(), createProcedure(), c.copy())
                        : registry.registerNodeCheck(getId(), getRetentionTime(), createProcedure(), c.copy()));
            } catch (HealthCheckException e) {
                return failedFuture(e);
            }
        });
    }

    /**
     * Gets the health check config as {@link JsonObject}.
     *
     * @return the config as {@link JsonObject}
     */
    public JsonObject getConfig() {
        return config.copy();
    }

    @VisibleForTesting
    Future<JsonObject> mergeHealthCheckConfig() {
        return readConfig(neonBee.getVertx(), this.getClass().getName())
                .onSuccess(configFromFile -> this.config.mergeIn(configFromFile))
                .transform(v -> succeededFuture(this.config));
    }
}
