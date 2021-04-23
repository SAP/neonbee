package io.neonbee.config;

import static io.neonbee.internal.helper.ConfigHelper.collectAdditionalConfig;
import static io.neonbee.internal.helper.ConfigHelper.rephraseConfigNames;
import static io.vertx.ext.web.handler.BasicAuthHandler.DEFAULT_REALM;
import static io.vertx.ext.web.handler.RedirectAuthHandler.DEFAULT_LOGIN_REDIRECT_URL;
import static io.vertx.ext.web.handler.RedirectAuthHandler.DEFAULT_RETURN_URL_PARAM;

import java.lang.invoke.MethodHandles;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableBiMap;
import com.google.errorprone.annotations.Immutable;

import io.neonbee.config.AuthProviderConfig.AuthProviderType;
import io.neonbee.internal.json.ImmutableJsonObject;
import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.htdigest.HtdigestAuth;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.oauth2.OAuth2Auth;
import io.vertx.ext.web.handler.AuthenticationHandler;
import io.vertx.ext.web.handler.BasicAuthHandler;
import io.vertx.ext.web.handler.DigestAuthHandler;
import io.vertx.ext.web.handler.JWTAuthHandler;
import io.vertx.ext.web.handler.OAuth2AuthHandler;
import io.vertx.ext.web.handler.RedirectAuthHandler;

@DataObject(generateConverter = true, publicConverter = false)
public class AuthHandlerConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private static final ImmutableBiMap<String, String> REPHRASE_MAP =
            ImmutableBiMap.<String, String>builder().put("authProviderConfig", "provider").build();

    private AuthHandlerType type;

    private AuthProviderConfig authProviderConfig;

    private JsonObject additionalConfig;

    /**
     * Private functional interface for {@link AuthHandlerType} enumeration.
     */
    @Immutable
    @FunctionalInterface
    private interface AuthHandlerFactory {
        /**
         * Create a Vert.x {@link AuthenticationHandler} based on the {@link AuthHandlerConfig}.
         *
         * @param vertx              the Vert.x instance to create the authentication handler for
         * @param authProviderConfig the auth. provider configuration to use
         * @param additionalConfig   any additional configuration for the authentication handler
         * @return the fully configured auth. handler
         */
        AuthenticationHandler createAuthHandler(Vertx vertx, AuthProviderConfig authProviderConfig,
                JsonObject additionalConfig);
    }

    /**
     * The type of the authentication handler supported by NeonBee.
     */
    @Immutable
    public enum AuthHandlerType implements AuthHandlerFactory {
        /**
         * HTTP Basic authentication.
         */
        BASIC((Vertx vertx, AuthProviderConfig authProviderConfig, JsonObject additionalConfig) -> BasicAuthHandler
                .create(authProviderConfig.createAuthProvider(vertx),
                        additionalConfig.getString("realm", DEFAULT_REALM))),
        /**
         * HTTP Basic authentication using .digest file format to perform authentication.
         */
        HTDIGEST((Vertx vertx, AuthProviderConfig authProviderConfig, JsonObject additionalConfig) -> {
            if (!AuthProviderType.HTDIGEST.equals(authProviderConfig.getType())) {
                throw new IllegalArgumentException(
                        "Cannot configure a digest authentication handler with any other authentication provider type");
            }
            return DigestAuthHandler.create(vertx, (HtdigestAuth) authProviderConfig.createAuthProvider(vertx));
        }),

        /**
         * JSON Web Token (JWT) authentication.
         */
        JWT((Vertx vertx, AuthProviderConfig authProviderConfig, JsonObject additionalConfig) -> {
            if (!AuthProviderType.JWT.equals(authProviderConfig.getType())) {
                throw new IllegalArgumentException(
                        "Cannot configure a JWT authentication handler with any other authentication provider type");
            }
            return JWTAuthHandler.create((JWTAuth) authProviderConfig.createAuthProvider(vertx));
        }),
        /**
         * OAuth2 authentication support. This handler is suitable for AuthCode flows.
         */
        OAUTH2((Vertx vertx, AuthProviderConfig authProviderConfig, JsonObject additionalConfig) -> {
            if (!AuthProviderType.OAUTH2.equals(authProviderConfig.getType())) {
                throw new IllegalArgumentException(
                        "Cannot configure a JWT authentication handler with any other authentication provider type");
            }
            return OAuth2AuthHandler.create(vertx, (OAuth2Auth) authProviderConfig.createAuthProvider(vertx),
                    additionalConfig.getString("callbackURL"));
        }),
        /**
         * Handle authentication by redirecting user to a custom login page.
         */
        REDIRECT((Vertx vertx, AuthProviderConfig authProviderConfig,
                JsonObject additionalConfig) -> RedirectAuthHandler.create(authProviderConfig.createAuthProvider(vertx),
                        additionalConfig.getString("loginRedirectURL", DEFAULT_LOGIN_REDIRECT_URL),
                        additionalConfig.getString("returnURLParam", DEFAULT_RETURN_URL_PARAM)));

        final AuthHandlerFactory factory;

        AuthHandlerType(AuthHandlerFactory factory) {
            this.factory = factory;
        }

        @Override
        public AuthenticationHandler createAuthHandler(Vertx vertx, AuthProviderConfig providerConfig,
                JsonObject additionalConfig) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Configuring auth. handler {} with options: {}", this, additionalConfig);
            }

            return factory.createAuthHandler(vertx, providerConfig,
                    Optional.ofNullable(additionalConfig).orElse(ImmutableJsonObject.EMPTY));
        }
    }

    /**
     * Creates an initial {@linkplain AuthHandlerConfig}.
     */
    public AuthHandlerConfig() {}

    /**
     * Creates a {@linkplain AuthHandlerConfig} parsing a given JSON object.
     *
     * @param json the JSON object to parse
     */
    public AuthHandlerConfig(JsonObject json) {
        this();

        JsonObject newJson = rephraseConfigNames(json.copy(), REPHRASE_MAP, true);
        AuthHandlerConfigConverter.fromJson(newJson, this);
        additionalConfig = Optional.ofNullable(additionalConfig).orElseGet(JsonObject::new)
                .mergeIn(collectAdditionalConfig(newJson, "type", "authProviderConfig"));
    }

    /**
     * Transforms this configuration object into JSON.
     *
     * @return a JSON representation of this configuration
     */
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        AuthHandlerConfigConverter.toJson(this, json);
        Optional.ofNullable(additionalConfig).ifPresent(config -> json.mergeIn(config));
        rephraseConfigNames(json, REPHRASE_MAP, false);
        return json;
    }

    /**
     * Returns the type of the authentication handler.
     *
     * @return the type as {@linkplain AuthHandlerType}
     */
    public AuthHandlerType getType() {
        return type;
    }

    /**
     * Sets the type of the authentication handler.
     *
     * @param type the type to set
     * @return the {@linkplain AuthHandlerConfig} for fluent use
     */
    @Fluent
    public AuthHandlerConfig setType(AuthHandlerType type) {
        this.type = type;
        return this;
    }

    /**
     * Returns the authentication provider configuration.
     *
     * @return the authentication provider configuration
     */
    public AuthProviderConfig getAuthProviderConfig() {
        return authProviderConfig;
    }

    /**
     * Sets the authentication provider configuration to be forwarded to the authentication provider when configuration
     * is done.
     *
     * @param authProviderConfig the authentication provider configuration to set
     * @return the {@linkplain AuthHandlerConfig} for fluent use
     */
    @Fluent
    public AuthHandlerConfig setAuthProviderConfig(AuthProviderConfig authProviderConfig) {
        this.authProviderConfig = authProviderConfig;
        return this;
    }

    /**
     * Returns additional configurations for this specific authentication handler type.
     *
     * @return additional configurations as JSON object
     */
    @GenIgnore
    public JsonObject getAdditionalConfig() {
        return additionalConfig;
    }

    /**
     * Sets additional configurations for this specific authentication handler type.
     *
     * @param additionalConfig the additional configuration to set
     * @return the {@linkplain AuthHandlerConfig} for fluent use
     */
    @Fluent
    @GenIgnore
    public AuthHandlerConfig setAdditionalConfig(JsonObject additionalConfig) {
        this.additionalConfig = additionalConfig;
        return this;
    }

    /**
     * Creates a new {@link AuthenticationHandler} based on this configuration, which is used to protect some or all
     * endpoints.
     *
     * @param vertx the Vert.x instance to use to create the authentication handler
     * @return the created {@link AuthenticationHandler}
     */
    public AuthenticationHandler createAuthHandler(Vertx vertx) {
        return type.createAuthHandler(vertx, authProviderConfig, additionalConfig);
    }
}
