package io.neonbee.config;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.annotations.Fluent;
import io.vertx.core.json.JsonObject;

/**
 * Global metrics configuration.
 */
@DataObject(generateConverter = true, publicConverter = false)
public class MetricsConfig {
    private boolean enabled;

    /**
     * Creates a {@linkplain MicrometerRegistryConfig}.
     */
    public MetricsConfig() {}

    /**
     * Creates a {@linkplain MicrometerRegistryConfig} parsing a given JSON object.
     *
     * @param json the JSON object to parse
     */
    public MetricsConfig(JsonObject json) {
        MetricsConfigConverter.fromJson(json, this);
    }

    /**
     * Are the metrics enabled?
     *
     * @return true if the metrics are enabled, otherwise false.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets the value to enable, disable metrics.
     *
     * @param enabled true if the metrics should be enabled, false otherwise.
     * @return the {@linkplain MetricsConfig} for fluent use
     */
    @Fluent
    public MetricsConfig setEnabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    /**
     * Transforms this configuration object into JSON.
     *
     * @return a JSON representation of this configuration
     */
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        MetricsConfigConverter.toJson(this, json);
        return json;
    }
}
