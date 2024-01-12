package io.neonbee.config;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.json.annotations.JsonGen;
import io.vertx.core.json.JsonObject;

@DataObject
@JsonGen(publicConverter = false)
public class MicrometerRegistryConfig {
    private String className;

    private JsonObject config = new JsonObject();

    /**
     * Creates a {@linkplain MicrometerRegistryConfig}.
     */
    public MicrometerRegistryConfig() {}

    /**
     * Creates a {@linkplain MicrometerRegistryConfig} parsing a given JSON object.
     *
     * @param json the JSON object to parse
     */
    public MicrometerRegistryConfig(JsonObject json) {
        MicrometerRegistryConfigConverter.fromJson(json, this);
    }

    /**
     * Transforms this configuration object into JSON.
     *
     * @return a JSON representation of this configuration
     */
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        MicrometerRegistryConfigConverter.toJson(this, json);
        return json;
    }

    /**
     * Get the class name.
     *
     * @return the class name
     */
    public String getClassName() {
        return className;
    }

    /**
     * The name of the class.
     *
     * @param className the name of the class
     * @return the {@linkplain MicrometerRegistryConfig} for fluent use
     */
    @Fluent
    public MicrometerRegistryConfig setClassName(String className) {
        this.className = className;
        return this;
    }

    /**
     * Get the configuration.
     *
     * @return the configuration
     */
    public JsonObject getConfig() {
        return config;
    }

    /**
     * Set the configuration.
     *
     * @param config the configuration
     * @return the {@linkplain MicrometerRegistryConfig} for fluent use
     */
    @Fluent
    public MicrometerRegistryConfig setConfig(JsonObject config) {
        this.config = config;
        return this;
    }
}
