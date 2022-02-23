package io.neonbee.endpoint.health;

import static io.neonbee.endpoint.Endpoint.createRouter;

import io.neonbee.NeonBee;
import io.neonbee.config.EndpointConfig;
import io.neonbee.endpoint.Endpoint;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.ext.web.Router;

public class HealthEndpoint implements Endpoint {
    /**
     * The default path the metrics endpoint is exposed by NeonBee.
     */
    private static final String DEFAULT_BASE_PATH = "/health/";

    @Override
    public EndpointConfig getDefaultConfig() {
        return new EndpointConfig().setType(HealthEndpoint.class.getName()).setBasePath(DEFAULT_BASE_PATH);
    }

    @Override
    public Router createEndpointRouter(Vertx vertx, String basePath, JsonObject config) {
        NeonBeeHealth health = NeonBee.get(vertx).getNeonBeeHealth();
        return createRouter(vertx, HealthCheckHandler.createWithHealthChecks(health.healthChecks));
    }
}
