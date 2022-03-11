package io.neonbee.data.internal.metrics;

import java.util.List;

import io.micrometer.core.instrument.Tag;
import io.vertx.core.Future;

/**
 * This implementation of the {@link DataVerticleMetrics} interface is used when metrics are disabled.
 */
public class NoopDataVerticleMetrics implements DataVerticleMetrics {

    NoopDataVerticleMetrics() {}

    @Override
    public void reportNumberOfRequests(String name, String description, List<Tag> tags) {
        // This method is intentionally empty.
    }

    @Override
    public void reportActiveRequestsGauge(String name, String description, List<Tag> tags, Future<?> future) {
        // This method is intentionally empty.
    }

    @Override
    public void reportStatusCounter(String name, String description, Iterable<Tag> tags, Future<?> future) {
        // This method is intentionally empty.
    }

    @Override
    public void reportTimingMetric(String name, String description, Iterable<Tag> tags, Future<?> future) {
        // This method is intentionally empty.
    }
}
