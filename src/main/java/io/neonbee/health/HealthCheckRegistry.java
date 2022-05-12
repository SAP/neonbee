package io.neonbee.health;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import com.google.common.annotations.VisibleForTesting;

import io.neonbee.NeonBee;
import io.neonbee.health.internal.HealthCheck;
import io.neonbee.logging.LoggingFacade;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.healthchecks.CheckResult;
import io.vertx.ext.healthchecks.HealthChecks;
import io.vertx.ext.healthchecks.Status;

public class HealthCheckRegistry {
    private static final LoggingFacade LOGGER = LoggingFacade.create();

    @VisibleForTesting
    HealthChecks healthChecks;

    @VisibleForTesting
    final Map<String, HealthCheck> checks;

    private final Map<String, HealthCheck> immutableChecks;

    private final Vertx vertx;

    /**
     * Constructs a new instance of {@link HealthCheckRegistry}.
     *
     * @param vertx the current Vert.x instance
     */
    public HealthCheckRegistry(Vertx vertx) {
        this.vertx = vertx;
        checks = new HashMap<>();
        immutableChecks = Collections.unmodifiableMap(checks);
        healthChecks = HealthChecks.create(vertx);
    }

    /**
     * Get a map with all health checks registered on this node, with the id of the health check as key.
     *
     * @return the health checks map
     */
    public Map<String, HealthCheck> getHealthChecks() {
        return immutableChecks;
    }

    /**
     * Registers a global health check. A global health check is a check that theoretically could be performed by every
     * node, and does not check node specific parameters like CPU or RAM, for instance.
     *
     * @param id            the id of the health check
     * @param retentionTime the duration how long a cached health status should be returned
     * @param procedure     the health check procedure that should be registered
     * @param config        the health check config
     * @return the registered health check, or null if health checks are disabled globally
     * @throws HealthCheckException if the health check is already registered
     */
    public HealthCheck registerGlobalCheck(String id, long retentionTime,
            Function<NeonBee, Handler<Promise<Status>>> procedure, JsonObject config) throws HealthCheckException {
        return register(id, retentionTime, true, procedure, config);
    }

    /**
     * Registers a node health check, which checks node specific parameters like CPU or RAM.
     *
     * @param id            the id of the health check
     * @param retentionTime the duration how long a cached health status should be returned
     * @param procedure     the health check procedure that should be registered
     * @param config        the health check config
     * @return the registered health check, or null if health checks are disabled globally
     * @throws HealthCheckException if the health check is already registered
     */
    public HealthCheck registerNodeCheck(String id, long retentionTime,
            Function<NeonBee, Handler<Promise<Status>>> procedure, JsonObject config) throws HealthCheckException {
        String nodePrefix = "node/" + NeonBee.get(vertx).getNodeId().strip() + "/";
        return register(nodePrefix + id, retentionTime, false, procedure, config);
    }

    /**
     * Registers a passed health check to the registry.
     *
     * @param healthCheck the health check to register
     * @return the health check instance for fluent use.
     */
    public Future<HealthCheck> register(AbstractHealthCheck healthCheck) {
        return healthCheck.register(this);
    }

    /**
     * Unregister a health check from the registry.
     *
     * @param healthCheck the health check that should be unregistered
     */
    public void unregister(HealthCheck healthCheck) {
        unregister(healthCheck.getId());
    }

    /**
     * Unregister a health check from the registry.
     *
     * @param id the id of the health check that should be unregistered
     */
    public void unregister(String id) {
        healthChecks.unregister(id);
        checks.remove(id);
    }

    @SuppressWarnings({ "PMD.AvoidSynchronizedAtMethodLevel", "checkstyle:OverloadMethodsDeclarationOrder" })
    private synchronized HealthCheck register(String id, long retentionTime, boolean global,
            Function<NeonBee, Handler<Promise<Status>>> procedure, JsonObject healthCheckConfig)
            throws HealthCheckException {
        if (healthCheckConfig != null && !healthCheckConfig.getBoolean("enabled", true)) {
            LOGGER.warn("HealthCheck '{}' is inactive.", id);
            return null;
        }

        if (checks.containsKey(id)) {
            throw new HealthCheckException("HealthCheck '" + id + "' already registered.");
        }

        healthChecks.register(id, getTimeout(healthCheckConfig).toMillis(), procedure.apply(NeonBee.get(vertx)));
        HealthCheck healthCheck = new HealthCheck() {
            private Future<CheckResult> lastCheckResult;

            private Instant lastCheck;

            @Override
            public synchronized Future<CheckResult> result() {
                Instant now = Instant.now();
                if (lastCheckResult == null || now.isAfter(lastCheck.plusSeconds(getRetentionTime()))) {
                    lastCheck = now;
                    return lastCheckResult = healthChecks.checkStatus(id);
                }

                return lastCheckResult;
            }

            @Override
            public String getId() {
                return id;
            }

            @Override
            public long getRetentionTime() {
                return retentionTime;
            }

            @Override
            public boolean isGlobal() {
                return global;
            }
        };

        this.checks.put(id, healthCheck);

        return healthCheck;
    }

    private Duration getTimeout(JsonObject healthCheckConfig) {
        long globalTimeout = NeonBee.get(vertx).getConfig().getHealthConfig().getTimeout();
        return Duration.ofSeconds(Optional.ofNullable(healthCheckConfig).map(c -> c.getLong("timeout", globalTimeout))
                .orElse(globalTimeout));
    }
}
