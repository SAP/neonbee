package io.neonbee.internal.verticle;

import java.util.concurrent.TimeUnit;

import com.google.common.annotations.VisibleForTesting;

import io.neonbee.logging.LoggingFacade;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.JsonObject;
import io.vertx.micrometer.MetricsService;

public class MetricsVerticle extends AbstractVerticle {
    private static final LoggingFacade LOGGER = LoggingFacade.create();

    @VisibleForTesting
    private final long metricsPeriod;

    /**
     * The MetricsVerticle is a wrapper for {@link MetricsService} and allows reading metrics.
     *
     * @param interval The interval to read metrics
     * @param unit     The unit of the metrics read interval
     */
    public MetricsVerticle(long interval, TimeUnit unit) {
        super();
        this.metricsPeriod = unit.toMillis(interval);
    }

    @Override
    public void start() {
        vertx.setPeriodic(metricsPeriod, t -> {
            if (LOGGER.isTraceEnabled()) {
                MetricsService metricsService = MetricsService.create(vertx);
                JsonObject metrics = metricsService.getMetricsSnapshot();
                if (metrics != null) {
                    LOGGER.trace("Metrics: {}", metrics);
                }
            }
        });
    }

}
