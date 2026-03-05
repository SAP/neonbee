package io.neonbee.endpoint.metrics;

import static io.neonbee.endpoint.Endpoint.createRouter;
import static io.vertx.core.Future.succeededFuture;

import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.neonbee.NeonBee;
import io.neonbee.config.EndpointConfig;
import io.neonbee.endpoint.Endpoint;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;

public class MetricsEndpoint implements Endpoint {

    /**
     * The default path the metrics endpoint is exposed by NeonBee.
     */
    public static final String DEFAULT_BASE_PATH = "/metrics/";

    /**
     * Add {@link NeonBeePrometheusMeterRegistry} to the {@link CompositeMeterRegistry}.
     *
     * Note that this Method is <b>static synchronized</b> to ensure that the registry is only added once.
     *
     * @param vertx the {@link Vertx} instance
     */
    @SuppressWarnings("PMD.AvoidSynchronizedAtMethodLevel")
    private static synchronized void addRegistry(Vertx vertx) {
        CompositeMeterRegistry compositeMeterRegistry = NeonBee.get(vertx).getCompositeMeterRegistry();
        boolean isNotRegisterd = compositeMeterRegistry.getRegistries().stream()
                .noneMatch(NeonBeePrometheusMeterRegistry.class::isInstance);
        if (isNotRegisterd) {
            compositeMeterRegistry.add(new NeonBeePrometheusMeterRegistry(PrometheusConfig.DEFAULT));
        }
    }

    @Override
    public EndpointConfig getDefaultConfig() {
        // as the EndpointConfig stays mutable, do not extract this to a static variable, but return a new object
        return new EndpointConfig().setType(MetricsEndpoint.class.getName()).setBasePath(DEFAULT_BASE_PATH);
    }

    @Override
    public Future<Router> createEndpointRouter(Vertx vertx, String basePath, JsonObject config) {
        addRegistry(vertx);
        return succeededFuture(createRouter(vertx, new PrometheusScrapingHandler(
                config.getString("registryName", NeonBee.get(vertx).getOptions().getMetricsRegistryName()))));
    }
}
