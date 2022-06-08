package io.neonbee.endpoint.health;

import static io.neonbee.endpoint.Endpoint.createRouter;

import io.neonbee.NeonBee;
import io.neonbee.config.EndpointConfig;
import io.neonbee.endpoint.Endpoint;
import io.neonbee.health.HealthCheckRegistry;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;

public class HealthEndpoint implements Endpoint {
    /**
     * The default path that is used by NeonBee to expose the health endpoint.
     */
    private static final String DEFAULT_BASE_PATH = "/health/";

    @Override
    public EndpointConfig getDefaultConfig() {
        return new EndpointConfig().setType(HealthEndpoint.class.getName()).setBasePath(DEFAULT_BASE_PATH);
    }

    @Override
    public Future<Router> createEndpointRouter(Vertx vertx, String basePath, JsonObject config) {
        HealthCheckRegistry registry = NeonBee.get(vertx).getHealthCheckRegistry();
        return Future.succeededFuture(createRouter(vertx, new HealthCheckHandler(registry)));
    }
}
