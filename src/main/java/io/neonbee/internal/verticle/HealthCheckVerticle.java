package io.neonbee.internal.verticle;

import static io.neonbee.NeonBeeDeployable.NEONBEE_NAMESPACE;
import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.UUID;

import io.neonbee.NeonBee;
import io.neonbee.NeonBeeDeployable;
import io.neonbee.data.DataContext;
import io.neonbee.data.DataQuery;
import io.neonbee.data.DataVerticle;
import io.neonbee.internal.SharedDataAccessor;
import io.neonbee.internal.helper.AsyncHelper;
import io.neonbee.logging.LoggingFacade;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.ext.healthchecks.CheckResult;

@NeonBeeDeployable(namespace = NEONBEE_NAMESPACE, autoDeploy = false)
public class HealthCheckVerticle extends DataVerticle<JsonArray> {

    /**
     * The key in the shared map of a health check verticle.
     */
    public static final String SHARED_MAP_KEY = "healthCheckVerticles";

    private static final String NAME = "_healthCheckVerticle-" + UUID.randomUUID();

    /**
     * The qualified name of the health check verticle.
     */
    public static final String QUALIFIED_NAME = DataVerticle.createQualifiedName(NEONBEE_NAMESPACE, NAME);

    private static final LoggingFacade LOGGER = LoggingFacade.create();

    @Override
    public void start(Promise<Void> promise) {
        Future.<Void>future(super::start).compose(v -> {
            if (NeonBee.get(vertx).getOptions().isClustered()) {
                return register(vertx);
            }
            return Future.succeededFuture();
        }).onComplete(promise);
    }

    @Override
    public Future<JsonArray> retrieveData(DataQuery query, DataContext context) {
        List<Future<JsonObject>> checkList = NeonBee.get(vertx).getHealthCheckRegistry().getHealthChecks().values()
                .stream().map(hc -> hc.result().map(CheckResult::toJson)).collect(toList());
        return AsyncHelper.allComposite(checkList).map(v -> new JsonArray(
                checkList.stream().map(Future::result).peek(r -> r.remove("outcome")).collect(toList())));
    }

    @Override
    public String getName() {
        return NAME;
    }

    private Future<Void> register(Vertx vertx) {
        AsyncMap<String, Object> sharedMap = NeonBee.get(vertx).getAsyncMap();
        return new SharedDataAccessor(vertx, HealthCheckVerticle.class).getLock(SHARED_MAP_KEY).onFailure(throwable -> {
            LOGGER.error("Error acquiring lock with name {}", SHARED_MAP_KEY, throwable);
        }).compose(lock -> sharedMap.get(SHARED_MAP_KEY).compose(qualifiedNamesOrNull -> {
            String qualifiedName = getQualifiedName();
            JsonArray qualifiedNames =
                    qualifiedNamesOrNull != null ? (JsonArray) qualifiedNamesOrNull : new JsonArray();
            if (!qualifiedNames.contains(qualifiedName)) {
                qualifiedNames.add(qualifiedName);
            }

            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("Register health check verticle {} of node {}.", qualifiedName,
                        NeonBee.get(vertx).getNodeId());
            }
            return sharedMap.put(SHARED_MAP_KEY, qualifiedNames);
        }).onComplete(anyResult -> {
            LOGGER.debug("Releasing lock {}", SHARED_MAP_KEY);
            lock.release();
        }));
    }
}
