package io.neonbee.config;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.annotations.Fluent;
import io.vertx.core.json.JsonObject;

/**
 * Global metrics configuration.
 */
@DataObject(generateConverter = true, publicConverter = false)
public class HealthConfig {
    private static final int DEFAULT_TIMEOUT = 1;

    private int timeout = DEFAULT_TIMEOUT;

    private boolean enabled = true;

    /**
     * Constructs an instance of {@linkplain HealthConfig}.
     */
    public HealthConfig() {}

    /**
     * Creates a {@linkplain HealthConfig} parsing a given JSON object.
     *
     * @param json the JSON object to parse
     */
    public HealthConfig(JsonObject json) {
        HealthConfigConverter.fromJson(json, this);
    }

    /**
     * Are health checks enabled?
     *
     * @return true if the health checks are enabled, otherwise false.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets the value to enable, disable health-checks.
     *
     * @param enabled true if health-checks should be enabled, false otherwise.
     * @return the {@linkplain HealthConfig} for fluent use
     */
    @Fluent
    public HealthConfig setEnabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    /**
     * Gets the timeout of health check procedures.
     *
     * @return the timeout in seconds
     */
    public int getTimeout() {
        return timeout;
    }

    /**
     * Sets the timeout (in seconds) of health check procedures.
     *
     * @param healthTimeout the health check timeout to set
     * @return the {@linkplain HealthConfig} for fluent use
     */
    @Fluent
    public HealthConfig setTimeout(int healthTimeout) {
        this.timeout = healthTimeout;
        return this;
    }

    /**
     * Transforms this configuration object into JSON.
     *
     * @return a JSON representation of this configuration
     */
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        HealthConfigConverter.toJson(this, json);
        return json;
    }
}
