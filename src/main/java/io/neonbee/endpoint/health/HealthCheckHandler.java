package io.neonbee.endpoint.health;

import static java.util.Objects.requireNonNull;

import java.util.stream.Stream;

import org.apache.olingo.commons.api.http.HttpStatusCode;

import com.google.common.annotations.VisibleForTesting;

import io.neonbee.data.internal.DataContextImpl;
import io.neonbee.health.HealthCheckRegistry;
import io.neonbee.logging.LoggingFacade;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.healthchecks.CheckResult;
import io.vertx.ext.web.RoutingContext;

public class HealthCheckHandler implements Handler<RoutingContext> {
    @VisibleForTesting
    static final String APPLICATION_JSON_CHARSET_UTF_8 = "application/json;charset=UTF-8";

    private static final LoggingFacade LOGGER = LoggingFacade.create();

    private final HealthCheckRegistry registry;

    /**
     * Constructs an instance of {@link HealthCheckHandler}.
     *
     * @param registry a health check registry
     */
    public HealthCheckHandler(HealthCheckRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void handle(RoutingContext rc) {
        registry.collectHealthCheckResults(new DataContextImpl(rc)).onSuccess(resp -> handleRequest(rc, resp))
                .onFailure(t -> handleFailure(rc, t));
    }

    private static void handleRequest(RoutingContext rc, JsonObject json) {
        requireNonNull(json);
        HttpServerResponse response = rc.response().putHeader(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON_CHARSET_UTF_8);

        int status = isUp(json) ? HttpResponseStatus.OK.code() : HttpResponseStatus.SERVICE_UNAVAILABLE.code();
        if (status == HttpResponseStatus.SERVICE_UNAVAILABLE.code() && hasProcedureError(json)) {
            status = HttpResponseStatus.INTERNAL_SERVER_ERROR.code();
        }

        if ((status == HttpStatusCode.OK.getStatusCode()) && getChecks(json).findAny().isEmpty()) {
            // Special case; no checks available
            response.setStatusCode(HttpStatusCode.NO_CONTENT.getStatusCode()).end();
            return;
        }

        response.setStatusCode(status).end(json.encode());
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
}
