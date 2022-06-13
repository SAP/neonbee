package io.neonbee.health;

import java.util.List;

import io.vertx.core.Vertx;

/**
 * This interface can be used to provide additional health checks to NeonBee. If you implement this interface, NeonBee
 * discovers the implementing class and registers all health checks of the list to NeonBee's health check registry.
 */
@FunctionalInterface
public interface HealthCheckProvider {

    /**
     * Provide custom health checks that will be registered to NeonBee's health check registry.
     *
     * @param vertx the current Vert.x instance
     * @return a succeeded future if registering was successful. A failed Future, otherwise.
     */
    List<AbstractHealthCheck> get(Vertx vertx);
}
