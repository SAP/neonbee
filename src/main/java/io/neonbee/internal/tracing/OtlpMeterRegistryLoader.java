package io.neonbee.internal.tracing;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.registry.otlp.AggregationTemporality;
import io.micrometer.registry.otlp.OtlpConfig;
import io.micrometer.registry.otlp.OtlpMeterRegistry;
import io.neonbee.config.TracingConfig;
import io.neonbee.config.metrics.MicrometerRegistryLoader;
import io.vertx.core.json.JsonObject;

/**
 * A {@link MicrometerRegistryLoader} that creates a Micrometer {@link OtlpMeterRegistry} which forwards all NeonBee /
 * Vert.x meters to an OTLP endpoint (e.g. Dynatrace).
 * <p>
 * NeonBee attaches this registry automatically when telemetry is enabled and {@link TracingConfig#isExportMetrics()
 * metrics export} is requested (see {@code NeonBee#createMicrometerRegistries()}). It can also be configured explicitly
 * through {@code NeonBeeConfig.micrometerRegistries} by referencing this class name and providing an
 * {@code otlpEndpoint} (and optionally {@code otlpApiToken}, {@code exportIntervalSeconds}, {@code serviceName}) in the
 * registry {@code config}.
 */
public class OtlpMeterRegistryLoader implements MicrometerRegistryLoader {

    /**
     * The standard OTLP path suffix for metrics, appended to the configured endpoint if not already present.
     */
    static final String METRICS_PATH = "/v1/metrics";

    /**
     * The default OTLP metrics endpoint (a local OTel Collector), used when no endpoint is configured explicitly.
     */
    static final String DEFAULT_METRICS_ENDPOINT = "http://localhost:4318" + METRICS_PATH;

    private static final String CONFIG_ENDPOINT = "otlpEndpoint";

    private static final String CONFIG_API_TOKEN = "otlpApiToken";

    private static final String CONFIG_INTERVAL = "exportIntervalSeconds";

    private static final String CONFIG_SERVICE_NAME = "serviceName";

    @Override
    public MeterRegistry load(JsonObject config) {
        JsonObject registryConfig = config != null ? config : new JsonObject();
        return createRegistry(registryConfig.getString(CONFIG_ENDPOINT), registryConfig.getString(CONFIG_API_TOKEN),
                registryConfig.getInteger(CONFIG_INTERVAL, TracingConfig.DEFAULT_EXPORT_INTERVAL_SECONDS),
                registryConfig.getString(CONFIG_SERVICE_NAME));
    }

    /**
     * Creates an {@link OtlpMeterRegistry} from a NeonBee {@link TracingConfig}, resolving the {@code service.name} the
     * same way traces do (configured value, else {@code OTEL_SERVICE_NAME} environment variable, else the given
     * fallback).
     *
     * @param config          the telemetry configuration (must have a non-blank {@code otlpEndpoint})
     * @param fallbackService the fallback service name (the NeonBee instance name)
     * @return the created meter registry
     */
    public static MeterRegistry createRegistry(TracingConfig config, String fallbackService) {
        return createRegistry(config.getOtlpEndpoint(), config.getOtlpApiToken(), config.getExportIntervalSeconds(),
                NeonBeeOpenTelemetry.resolveServiceName(config.getServiceName(), fallbackService));
    }

    private static MeterRegistry createRegistry(String endpoint, String apiToken, int intervalSeconds,
            String serviceName) {
        String metricsUrl = (endpoint == null || endpoint.isBlank()) ? DEFAULT_METRICS_ENDPOINT
                : metricsEndpoint(endpoint);
        Map<String, String> headers = new HashMap<>();
        if (apiToken != null && !apiToken.isBlank()) {
            headers.put("Authorization", "Api-Token " + apiToken);
        }
        Map<String, String> resourceAttributes = new HashMap<>();
        if (serviceName != null && !serviceName.isBlank()) {
            resourceAttributes.put("service.name", serviceName);
        }
        int step = intervalSeconds > 0 ? intervalSeconds : TracingConfig.DEFAULT_EXPORT_INTERVAL_SECONDS;

        OtlpConfig otlpConfig = new OtlpConfig() {
            @Override
            public String get(String key) {
                return null; // all values are provided via the overridden methods below
            }

            @Override
            public String url() {
                return metricsUrl;
            }

            @Override
            public Duration step() {
                return Duration.ofSeconds(step);
            }

            @Override
            public Map<String, String> headers() {
                return headers;
            }

            @Override
            public Map<String, String> resourceAttributes() {
                return resourceAttributes;
            }

            @Override
            public AggregationTemporality aggregationTemporality() {
                // Dynatrace ingests OTLP metrics using delta temporality
                return AggregationTemporality.DELTA;
            }
        };

        return new OtlpMeterRegistry(otlpConfig, Clock.SYSTEM);
    }

    /**
     * Appends the standard {@code /v1/metrics} path to the configured endpoint if it is not already present.
     *
     * @param endpoint the configured OTLP endpoint base URL
     * @return the metrics-specific endpoint URL
     */
    static String metricsEndpoint(String endpoint) {
        String normalized = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
        return normalized.endsWith(METRICS_PATH) ? normalized : normalized + METRICS_PATH;
    }
}
