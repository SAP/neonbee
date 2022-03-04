package io.neonbee.endpoint.metrics;

import io.micrometer.core.instrument.Clock;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.prometheus.client.CollectorRegistry;

class NeonBeePrometheusMeterRegistry extends PrometheusMeterRegistry {
    NeonBeePrometheusMeterRegistry(PrometheusConfig config) {
        super(config);
    }

    NeonBeePrometheusMeterRegistry(PrometheusConfig config, CollectorRegistry registry, Clock clock) {
        super(config, registry, clock);
    }
}
