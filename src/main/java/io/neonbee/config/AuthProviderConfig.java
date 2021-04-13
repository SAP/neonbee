package io.neonbee.config;

import static io.vertx.ext.auth.htdigest.HtdigestAuth.HTDIGEST_FILE;

import java.lang.invoke.MethodHandles;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.neonbee.internal.json.ImmutableJsonObject;
import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.annotations.Fluent;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.authentication.AuthenticationProvider;
import io.vertx.ext.auth.htdigest.HtdigestAuth;
import io.vertx.ext.auth.htpasswd.HtpasswdAuth;
import io.vertx.ext.auth.htpasswd.HtpasswdAuthOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.auth.oauth2.OAuth2Auth;
import io.vertx.ext.auth.oauth2.OAuth2Options;

@DataObject(generateConverter = true, publicConverter = false)
public class AuthProviderConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    /**
     * Private functional interface for {@link AuthProviderType} enumeration.
     */
    @FunctionalInterface
    private static interface AuthProviderFactory {
        /**
         * Create a Vert.x {@link AuthenticationProvider} based on the {@link AuthProviderConfig}.
         *
         * @param vertx            the Vert.x instance to create the authentication provider for
         * @param additionalConfig any additional configuration for the authentication provider
         * @return the fully configured auth. provider
         */
        AuthenticationProvider createAuthProvider(Vertx vertx, JsonObject additionalConfig);
    }

    /**
     * The type of the authentication provider supported by NeonBee.
     */
    public enum AuthProviderType implements AuthProviderFactory {
        /**
         * An authentication provider using a .htdigest file as store.
         */
        HTDIGEST((vertx, additionalConfig) -> HtdigestAuth.create(vertx,
                additionalConfig.getString("htfile", HTDIGEST_FILE))),
        /**
         * An authentication provider using a .htpasswd file as store.
         */
        HTPASSWD((vertx, additionalConfig) -> HtpasswdAuth.create(vertx, new HtpasswdAuthOptions(additionalConfig))),
        /**
         * An authentication provider consuming user / password information from a Vert.x JDBC client.
         */
        JDBC((vertx, additionalConfig) -> {
            throw new UnsupportedOperationException("JDBC authentication provider is not implemented yet");
        }),
        /**
         * JSON Web Token (JWT)-based authentication provider.
         */
        JWT((vertx, additionalConfig) -> JWTAuth.create(vertx, new JWTAuthOptions(additionalConfig))),
        /**
         * An authentication provider consuming user / password information from a MongoDB instance.
         */
        MONGO((vertx, additionalConfig) -> {
            throw new UnsupportedOperationException("MongoDB authentication provider is not implemented yet");
        }),
        /**
         * OAuth2 based authentication provider.
         */
        OAUTH2((vertx, additionalConfig) -> OAuth2Auth.create(vertx, new OAuth2Options(additionalConfig)));

        final AuthProviderFactory factory;

        AuthProviderType(AuthProviderFactory factory) {
            this.factory = factory;
        }

        @Override
        public AuthenticationProvider createAuthProvider(Vertx vertx, JsonObject additionalConfig) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Configuring auth. provider {} with options: {}", this, additionalConfig);
            }

            return factory.createAuthProvider(vertx,
                    Optional.ofNullable(additionalConfig).orElse(ImmutableJsonObject.EMPTY));
        }
    }

    private AuthProviderType type;

    private JsonObject additionalConfig;

    /**
     * Creates an initial {@linkplain AuthProviderConfig}
     */
    public AuthProviderConfig() {}

    /**
     * Creates a {@linkplain AuthProviderConfig} parsing a given JSON object
     *
     * @param json the JSON object to parse
     */
    public AuthProviderConfig(JsonObject json) {
        this();

        AuthProviderConfigConverter.fromJson(json, this);
    }

    /**
     * Transforms this configuration object into JSON
     *
     * @return a JSON representation of this configuration
     */
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        AuthProviderConfigConverter.toJson(this, json);
        return json;
    }

    /**
     * Returns the type of the authentication provider
     *
     * @return the type as {@linkplain AuthProviderType}
     */
    public AuthProviderType getType() {
        return type;
    }

    /**
     * Sets the type of the authentication provider
     *
     * @param type the type to set
     * @return the {@linkplain AuthProviderConfig} for fluent use
     */
    @Fluent
    public AuthProviderConfig setType(AuthProviderType type) {
        this.type = type;
        return this;
    }

    /**
     * Returns additional configurations for this specific authentication provider type
     *
     * @return additional configurations as JSON object
     */
    public JsonObject getAdditionalConfig() {
        return additionalConfig;
    }

    /**
     * Sets additional configurations for this specific authentication provider type
     *
     * @param additionalConfig the additional configuration to set
     * @return the {@linkplain AuthProviderConfig} for fluent use
     */
    @Fluent
    public AuthProviderConfig setAdditionalConfig(JsonObject additionalConfig) {
        this.additionalConfig = additionalConfig;
        return this;
    }

    public AuthenticationProvider createAuthProvider(Vertx vertx) {
        return type.createAuthProvider(vertx, additionalConfig);
    }
}
