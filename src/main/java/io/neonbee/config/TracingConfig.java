package io.neonbee.config;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.json.annotations.JsonGen;
import io.vertx.core.json.JsonObject;

/**
 * Configuration for OpenTelemetry tracing. Tracing is opt-in — disabled by default.
 */
@DataObject
@JsonGen(publicConverter = false)
public class TracingConfig {

    /**
     * Default OTLP export interval in seconds.
     */
    public static final int DEFAULT_EXPORT_INTERVAL_SECONDS = 60;

    private boolean enabled;

    private String otlpEndpoint;

    private String otlpApiToken;

    private int exportIntervalSeconds = DEFAULT_EXPORT_INTERVAL_SECONDS;

    /**
     * Creates a {@linkplain TracingConfig} with default values (tracing disabled).
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
     * Whether tracing is enabled.
     *
     * @return true if tracing is enabled, false otherwise
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Enables or disables tracing (opt-in, defaults to false).
     *
     * @param enabled true to enable tracing
     * @return the {@linkplain TracingConfig} for fluent use
     */
    @Fluent
    public TracingConfig setEnabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    /**
     * Gets the OTLP endpoint URL, e.g. {@code https://<env>.live.dynatrace.com/api/v2/otlp} or
     * {@code http://localhost:4317} for a local OTel Collector / Jaeger.
     *
     * @return the OTLP endpoint, or null if not configured
     */
    public String getOtlpEndpoint() {
        return otlpEndpoint;
    }

    /**
     * Sets the OTLP endpoint URL for exporting traces and metrics directly (without Dynatrace OneAgent).
     *
     * @param otlpEndpoint the OTLP gRPC endpoint URL
     * @return the {@linkplain TracingConfig} for fluent use
     */
    @Fluent
    public TracingConfig setOtlpEndpoint(String otlpEndpoint) {
        this.otlpEndpoint = otlpEndpoint;
        return this;
    }

    /**
     * Gets the API token used for authenticating against the OTLP endpoint (e.g. a Dynatrace API token with
     * {@code ingest.traces} scope).
     *
     * @return the API token, or null if not configured
     */
    public String getOtlpApiToken() {
        return otlpApiToken;
    }

    /**
     * Sets the API token for OTLP authentication.
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
     * Gets the interval in seconds at which metrics are exported via OTLP.
     *
     * @return export interval in seconds
     */
    public int getExportIntervalSeconds() {
        return exportIntervalSeconds;
    }

    /**
     * Sets the interval in seconds at which metrics are exported via OTLP.
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
