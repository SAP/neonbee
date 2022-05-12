package io.neonbee.health.internal;

import io.neonbee.health.HealthCheckRegistry;
import io.vertx.core.Future;
import io.vertx.ext.healthchecks.CheckResult;

public interface HealthCheck {
    /**
     * Provides the name of the health check.
     *
     * @return the name of the {@link HealthCheck}
     */
    String getId();

    /**
     * Provides the retention time of a health check, which is defined as the duration between two consecutive health
     * checks, in which a cached result will be returned before a new health check will be requested again.
     *
     * @return the time (in seconds) how long the health check result should be returned from the cache.
     */
    long getRetentionTime();

    /**
     * Indicates whether the health check is a globally scoped health check, or a node-specific health check. A node
     * specific health check should be used for parameters that can be different on every node in a cluster like RAM or
     * CPU, for instance.
     *
     * @return true if the {@link HealthCheck} is a global health check, false otherwise.
     */
    boolean isGlobal();

    /**
     * The result of a health check. Will throw an exception if the health check was not yet registered to a
     * {@link HealthCheckRegistry registry}.
     *
     * @return a Future with the result of the health check.
     */
    Future<CheckResult> result();
}
