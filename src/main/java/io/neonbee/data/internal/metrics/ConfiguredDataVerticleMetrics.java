package io.neonbee.data.internal.metrics;

import java.util.List;

import com.google.common.annotations.VisibleForTesting;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.neonbee.NeonBee;
import io.neonbee.data.DataVerticle;
import io.neonbee.logging.LoggingFacade;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.micrometer.backends.BackendRegistries;

/**
 * This class configures the metrics to report for a {@link DataVerticle}.
 */
public class ConfiguredDataVerticleMetrics implements DataVerticleMetrics {
    /**
     * Key to enable metrics.
     */
    public static final String ENABLED = "enabled";

    /**
     * Key for the name of the registry to use.
     *
     * @deprecated use {@link #METRICS_REGISTRY_NAME} instead
     */
    @Deprecated(forRemoval = true)
    public static final String METER_REGISTRY_NAME = "meterRegistryName";

    /**
     * Key for the name of the registry to use for metrics reporting of this verticle.
     */
    public static final String METRICS_REGISTRY_NAME = "metricsRegistryName";

    /**
     * Key for reporting the request count.
     */
    public static final String NUMBER_OF_REQUESTS = "reportNumberOfRequests";

    /**
     * Key for reporting the currently active requests.
     */
    public static final String ACTIVE_REQUESTS = "reportActiveRequests";

    /**
     * Key for reporting the status counter.
     */
    public static final String STATUS_COUNTER = "reportStatusCounter";

    /**
     * Key for reporting timing values.
     */
    public static final String TIMING = "reportTiming";

    @VisibleForTesting
    static final NoopDataVerticleMetrics DUMMY_IMPL = new NoopDataVerticleMetrics();

    private static final LoggingFacade LOGGER = LoggingFacade.create();

    @VisibleForTesting
    final DataVerticleMetrics reportNumberOfRequests;

    @VisibleForTesting
    final DataVerticleMetrics reportActiveRequestsGauge;

    @VisibleForTesting
    final DataVerticleMetrics reportStatusCounter;

    @VisibleForTesting
    final DataVerticleMetrics reportTimingMetric;

    ConfiguredDataVerticleMetrics(DataVerticleMetrics reportNumberOfRequests,
            DataVerticleMetrics reportActiveRequestsGauge, DataVerticleMetrics reportStatusCounter,
            DataVerticleMetrics reportTimingMetric) {
        this.reportNumberOfRequests = reportNumberOfRequests;
        this.reportActiveRequestsGauge = reportActiveRequestsGauge;
        this.reportStatusCounter = reportStatusCounter;
        this.reportTimingMetric = reportTimingMetric;
    }

    /**
     * Configure the {@link DataVerticleMetrics} to report.
     *
     * To enable all metrics, you must specify the "enabled" key with the value true. Otherwise, no metrics will be
     * reported. You can select the registry to use by specifying the meterRegistryName key with the name of the
     * registry associated with the registry in the micrometer options.
     *
     * If you specify any of the configuration values "reportNumberOfRequests", "reportActiveRequests",
     * "reportStatusCounter", "reportTiming", only the values configured as true will be reported. If you do not specify
     * any of these values, all metrics are reported.
     *
     * Full example:
     *
     * <pre>
     * {@code
     * {
     *     "enabled" : true,
     *     "meterRegistryName" : "default",
     *     "reportNumberOfRequests" : true,
     *     "reportActiveRequests" : true
     *     "reportStatusCounter" : true,
     *     "reportTiming" : true
     * }
     * }
     * </pre>
     *
     * @param neonBee       the NeonBee instance used for this metrics reporting
     * @param metricsConfig {@link JsonObject} containing the metrics configuration.
     * @return configured {@link DataVerticleMetrics}
     */
    public static DataVerticleMetrics configureMetricsReporting(NeonBee neonBee, JsonObject metricsConfig) {
        if (metricsConfig == null || !Boolean.TRUE.equals(metricsConfig.getBoolean(ENABLED))) {
            return DUMMY_IMPL;
        }

        String meterRegistryName =
                metricsConfig.getString(METRICS_REGISTRY_NAME, neonBee.getOptions().getMetricsRegistryName());
        MeterRegistry registry = BackendRegistries.getNow(meterRegistryName);

        if (registry == null) {
            LOGGER.error(
                    "Micrometer registry hasn't been registered yet or it has been stopped. Metrics will not be sent.");
            return DUMMY_IMPL;
        } else {
            DataVerticleMetrics metricsImpl = new DataVerticleMetricsImpl(registry);
            return configureDataVerticleMetrics(metricsConfig, metricsImpl);
        }
    }

    private static DataVerticleMetrics configureDataVerticleMetrics(JsonObject metricsConfig,
            DataVerticleMetrics metricsImpl) {

        int fieldNameSize = metricsConfig.containsKey(METRICS_REGISTRY_NAME) ? 2 : 1;
        boolean activateAllMetrics = metricsConfig.size() == fieldNameSize;
        if (activateAllMetrics) {
            return metricsImpl;
        } else {
            return new ConfiguredDataVerticleMetrics(
                    Boolean.TRUE.equals(metricsConfig.getBoolean(NUMBER_OF_REQUESTS)) ? metricsImpl : DUMMY_IMPL,
                    Boolean.TRUE.equals(metricsConfig.getBoolean(ACTIVE_REQUESTS)) ? metricsImpl : DUMMY_IMPL,
                    Boolean.TRUE.equals(metricsConfig.getBoolean(STATUS_COUNTER)) ? metricsImpl : DUMMY_IMPL,
                    Boolean.TRUE.equals(metricsConfig.getBoolean(TIMING)) ? metricsImpl : DUMMY_IMPL);
        }
    }

    @Override
    public void reportNumberOfRequests(String name, String description, List<Tag> tags) {
        reportNumberOfRequests.reportNumberOfRequests(name, description, tags);
    }

    @Override
    public void reportActiveRequestsGauge(String name, String description, List<Tag> tags, Future<?> future) {
        reportActiveRequestsGauge.reportActiveRequestsGauge(name, description, tags, future);
    }

    @Override
    public void reportStatusCounter(String name, String description, Iterable<Tag> tags, Future<?> future) {
        reportStatusCounter.reportStatusCounter(name, description, tags, future);
    }

    @Override
    public void reportTimingMetric(String name, String description, Iterable<Tag> tags, Future<?> future) {
        reportTimingMetric.reportTimingMetric(name, description, tags, future);
    }
}
