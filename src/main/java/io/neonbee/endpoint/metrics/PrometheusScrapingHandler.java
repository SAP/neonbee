package io.neonbee.endpoint.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import io.neonbee.logging.LoggingFacade;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.RoutingContext;
import io.vertx.micrometer.backends.BackendRegistries;
import io.vertx.micrometer.impl.PrometheusScrapingHandlerImpl;

/**
 * A Vert.x Web {@link io.vertx.ext.web.Route} handler for Prometheus metrics scraping.
 * <p>
 * The original Implementation doesn't work with {@link CompositeMeterRegistry}. This implementation fixes this.
 */
public class PrometheusScrapingHandler extends PrometheusScrapingHandlerImpl {
    private static final LoggingFacade LOGGER = LoggingFacade.create();

    private final String registryName;

    /**
     * Constructs a new instance of NeonBeePrometheusHandler.
     */
    public PrometheusScrapingHandler() {
        super();
        registryName = null;
    }

    /**
     * Constructs a new instance of NeonBeePrometheusHandler.
     *
     * @param registryName The name of the micrometer registry
     */
    public PrometheusScrapingHandler(String registryName) {
        super(registryName);
        this.registryName = registryName;
    }

    private static void noPrometheusMeterRegistryPresent(RoutingContext rc) {
        LOGGER.warn("Could not find a PrometheusMeterRegistry in the CompositeMeterRegistry");
        rc.response().setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code())
                .setStatusMessage("Could not find a PrometheusMeterRegistry").end();
    }

    private static void handleWithPrometheusMeterRegistry(RoutingContext rc, PrometheusMeterRegistry pmr) {
        rc.response()
                .putHeader(HttpHeaders.CONTENT_TYPE, "text/plain; version=0.0.4; charset=utf-8")
                .end(pmr.scrape());
    }

    @Override
    public void handle(RoutingContext rc) {
        MeterRegistry meterRegistry;
        if (registryName == null) {
            meterRegistry = BackendRegistries.getDefaultNow();
        } else {
            meterRegistry = BackendRegistries.getNow(registryName);
        }

        if (meterRegistry instanceof CompositeMeterRegistry) {
            CompositeMeterRegistry cmr = (CompositeMeterRegistry) meterRegistry;
            cmr.getRegistries().stream().filter(registry -> registry instanceof NeonBeePrometheusMeterRegistry)
                    .findAny().map(PrometheusMeterRegistry.class::cast)
                    .ifPresentOrElse(pmr -> handleWithPrometheusMeterRegistry(rc, pmr),
                            () -> noPrometheusMeterRegistryPresent(rc));
        } else {
            super.handle(rc);
        }
    }
}
