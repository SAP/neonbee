package io.neonbee.health;

import static io.neonbee.internal.verticle.HealthCheckVerticle.SHARED_MAP_KEY;
import static java.util.stream.Collectors.toList;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import com.google.common.annotations.VisibleForTesting;

import io.neonbee.NeonBee;
import io.neonbee.data.DataContext;
import io.neonbee.data.DataRequest;
import io.neonbee.data.DataVerticle;
import io.neonbee.data.internal.DataContextImpl;
import io.neonbee.health.internal.HealthCheck;
import io.neonbee.internal.helper.AsyncHelper;
import io.neonbee.logging.LoggingFacade;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.healthchecks.CheckResult;
import io.vertx.ext.healthchecks.HealthChecks;
import io.vertx.ext.healthchecks.Status;

public class HealthCheckRegistry {
    private static final String UP = "UP";

    private static final String DOWN = "DOWN";

    private static final String ID_KEY = "id";

    private static final String CHECKS_KEY = "checks";

    private static final String STATUS_KEY = "status";

    private static final String OUTCOME_KEY = "outcome";

    private static final LoggingFacade LOGGER = LoggingFacade.create();

    @VisibleForTesting
    HealthChecks healthChecks;

    @VisibleForTesting
    final Map<String, HealthCheck> checks;

    private final Vertx vertx;

    /**
     * Constructs a new instance of {@link HealthCheckRegistry}.
     *
     * @param vertx the current Vert.x instance
     */
    public HealthCheckRegistry(Vertx vertx) {
        this.vertx = vertx;
        checks = new HashMap<>();
        healthChecks = HealthChecks.create(vertx);
    }

    /**
     * Get a map with all health checks registered on this node, with the id of the health check as key.
     *
     * @return the health checks map
     */
    public Map<String, HealthCheck> getHealthChecks() {
        return Collections.unmodifiableMap(checks);
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
        String nodePrefix = "node." + NeonBee.get(vertx).getNodeId().strip() + ".";
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

    /**
     * Requests health information from all NeonBee nodes registered in the cluster. If NeonBee is not clustered,
     * {@link #getHealthChecks()} is used internally to fetch the data, which gets consolidated and returned.
     *
     * @return the consolidated health check data
     * @see #collectHealthCheckResults(DataContext)
     */
    public Future<JsonObject> collectHealthCheckResults() {
        return collectHealthCheckResults(new DataContextImpl());
    }

    /**
     * Requests health information from all NeonBee nodes registered in the cluster. If NeonBee is not clustered,
     * {@link #getHealthChecks()} is used internally to fetch the data, which gets consolidated and returned.
     *
     * @param dataContext the current data context
     * @return the consolidated health check data
     */
    public Future<JsonObject> collectHealthCheckResults(DataContext dataContext) {
        Future<List<JsonObject>> asyncResults;
        if (NeonBee.get(vertx).getOptions().isClustered()) {
            asyncResults = getClusteredHealthCheckResults(dataContext);
        } else {
            asyncResults = getLocalHealthCheckResults();
        }

        return asyncResults.map(results -> consolidateResults(results, dataContext)).onFailure(
                t -> LOGGER.correlateWith(dataContext).error("Could not consolidate health check information"));
    }

    @VisibleForTesting
    Future<List<JsonObject>> getLocalHealthCheckResults() {
        List<Future<JsonObject>> asyncCheckResults =
                getHealthChecks().values().stream().map(hc -> hc.result().map(CheckResult::toJson)).collect(toList());

        return AsyncHelper.allComposite(asyncCheckResults).map(resolvedCheckResults -> resolvedCheckResults.list()
                .stream().map(JsonObject.class::cast).peek(result -> result.remove("outcome")).collect(toList()));
    }

    private Future<List<JsonObject>> getClusteredHealthCheckResults(DataContext dataContext) {
        return NeonBee.get(vertx).getAsyncMap().get(SHARED_MAP_KEY)
                .map(qualifiedNames -> (qualifiedNames != null ? (JsonArray) qualifiedNames : new JsonArray()))
                .compose(qualifiedNames -> AsyncHelper.allComposite(sendDataRequests(qualifiedNames, dataContext)))
                .map(resolvedRequests -> {
                    return resolvedRequests.list().stream().map(JsonArray.class::cast).flatMap(JsonArray::stream)
                            .map(JsonObject.class::cast).collect(toList());
                });
    }

    private List<Future<JsonArray>> sendDataRequests(JsonArray qualifiedNames, DataContext dataContext) {
        return qualifiedNames.stream().map(Object::toString).map(DataRequest::new)
                .map(dr -> DataVerticle.<JsonArray>requestData(vertx, dr, dataContext).onSuccess(data -> {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.correlateWith(dataContext).debug("Retrieved health check of verticle {}",
                                dr.getQualifiedName());
                    }
                }).onFailure(t -> {
                    if (LOGGER.isErrorEnabled()) {
                        LOGGER.correlateWith(dataContext).error("Could not retrieve health check data from verticle {}",
                                dr.getQualifiedName(), t.getCause());
                    }
                })).collect(toList());
    }

    private JsonObject consolidateResults(List<JsonObject> healthCheckResults, DataContext dataContext) {
        AtomicReference<String> aggregatedStatus = new AtomicReference<>(UP);
        Map<String, JsonObject> consolidatedChecks = new HashMap<>();

        healthCheckResults.stream().forEach(checkResult -> {
            String checkId = checkResult.getString(ID_KEY);
            String status = checkResult.getString(STATUS_KEY);

            if (checkId == null || status == null) {
                LOGGER.correlateWith(dataContext).warn("Detected inconsistent health check");
                return;
            }

            if (consolidatedChecks.containsKey(checkId)) {
                if (!status.equals(consolidatedChecks.get(checkId).getString(STATUS_KEY))) {
                    LOGGER.correlateWith(dataContext).warn("Detected inconsistent status of health check {}", checkId);
                    // we keep the already existing entry
                }
            } else {
                consolidatedChecks.put(checkId, checkResult);
                if (DOWN.equals(status)) {
                    aggregatedStatus.set(DOWN);
                }
            }
        });

        return new JsonObject().put(CHECKS_KEY, new JsonArray(new ArrayList<>(consolidatedChecks.values())))
                .put(STATUS_KEY, aggregatedStatus.get()).put(OUTCOME_KEY, aggregatedStatus.get());
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
