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

/**
 * This is a publicly available endpoint meant for external monitoring systems. The endpoint will return HTTP status
 * code 200 if NeonBee is running, and code 503 service unavailable when the status is DOWN. The response contains a
 * JSON like this:
 * <p>
 * <code>
 *   {
 *     "status": "UP",
 *     "version": "1.2.3"
 *   }
 * </code>
 * </p>
 * The "status" is either "UP" or "DOWN", and the "version" is the current NeonBee release version.
 */
public class StatusEndpoint implements Endpoint {
    /**
     * The default path that is used by NeonBee to expose the health endpoint.
     */
    private static final String DEFAULT_BASE_PATH = "/status/";

    @Override
    public EndpointConfig getDefaultConfig() {
        return new EndpointConfig().setType(StatusEndpoint.class.getName()).setBasePath(DEFAULT_BASE_PATH);
    }

    @Override
    public Future<Router> createEndpointRouter(Vertx vertx, String basePath, JsonObject config) {
        HealthCheckRegistry registry = NeonBee.get(vertx).getHealthCheckRegistry();
        return Future.succeededFuture(createRouter(vertx, new HealthCheckHandler(registry, false, vertx)));
    }
}
