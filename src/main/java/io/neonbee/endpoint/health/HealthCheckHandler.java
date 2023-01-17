package io.neonbee.endpoint.health;

import static io.neonbee.internal.helper.StringHelper.EMPTY;
import static java.util.Objects.requireNonNull;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.apache.olingo.commons.api.http.HttpStatusCode;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;

import io.neonbee.data.internal.DataContextImpl;
import io.neonbee.health.HealthCheckRegistry;
import io.neonbee.logging.LoggingFacade;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.healthchecks.CheckResult;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.RoutingContext;

/**
 * {@link HealthCheckHandler} is a class that implements the Vert.x {@link RoutingContext} Handler interface. It is used
 * to handle health check requests by providing a RESTful API for the health status.
 *
 * The class collects the health check results from a central {@link HealthCheckRegistry} and formats the response with
 * appropriate status codes based on the results. This can be retrieved for all checks, or, if a specific and existing
 * health check name is passed in the request path, only health checks for the single health check are returned. By
 * default, the HTTP response is verbose and provide detailed information about every single health check. If the
 * verbose flag is set to false via the constructor, only the overall status and a version number is returned.
 */
public class HealthCheckHandler implements Handler<RoutingContext> {
    @VisibleForTesting
    static final String APPLICATION_JSON_CHARSET_UTF_8 = "application/json;charset=UTF-8";

    @VisibleForTesting
    static final String VERSION_FILE = "META-INF/neonbee/neonbee-version.txt";

    private static final String DEV_VERSION = "0.0.0+dev";

    private static final LoggingFacade LOGGER = LoggingFacade.create();

    private final HealthCheckRegistry registry;

    private final boolean verbose;

    private final Vertx vertx;

    private final AtomicReference<String> version = new AtomicReference<>();

    /**
     * Constructs an instance of {@link HealthCheckHandler}.
     *
     * @param registry a health check registry
     * @param vertx    the current Vert.x instance
     */
    public HealthCheckHandler(HealthCheckRegistry registry, Vertx vertx) {
        this(registry, true, vertx);
    }

    /**
     * Constructs an instance of {@link HealthCheckHandler}.
     *
     * @param registry a health check registry
     * @param verbose  whether response should contain verbose health information or not
     * @param vertx    the current Vert.x instance
     * @see HealthCheckHandler#HealthCheckHandler(HealthCheckRegistry, Vertx)
     */
    HealthCheckHandler(HealthCheckRegistry registry, boolean verbose, Vertx vertx) {
        this.registry = registry;
        this.verbose = verbose;
        this.vertx = vertx;
    }

    @Override
    public void handle(RoutingContext rc) {
        String checkName = parseCheck(rc);
        registry.collectHealthCheckResults(new DataContextImpl(rc), checkName)
                .onFailure(t -> handleFailure(rc, t))
                .compose(resp -> handleRequest(rc, resp, verbose));
    }

    /**
     * Parses the health check name from the request path if provided.
     *
     * @param rc the {@link RoutingContext} with the request
     * @return the health check name if provided, empty String otherwise
     */
    private String parseCheck(RoutingContext rc) {
        String requestPath = rc.request().path();
        String routePath = Optional.ofNullable(rc.currentRoute())
                .map(Route::getPath)
                .map(path -> path.replaceAll("/+$", EMPTY) + "/")
                .orElse("/");

        if (!requestPath.contains(routePath)) {
            requestPath += '/';
        }

        String baseRoute = (rc.mountPoint() + routePath).replaceAll("/{2,}", "/");
        if (!requestPath.startsWith(baseRoute)) {
            return EMPTY;
        }

        String checkName = requestPath.substring(baseRoute.length());
        if (!Strings.isNullOrEmpty(checkName)) {
            String[] segments = checkName.split("/", 2);
            checkName = segments[0];
        }
        return checkName;
    }

    /**
     * Sets the status code of the response object based on the value of "status" key in the input JsonObject. It also
     * adds the response header and based on verbose mode sets a detailed or non-detailed response body.
     *
     * @param rc      the current {@link RoutingContext}
     * @param json    all consolidated health checks
     * @param verbose if response should be verbose or not
     * @return a succeeded Future if handling the requests is successful, a failed Future otherwise.
     */
    private Future<Void> handleRequest(RoutingContext rc, JsonObject json, boolean verbose) {
        requireNonNull(json);

        HttpServerResponse response = rc.response().putHeader(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON_CHARSET_UTF_8);
        if (!isUp(json)) {
            if (hasProcedureError(json)) {
                response.setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code());
            } else {
                response.setStatusCode(HttpResponseStatus.SERVICE_UNAVAILABLE.code());
            }
        } else if (isNoCheckAvailable(json)) {
            return response.setStatusCode(HttpStatusCode.NO_CONTENT.getStatusCode()).end()
                    .recover(t -> Future.succeededFuture());
        } else {
            response.setStatusCode(HttpResponseStatus.OK.code());
        }

        if (verbose) {
            return response.end(json.encode()).recover(t -> Future.succeededFuture());
        }

        return Future.succeededFuture(version.get()).compose(cachedVersion -> {
            if (cachedVersion != null) {
                return Future.succeededFuture(cachedVersion);
            }
            return vertx.fileSystem().readFile(VERSION_FILE).map(Buffer::toString).onComplete(ar -> {
                if (ar.succeeded()) {
                    version.set(ar.result());
                } else {
                    version.set(DEV_VERSION);
                    if (LOGGER.isErrorEnabled()) {
                        LOGGER.error("Failed to read version file: " + VERSION_FILE, ar.cause());
                    }
                }
            }).recover(t -> Future.succeededFuture(version.get()));
        }).compose(neonbeeVersion -> response
                .end(new JsonObject().put("status", json.getString("status")).put("version", neonbeeVersion).encode()))
                .mapEmpty();
    }

    private static void handleFailure(RoutingContext rc, Throwable throwable) {
        LOGGER.correlateWith(rc).error("Failed to request data from health check registry.", throwable);
        rc.response().setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code())
                .setStatusMessage("Internal Server Error: Could not request any health data.").end();
    }

    /**
     * Retrieves the status from a passed JsonObject.
     *
     * @param json the {@link JsonObject} to check
     * @return the status as boolean
     * @see CheckResult#isUp(CheckResult)
     */
    private static boolean isUp(JsonObject json) {
        return "UP".equals(json.getString("status"));
    }

    private static boolean hasProcedureError(JsonObject json) {
        JsonObject data = json.getJsonObject("data");
        if (data != null && data.getBoolean("procedure-execution-failure", false)) {
            return true;
        }
        return getChecks(json).anyMatch(HealthCheckHandler::hasProcedureError);
    }

    private static Stream<JsonObject> getChecks(JsonObject json) {
        return json.getJsonArray("checks", new JsonArray()).stream().map(j -> (JsonObject) j);
    }

    private boolean isNoCheckAvailable(JsonObject json) {
        return (isUp(json) && getChecks(json).findAny().isEmpty());
    }
}
