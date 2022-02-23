package io.neonbee;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.healthchecks.HealthChecks;

@FunctionalInterface
public interface HealthCheckRegistry {

    /**
     * Registers custom Vert.x {@link io.vertx.ext.healthchecks.HealthChecks} procedures.
     *
     * @param checks the {@link HealthChecks} instance
     * @param vertx  the current Vert.x instance
     * @return a succeeding Future if registering was successful, a failed Future otherwise.
     */
    Future<Void> register(HealthChecks checks, Vertx vertx);
}
