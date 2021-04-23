package io.neonbee.config;

import static io.neonbee.internal.helper.ConfigHelper.collectAdditionalConfig;
import static io.neonbee.internal.helper.ConfigHelper.rephraseConfigNames;

import java.util.List;
import java.util.Optional;

import com.google.common.collect.ImmutableBiMap;

import io.neonbee.endpoint.Endpoint;
import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.handler.ChainAuthHandler;

@DataObject(generateConverter = true, publicConverter = false)
public class EndpointConfig {
    private static final ImmutableBiMap<String, String> REPHRASE_MAP =
            ImmutableBiMap.<String, String>builder().put("authChainConfig", "authenticationChain").build();

    private String type;

    private String basePath;

    private Boolean enabled = true; // endpoints are enabled by default

    private List<AuthHandlerConfig> authChainConfig;

    private JsonObject additionalConfig;

    /**
     * Creates an initial {@linkplain EndpointConfig}.
     */
    public EndpointConfig() {}

    /**
     * Creates a {@linkplain EndpointConfig} parsing a given JSON object.
     *
     * @param json the JSON object to parse
     */
    public EndpointConfig(JsonObject json) {
        this();

        JsonObject newJson = rephraseConfigNames(json.copy(), REPHRASE_MAP, true);
        EndpointConfigConverter.fromJson(newJson, this);
        additionalConfig = Optional.ofNullable(additionalConfig).orElseGet(JsonObject::new)
                .mergeIn(collectAdditionalConfig(newJson, "type", "basePath", "enabled", "authChainConfig"));
    }

    /**
     * Transforms this configuration object into JSON.
     *
     * @return a JSON representation of this configuration
     */
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        EndpointConfigConverter.toJson(this, json);
        Optional.ofNullable(additionalConfig).ifPresent(config -> json.mergeIn(config));
        rephraseConfigNames(json, REPHRASE_MAP, false);
        return json;
    }

    /**
     * Returns the type of the endpoint, a full qualified class name of an {@linkplain Endpoint}.
     *
     * @return the type as string
     */
    public String getType() {
        return type;
    }

    /**
     * Sets the type of the endpoint, must be a full qualified class name of an {@linkplain Endpoint}.
     *
     * @param type the fully qualified class name as the endpoint type
     * @return the {@linkplain EndpointConfig} for fluent use
     */
    @Fluent
    public EndpointConfig setType(String type) {
        this.type = type;
        return this;
    }

    /**
     * Gets base path this endpoint is exposed to.
     *
     * @return the base path as string
     */
    public String getBasePath() {
        return basePath;
    }

    /**
     * Sets the base path for the endpoint.
     *
     * @param basePath the base path as string
     * @return the {@linkplain EndpointConfig} for fluent use
     */
    @Fluent
    public EndpointConfig setBasePath(String basePath) {
        this.basePath = basePath;
        return this;
    }

    /**
     * Returns wether this endpoint is enabled or not. May also return {@code null} in case unset.
     *
     * @return true if enabled, false otherwise or null if unset
     */
    public Boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets / unsets whether this endpoint is enabled or not.
     *
     * @param enabled true, false or null
     * @return the {@linkplain EndpointConfig} for fluent use
     */
    @Fluent
    public EndpointConfig setEnabled(Boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    /**
     * Gets a list of authentication handler configurations to use in a authentication chain.
     *
     * @return the list of authentication handler configurations
     */
    public List<AuthHandlerConfig> getAuthChainConfig() {
        return authChainConfig;
    }

    /**
     * Sets a list of authentication handler configurations, which will be joined into one
     * {@linkplain ChainAuthHandler}.
     *
     * @param authChainConfig the list of authentication handler configurations to initialize
     * @return the {@linkplain EndpointConfig} for fluent use
     */
    @Fluent
    public EndpointConfig setAuthChainConfig(List<AuthHandlerConfig> authChainConfig) {
        this.authChainConfig = authChainConfig;
        return this;
    }

    /**
     * Returns additional configurations for this specific endpoint type.
     *
     * @return additional configurations as JSON object
     */
    @GenIgnore
    public JsonObject getAdditionalConfig() {
        return additionalConfig;
    }

    /**
     * Sets additional configurations for this specific endpoint type.
     *
     * @param additionalConfig the additional configuration to set
     * @return the {@linkplain EndpointConfig} for fluent use
     */
    @Fluent
    @GenIgnore
    public EndpointConfig setAdditionalConfig(JsonObject additionalConfig) {
        this.additionalConfig = additionalConfig;
        return this;
    }
}
