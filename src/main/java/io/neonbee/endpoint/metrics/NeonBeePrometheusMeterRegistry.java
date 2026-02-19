package io.neonbee.endpoint.metrics;

import io.micrometer.core.instrument.Clock;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import io.prometheus.metrics.model.registry.PrometheusRegistry;

class NeonBeePrometheusMeterRegistry extends PrometheusMeterRegistry {
    NeonBeePrometheusMeterRegistry(PrometheusConfig config) {
        super(config);
    }

    NeonBeePrometheusMeterRegistry(PrometheusConfig config, PrometheusRegistry registry, Clock clock) {
        super(config, registry, clock);
    }
}
