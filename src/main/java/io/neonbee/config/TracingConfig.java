package io.neonbee.config;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.json.annotations.JsonGen;
import io.vertx.core.json.JsonObject;

/**
 * Configuration for OpenTelemetry telemetry (traces and metrics). Telemetry is opt-in — disabled by default.
 * <p>
 * When {@link #isEnabled() enabled}, NeonBee exports:
 * <ul>
 * <li>traces (if {@link #isExportTraces()}) via the OpenTelemetry SDK using an OTLP {@code http/protobuf} span
 * exporter, and</li>
 * <li>metrics (if {@link #isExportMetrics()}) by attaching an OTLP Micrometer registry to the existing meter registry,
 * so all Vert.x and NeonBee meters are forwarded.</li>
 * </ul>
 * Both signals are sent to the same {@link #getOtlpEndpoint() OTLP endpoint} (e.g. Dynatrace), which requires the
 * {@code http/protobuf} protocol (gRPC is not supported by Dynatrace).
 */
@DataObject
@JsonGen(publicConverter = false)
public class TracingConfig {

    /**
     * Default OTLP export interval in seconds.
     */
    public static final int DEFAULT_EXPORT_INTERVAL_SECONDS = 60;

    private boolean enabled;

    private boolean exportTraces = true;

    private boolean exportMetrics = true;

    private String serviceName;

    private String otlpEndpoint;

    private String otlpApiToken;

    private int exportIntervalSeconds = DEFAULT_EXPORT_INTERVAL_SECONDS;

    /**
     * Creates a {@linkplain TracingConfig} with default values (telemetry disabled).
     */
    public TracingConfig() {}

    /**
     * Creates a {@linkplain TracingConfig} parsing a given JSON object.
     *
     * @param json the JSON object to parse
     */
    public TracingConfig(JsonObject json) {
        TracingConfigConverter.fromJson(json, this);
    }

    /**
     * Whether telemetry (traces and/or metrics) is enabled.
     *
     * @return true if telemetry is enabled, false otherwise
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Enables or disables telemetry (opt-in, defaults to false).
     *
     * @param enabled true to enable telemetry
     * @return the {@linkplain TracingConfig} for fluent use
     */
    @Fluent
    public TracingConfig setEnabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    /**
     * Whether traces should be exported when telemetry is {@link #isEnabled() enabled}. Defaults to true.
     *
     * @return true if traces should be exported
     */
    public boolean isExportTraces() {
        return exportTraces;
    }

    /**
     * Sets whether traces should be exported when telemetry is enabled.
     *
     * @param exportTraces true to export traces
     * @return the {@linkplain TracingConfig} for fluent use
     */
    @Fluent
    public TracingConfig setExportTraces(boolean exportTraces) {
        this.exportTraces = exportTraces;
        return this;
    }

    /**
     * Whether metrics should be exported when telemetry is {@link #isEnabled() enabled}. Defaults to true.
     *
     * @return true if metrics should be exported
     */
    public boolean isExportMetrics() {
        return exportMetrics;
    }

    /**
     * Sets whether metrics should be exported when telemetry is enabled.
     *
     * @param exportMetrics true to export metrics
     * @return the {@linkplain TracingConfig} for fluent use
     */
    @Fluent
    public TracingConfig setExportMetrics(boolean exportMetrics) {
        this.exportMetrics = exportMetrics;
        return this;
    }

    /**
     * Gets the logical service name reported as the {@code service.name} resource attribute. If not set, NeonBee falls
     * back to the {@code OTEL_SERVICE_NAME} environment variable and finally to the NeonBee instance name.
     *
     * @return the service name, or null if not configured
     */
    public String getServiceName() {
        return serviceName;
    }

    /**
     * Sets the logical service name reported as the {@code service.name} resource attribute.
     *
     * @param serviceName the service name
     * @return the {@linkplain TracingConfig} for fluent use
     */
    @Fluent
    public TracingConfig setServiceName(String serviceName) {
        this.serviceName = serviceName;
        return this;
    }

    /**
     * Gets the OTLP endpoint base URL, e.g. {@code https://<env>.live.dynatrace.com/api/v2/otlp} or
     * {@code http://localhost:4318} for a local OTel Collector. The signal-specific paths ({@code /v1/traces},
     * {@code /v1/metrics}) are appended automatically if not already present.
     *
     * @return the OTLP endpoint, or null if not configured
     */
    public String getOtlpEndpoint() {
        return otlpEndpoint;
    }

    /**
     * Sets the OTLP endpoint base URL for exporting traces and metrics directly (without a Dynatrace OneAgent). Must be
     * an {@code http/protobuf} endpoint (gRPC is not supported by Dynatrace).
     *
     * @param otlpEndpoint the OTLP {@code http/protobuf} endpoint base URL
     * @return the {@linkplain TracingConfig} for fluent use
     */
    @Fluent
    public TracingConfig setOtlpEndpoint(String otlpEndpoint) {
        this.otlpEndpoint = otlpEndpoint;
        return this;
    }

    /**
     * Gets the API token used for authenticating against the OTLP endpoint (e.g. a Dynatrace API token with the
     * {@code openTelemetryTrace.ingest} and/or {@code metrics.ingest} scope). May be null when authentication is
     * provided out-of-band (e.g. via the {@code OTEL_EXPORTER_OTLP_HEADERS} environment variable or a collector).
     *
     * @return the API token, or null if not configured
     */
    public String getOtlpApiToken() {
        return otlpApiToken;
    }

    /**
     * Sets the API token for OTLP authentication. Sent as the {@code Authorization: Api-Token <token>} header.
     *
     * @param otlpApiToken the API token
     * @return the {@linkplain TracingConfig} for fluent use
     */
    @Fluent
    public TracingConfig setOtlpApiToken(String otlpApiToken) {
        this.otlpApiToken = otlpApiToken;
        return this;
    }

    /**
     * Gets the interval in seconds at which batched spans and metrics are exported via OTLP.
     *
     * @return export interval in seconds
     */
    public int getExportIntervalSeconds() {
        return exportIntervalSeconds;
    }

    /**
     * Sets the interval in seconds at which batched spans and metrics are exported via OTLP.
     *
     * @param exportIntervalSeconds export interval in seconds
     * @return the {@linkplain TracingConfig} for fluent use
     */
    @Fluent
    public TracingConfig setExportIntervalSeconds(int exportIntervalSeconds) {
        this.exportIntervalSeconds = exportIntervalSeconds;
        return this;
    }

    /**
     * Transforms this configuration object into JSON.
     *
     * @return a JSON representation of this configuration
     */
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        TracingConfigConverter.toJson(this, json);
        return json;
    }
}
