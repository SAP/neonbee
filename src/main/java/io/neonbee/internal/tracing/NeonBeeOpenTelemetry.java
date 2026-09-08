package io.neonbee.internal.tracing;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.neonbee.NeonBeeOptions;
import io.neonbee.config.NeonBeeConfig;
import io.neonbee.config.TracingConfig;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporterBuilder;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.vertx.core.Vertx;
import io.vertx.core.VertxBuilder;
import io.vertx.tracing.opentelemetry.OpenTelemetryTracingFactory;

/**
 * Factory and lifecycle helper for the NeonBee OpenTelemetry trace pipeline.
 * <p>
 * The {@link OpenTelemetrySdk} has to be attached to the Vert.x instance <b>before</b> it is built (via
 * {@link VertxBuilder#withTracer}). Therefore the telemetry configuration is read from the {@link NeonBeeOptions} (see
 * {@link NeonBeeOptions#getTracingConfig()}), which is available before Vert.x is created.
 * <p>
 * Metrics are handled separately through Micrometer (see {@link io.neonbee.internal.tracing.OtlpMeterRegistryLoader})
 * and do not go through this class.
 */
public final class NeonBeeOpenTelemetry {

    /**
     * The standard OTLP path suffix for traces, appended to the configured endpoint if not already present.
     */
    static final String TRACES_PATH = "/v1/traces";

    /**
     * The OpenTelemetry system property selecting the context storage provider. It has to point to the Vert.x provider
     * so that trace context is propagated across Vert.x' asynchronous boundaries (e.g. event bus / verticle calls).
     */
    static final String CONTEXT_STORAGE_PROVIDER_PROPERTY = "io.opentelemetry.context.contextStorageProvider";

    /**
     * The Vert.x context storage provider expected in {@link #CONTEXT_STORAGE_PROVIDER_PROPERTY}.
     */
    static final String VERTX_CONTEXT_STORAGE_PROVIDER =
            "io.vertx.tracing.opentelemetry.VertxContextStorageProvider";

    private static final Logger LOGGER = LoggerFactory.getLogger(NeonBeeOpenTelemetry.class);

    private static final String OTEL_SERVICE_NAME_ENV = "OTEL_SERVICE_NAME";

    private static final AttributeKey<String> SERVICE_NAME = AttributeKey.stringKey("service.name");

    // keeps the created SDK per Vert.x instance so it can be flushed and closed when the instance shuts down
    private static final Map<Vertx, OpenTelemetrySdk> SDK_INSTANCES = new ConcurrentHashMap<>();

    private NeonBeeOpenTelemetry() {
        // no instances
    }

    /**
     * Builds an {@link OpenTelemetrySdk} for trace export from the given options and, if telemetry / trace export is
     * enabled and configured, attaches it as a tracer to the given Vert.x builder.
     *
     * @param builder the Vert.x builder to attach the tracer to
     * @param options the NeonBee options carrying the {@link TracingConfig}
     * @return the created {@link OpenTelemetrySdk} (to be {@link #register(Vertx, OpenTelemetrySdk) registered} once
     *         the Vert.x instance is built), or an empty optional if trace export is disabled or not configured
     */
    public static Optional<OpenTelemetrySdk> applyTracing(VertxBuilder builder, NeonBeeOptions options) {
        Optional<OpenTelemetrySdk> sdk = buildSdk(options);
        sdk.ifPresent(openTelemetry -> builder.withTracer(new OpenTelemetryTracingFactory(openTelemetry)));
        return sdk;
    }

    /**
     * Builds an {@link OpenTelemetrySdk} for trace export from the given options.
     *
     * @param options the NeonBee options carrying the {@link TracingConfig}
     * @return the created {@link OpenTelemetrySdk}, or an empty optional if trace export is disabled or not configured
     */
    static Optional<OpenTelemetrySdk> buildSdk(NeonBeeOptions options) {
        TracingConfig config = options.getTracingConfig();
        if (config == null || !config.isEnabled() || !config.isExportTraces()) {
            return Optional.empty();
        }

        String endpoint = config.getOtlpEndpoint();
        if (endpoint == null || endpoint.isBlank()) {
            LOGGER.warn(
                    "OpenTelemetry trace export is enabled but no 'otlpEndpoint' is configured; traces will not be exported.");
            return Optional.empty();
        }

        ensureContextStorageProvider();

        OtlpHttpSpanExporterBuilder exporterBuilder =
                OtlpHttpSpanExporter.builder().setEndpoint(tracesEndpoint(endpoint));
        String apiToken = config.getOtlpApiToken();
        if (apiToken != null && !apiToken.isBlank()) {
            exporterBuilder.addHeader("Authorization", "Api-Token " + apiToken);
        }

        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(BatchSpanProcessor.builder(exporterBuilder.build())
                        .setScheduleDelay(Duration.ofSeconds(config.getExportIntervalSeconds())).build())
                .setResource(Resource.getDefault().merge(Resource.create(
                        Attributes.of(SERVICE_NAME,
                                resolveServiceName(config.getServiceName(), options.getInstanceName())))))
                .build();

        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("OpenTelemetry trace export enabled, exporting to {}", tracesEndpoint(endpoint));
        }
        return Optional.of(OpenTelemetrySdk.builder().setTracerProvider(tracerProvider)
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance())).build());
    }

    /**
     * Ensures the OpenTelemetry context storage provider points to the Vert.x provider, which is required to propagate
     * trace context across Vert.x' asynchronous boundaries (e.g. event bus / verticle calls). This should ideally be
     * set as a JVM system property at startup; if it is unset we set it here (early, before any OpenTelemetry context
     * is used) and log how to configure it explicitly. If it is already set to a different value we only warn.
     */
    static void ensureContextStorageProvider() {
        String current = System.getProperty(CONTEXT_STORAGE_PROVIDER_PROPERTY);
        if (current == null || current.isBlank()) {
            System.setProperty(CONTEXT_STORAGE_PROVIDER_PROPERTY, VERTX_CONTEXT_STORAGE_PROVIDER);
            LOGGER.info(
                    "Set system property '{}' to '{}' for OpenTelemetry context propagation. For reliable propagation "
                            + "set it as a JVM argument at startup: -D{}={}",
                    CONTEXT_STORAGE_PROVIDER_PROPERTY, VERTX_CONTEXT_STORAGE_PROVIDER,
                    CONTEXT_STORAGE_PROVIDER_PROPERTY, VERTX_CONTEXT_STORAGE_PROVIDER);
        } else if (!VERTX_CONTEXT_STORAGE_PROVIDER.equals(current)) {
            LOGGER.warn(
                    "System property '{}' is set to '{}', not the Vert.x provider '{}'. Trace context may not be "
                            + "propagated across event bus / verticle calls.",
                    CONTEXT_STORAGE_PROVIDER_PROPERTY, current, VERTX_CONTEXT_STORAGE_PROVIDER);
        }
    }

    /**
     * Bridges the {@link TracingConfig} of a (pre-loaded) {@link NeonBeeConfig} into the {@link NeonBeeOptions}, so it
     * is available before Vert.x is created. The file configuration is applied only if the options do not already carry
     * an explicit OTLP endpoint; a CLI {@code --enable-tracing} force-enable is always preserved.
     *
     * @param options the NeonBee options to enrich (only {@link NeonBeeOptions.Mutable} instances can be modified)
     * @param config  the (pre-loaded) NeonBee configuration, or {@code null}
     */
    public static void bridgeConfigToOptions(NeonBeeOptions options, NeonBeeConfig config) {
        if (config == null || !(options instanceof NeonBeeOptions.Mutable mutableOptions)) {
            return;
        }

        TracingConfig fromConfig = config.getTracingConfig();
        if (fromConfig == null) {
            return;
        }

        TracingConfig fromOptions = options.getTracingConfig();
        // options already fully configured through the command line / programmatically, do not override
        if (fromOptions != null && fromOptions.getOtlpEndpoint() != null
                && !fromOptions.getOtlpEndpoint().isBlank()) {
            return;
        }

        // preserve a CLI force-enable (e.g. --enable-tracing) even if the file config disables telemetry
        boolean enabled = fromConfig.isEnabled() || (fromOptions != null && fromOptions.isEnabled());
        mutableOptions.setTracingConfig(new TracingConfig(fromConfig.toJson()).setEnabled(enabled));
    }

    /**
     * Associates the given SDK with a Vert.x instance so that it is flushed and closed when the instance shuts down.
     *
     * @param vertx the Vert.x instance
     * @param sdk   the SDK to register
     */
    public static void register(Vertx vertx, OpenTelemetrySdk sdk) {
        SDK_INSTANCES.put(vertx, sdk);
    }

    /**
     * Flushes and closes the {@link OpenTelemetrySdk} that was {@link #register(Vertx, OpenTelemetrySdk) registered}
     * for the given Vert.x instance (if any). Safe to call even if no SDK was registered.
     *
     * @param vertx the Vert.x instance being closed
     */
    public static void close(Vertx vertx) {
        if (vertx == null) {
            return;
        }
        OpenTelemetrySdk sdk = SDK_INSTANCES.remove(vertx);
        if (sdk != null) {
            try {
                // closing the SDK flushes any pending spans through the batch span processor
                sdk.close();
            } catch (RuntimeException e) {
                LOGGER.warn("Failed to close OpenTelemetry SDK cleanly", e);
            }
        }
    }

    /**
     * Resolves the effective {@code service.name}: the configured value, else the {@code OTEL_SERVICE_NAME} environment
     * variable, else the given fallback (the NeonBee instance name).
     *
     * @param configured the configured service name (may be null / blank)
     * @param fallback   the fallback service name (the NeonBee instance name)
     * @return the resolved service name
     */
    public static String resolveServiceName(String configured, String fallback) {
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        String fromEnv = System.getenv(OTEL_SERVICE_NAME_ENV);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv;
        }
        return fallback;
    }

    /**
     * Appends the standard {@code /v1/traces} path to the configured endpoint if it is not already present.
     *
     * @param endpoint the configured OTLP endpoint base URL
     * @return the traces-specific endpoint URL
     */
    static String tracesEndpoint(String endpoint) {
        return signalEndpoint(endpoint, TRACES_PATH);
    }

    private static String signalEndpoint(String endpoint, String signalPath) {
        String normalized = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
        return normalized.endsWith(signalPath) ? normalized : normalized + signalPath;
    }
}
