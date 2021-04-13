package io.neonbee.endpoint.metrics;

import static io.neonbee.endpoint.Endpoint.createRouter;

import io.neonbee.config.EndpointConfig;
import io.neonbee.endpoint.Endpoint;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.micrometer.PrometheusScrapingHandler;

public class MetricsEndpoint implements Endpoint {
    /**
     * The default path the metrics endpoint is exposed by NeonBee.
     */
    public static final String DEFAULT_BASE_PATH = "/metrics/";

    @Override
    public EndpointConfig getDefaultConfig() {
        // as the EndpointConfig stays mutable, do not extract this to a static variable, but return a new object
        return new EndpointConfig().setType(MetricsEndpoint.class.getName()).setBasePath(DEFAULT_BASE_PATH);
    }

    @Override
    public Router createEndpointRouter(Vertx vertx, String basePath, JsonObject config) {
        return createRouter(vertx, PrometheusScrapingHandler.create(config.getString("registryName")));
    }
}
