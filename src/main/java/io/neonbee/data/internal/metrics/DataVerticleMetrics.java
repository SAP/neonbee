package io.neonbee.data.internal.metrics;

import java.util.List;

import io.micrometer.core.instrument.Tag;
import io.vertx.core.Future;

public interface DataVerticleMetrics {

    /**
     * Reports the sum of all calls.
     *
     * @param name        name of the metric
     * @param description description of the metric
     * @param tags        dimensions of a meter used to classify the metric
     */
    void reportNumberOfRequests(String name, String description, List<Tag> tags);

    /**
     * Reports the number of requests waiting for a response.
     *
     * @param name        the name of the metric
     * @param description description of the metric
     * @param tags        dimensions of a meter used to classify the metric
     * @param future      the future to measure
     */
    void reportActiveRequestsGauge(String name, String description, List<Tag> tags, Future<?> future);

    /**
     * Reports status metrics.
     *
     * For example, whether a call could be executed successfully or with errors.
     *
     * @param name        the name of the metric
     * @param description description of the metric
     * @param tags        dimensions of a meter used to classify the metric
     * @param future      the future to measure
     */
    void reportStatusCounter(String name, String description, Iterable<Tag> tags, Future<?> future);

    /**
     * Reports timing metrics such as duration.
     *
     * @param name        the name of the metric
     * @param description description of the metric
     * @param tags        dimensions of a meter used to classify the metric
     * @param future      the future to measure
     */
    void reportTimingMetric(String name, String description, Iterable<Tag> tags, Future<?> future);
}
