package io.neonbee.config;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.annotations.Fluent;
import io.vertx.core.json.JsonObject;

/**
 * Global CORS configuration.
 */
@DataObject(generateConverter = true, publicConverter = false)
public class CorsConfig {
    private boolean enabled;

    private List<String> origins;

    private List<String> relativeOrigins;

    private Set<String> allowedMethods;

    private Set<String> allowedHeaders;

    private Set<String> exposedHeaders;

    private int maxAgeSeconds = -1;

    private boolean allowCredentials;

    /**
     * Creates a {@linkplain CorsConfig}.
     */
    public CorsConfig() {}

    /**
     * Creates a {@linkplain CorsConfig} parsing a given JSON object.
     *
     * @param json the JSON object to parse
     */
    public CorsConfig(JsonObject json) {
        CorsConfigConverter.fromJson(json, this);
    }

    /**
     * Transforms this configuration object into JSON.
     *
     * @return a JSON representation of this configuration
     */
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        CorsConfigConverter.toJson(this, json);
        return json;
    }

    /**
     * Is the CorsHandler enabled?
     * <p>
     * By default, it is disabled.
     *
     * @return true if the metrics are enabled, otherwise false.
     */
    public boolean isEnabled() {
        return enabled && (origins != null || relativeOrigins != null);
    }

    /**
     * Sets the value to enable, disable CorsHandler.
     *
     * @param enabled true if the CorsHandler should be enabled, false otherwise.
     * @return the {@linkplain CorsConfig} for fluent use
     */
    @Fluent
    public CorsConfig setEnabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    /**
     * Returns the list of allowed static origins.
     * <p>
     * By default, no static origin is set. If no static origin <b>and</b> no relative origin is set, the cors handler
     * will not be added at all.
     *
     * @return the list of allowed origins, or null íf no static origin is set.
     */
    public List<String> getOrigins() {
        return origins;
    }

    /**
     * Set the list of allowed static origins.
     * <p>
     * An origin follows RFC6454 section 7 and is expected to have the format:
     * {@code <scheme> "://" <hostname> [ ":"<port> ]}
     *
     * @param origins the well formatted static origin list
     * @return the {@linkplain CorsConfig} for fluent use
     */
    @Fluent
    public CorsConfig setOrigins(List<String> origins) {
        this.origins = origins;
        return this;
    }

    /**
     * Returns the list of allowed relative origins.
     * <p>
     * By default, no relative origin is set. If no static origin <b>and</b> no relative origin is set, the cors handler
     * will not be added at all.
     *
     * @return the list of allowed relative origins, or null íf no relative origin is set.
     */
    public List<String> getRelativeOrigins() {
        return relativeOrigins;
    }

    /**
     * Set the list of allowed relative origins.
     * <p>
     * A relative origin is a regex that should match the format: {@code <scheme> "://" <hostname> [ ":" <port> ]}.
     *
     * @param relativeOrigins the well formatted relative origin list
     * @return the {@linkplain CorsConfig} for fluent use
     */
    @Fluent
    public CorsConfig setRelativeOrigins(List<String> relativeOrigins) {
        this.relativeOrigins = relativeOrigins;
        return this;
    }

    /**
     * Returns the set of allowed methods.
     * <p>
     * By default, this is null, which means the header <i>access-control-allow-methods</i> is not sent at all.
     *
     * @return the set of allowed methods, or null íf no method is set.
     */
    public Set<String> getAllowedMethods() {
        return allowedMethods;
    }

    /**
     * Set a set of allowed methods.
     *
     * @param allowedMethods the methods to add
     * @return the {@linkplain CorsConfig} for fluent use
     */
    @Fluent
    public CorsConfig setAllowedMethods(Set<String> allowedMethods) {
        this.allowedMethods = allowedMethods;
        return this;
    }

    /**
     * Returns the set of allowed headers.
     * <p>
     * By default, this is null, which means the header <i>access-control-allow-headers</i> is not sent at all.
     *
     * @return the set of allowed headers, or null íf no header is set.
     */
    public Set<String> getAllowedHeaders() {
        return allowedHeaders;
    }

    /**
     * Set a set of allowed headers.
     *
     * @param allowedHeaders the allowed header names
     * @return the {@linkplain CorsConfig} for fluent use
     */
    @Fluent
    public CorsConfig setAllowedHeaders(Set<String> allowedHeaders) {
        this.allowedHeaders = allowedHeaders;
        return this;
    }

    /**
     * Returns the set of exposed headers.
     * <p>
     * By default, this is null, which means the header <i>access-control-expose-headers</i> is not sent at all.
     *
     * @return the set of allowed headers, or null íf no exposed header is set.
     */
    public Set<String> getExposedHeaders() {
        return exposedHeaders;
    }

    /**
     * Set a set of exposed headers.
     *
     * @param exposedHeaders the exposed header names
     * @return the {@linkplain CorsConfig} for fluent use
     */
    @Fluent
    public CorsConfig setExposedHeaders(Set<String> exposedHeaders) {
        this.exposedHeaders = exposedHeaders;
        return this;
    }

    /**
     * Returns how long the browser should cache the information in seconds.
     * <p>
     * By default, it is -1, which means the header <i>access-control-max-age</i> is not sent at all.
     *
     * @return how long the browser should cache the information.
     */
    public int getMaxAgeSeconds() {
        return maxAgeSeconds;
    }

    /**
     * Set how long the browser should cache the information.
     *
     * @param maxAgeSeconds max age in seconds
     * @return the {@linkplain CorsConfig} for fluent use
     */
    @Fluent
    public CorsConfig setMaxAgeSeconds(int maxAgeSeconds) {
        this.maxAgeSeconds = maxAgeSeconds;
        return this;
    }

    /**
     * Are credentials are allowed?
     * <p>
     * By default, it is not allowed.
     *
     * @return true if credentials are allowed, otherwise false.
     */
    @SuppressWarnings("PMD.BooleanGetMethodName")
    public boolean getAllowCredentials() {
        return allowCredentials;
    }

    /**
     * Set whether credentials are allowed or not.
     *
     * @param allowCredentials true if allowed
     * @return the {@linkplain CorsConfig} for fluent use
     */
    @Fluent
    public CorsConfig setAllowCredentials(boolean allowCredentials) {
        this.allowCredentials = allowCredentials;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CorsConfig)) {
            return false;
        }
        CorsConfig that = (CorsConfig) o;
        return enabled == that.enabled && maxAgeSeconds == that.maxAgeSeconds
                && allowCredentials == that.allowCredentials && Objects.equals(origins, that.origins)
                && Objects.equals(relativeOrigins, that.relativeOrigins)
                && Objects.equals(allowedMethods, that.allowedMethods)
                && Objects.equals(allowedHeaders, that.allowedHeaders)
                && Objects.equals(exposedHeaders, that.exposedHeaders);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, origins, relativeOrigins, allowedMethods, allowedHeaders, exposedHeaders,
                maxAgeSeconds, allowCredentials);
    }
}
