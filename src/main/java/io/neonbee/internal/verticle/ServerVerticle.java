package io.neonbee.internal.verticle;

import static io.neonbee.internal.Helper.EMPTY;
import static io.vertx.core.http.HttpServerOptions.DEFAULT_MAX_HEADER_SIZE;
import static io.vertx.core.http.HttpServerOptions.DEFAULT_MAX_INITIAL_LINE_LENGTH;

import java.lang.invoke.MethodHandles;
import java.net.HttpURLConnection;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

import io.neonbee.NeonBee;
import io.neonbee.NeonBeeOptions;
import io.neonbee.internal.handler.CacheControlHandler;
import io.neonbee.internal.handler.CorrelationIdHandler;
import io.neonbee.internal.handler.ErrorHandler;
import io.neonbee.internal.handler.HooksHandler;
import io.neonbee.internal.handler.InstanceInfoHandler;
import io.neonbee.internal.handler.LoggerHandler;
import io.neonbee.internal.handler.NotFoundHandler;
import io.neonbee.internal.handler.ODataEndpointHandler;
import io.neonbee.internal.handler.ODataEndpointHandler.UriConversion;
import io.neonbee.internal.handler.RawDataEndpointHandler;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.PubSecKeyOptions;
import io.vertx.ext.auth.authentication.AuthenticationProvider;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.AuthenticationHandler;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.ChainAuthHandler;
import io.vertx.ext.web.handler.JWTAuthHandler;
import io.vertx.ext.web.handler.SessionHandler;
import io.vertx.ext.web.handler.TimeoutHandler;
import io.vertx.ext.web.sstore.ClusteredSessionStore;
import io.vertx.ext.web.sstore.LocalSessionStore;
import io.vertx.micrometer.PrometheusScrapingHandler;

/**
 * The ServerVerticle is the heart of all web based end points of NeonBee.
 *
 * ServerVerticle is able to handle a config() JSON object:
 * <p>
 * <code>
 * {
 *   port: number, // the port number to use for the HTTP server, defaults to 8080
 *   useAlpn: boolean, // sets whether to use application-layer protocol negotiation or not, defaults to true
 *   sessionHandling: string, // one of: none, local or enabled, defaults to none
 *   sessionCookieName: string, // the name of the session cookie, defaults to neonbee-web.session
 *   decompressionSupported: boolean, // sets whether the server should decompress request bodies, defaults to true
 *   compressionSupported: boolean, // sets whether the server supports compression, defaults to true
 *   compressionLevel: number, // if compression is supported, sets the level of compression, defaults to 1
 *      (note that according to https://vertx.io/docs/vertx-core/java/#_http_compression compression levels higher
 *      than 1-2 will result in just some bytes worth of saved bandwidth, so go with the minimum of 1 as a default)
 *   correlationStrategy: string, // one of: request_header, generate_uuid, defaults to request_header
 *   timeout: number, // the number of seconds before the router timeout applies, defaults to 30
 *   timeoutErrorCode: number, // the error code for the default timeout, defaults to 504
 *   maxHeaderSize: 8192 // the maximum length of all HTTP headers, defaults to 8192 bytes
 *   maxInitialLineLength: 4096 // the maximum initial line length of the HTTP header (e.g. "GET / HTTP/1.0"), defaults to 4096 bytes
 *   endpoints: { // specific endpoint configuration, defaults to the object seen below
 *     odata: { // provides a OData V4 compliant endpoint, for accessing entity verticle data
 *       enabled: boolean, // enable the OData endpoint, defaults to true
 *       basePath: string, // the base path to map this endpoint to, defaults to /odata/
 *       uriConversion: string // namespace and service name URI mapping (strict, or loose based on CDS)
 *     },
 *     raw: { // provides a REST endpoint (JSON, text, binary), for accessing data verticle
 *       enabled: boolean, // enable the raw endpoint, defaults to true
 *       basePath: string // the base path to map this endpoint to, defaults to /raw/
 *       exposeHiddenVerticles: false // whether or not to expose hidden verticle, defaults to false
 *     },
 *     metrics: { // provides an Prometheus scraping endpoint for Micrometer.io metrics
 *       enabled: boolean, // enable the metrics endpoint, defaults to true
 *       basePath: string // the base path to map this endpoint to, defaults to /metrics/
 *     }
 *   },
 *   authenticationChain: [ // authentication chain, defaults to an empty array (no authentication), use any of:
 *      {
 *        type: string, // any of: basic, digest, jwt, oauth2, redirect, mandatory attribute
 *        // ... more authentication handler options
 *        provider: {
 *           type: string, // any of: htdigest, htpasswd, jdbc, jwt, mongo, oauth2, shiro, mandatory attribute
 *           // ... more authentication provider options
 *        }
 *      }
 *   ]
 * }
 * </code>
 */
@SuppressWarnings("checkstyle:JavadocVariable")
public class ServerVerticle extends AbstractVerticle {
    public static final String DEFAULT_ODATA_BASE_PATH = "/odata/";

    public static final String DEFAULT_RAW_BASE_PATH = "/raw/";

    public static final String DEFAULT_METRICS_BASE_PATH = "/metrics/";

    public static final int DEFAULT_ROUTER_TIMEOUT = 30;

    public static final int DEFAULT_PORT = 8080;

    public static final String CONFIG_PROPERTY_PORT_KEY = "port";

    protected static final String DEFAULT_SESSION_COOKIE_NAME = "neonbee-web.session";

    protected static final String CONFIG_PROPERTY_SESSION_HANDLING = "sessionHandling";

    protected static final String CONFIG_PROPERTY_SESSION_HANDLING_CLUSTERED = "clustered";

    protected static final String CONFIG_PROPERTY_SESSION_HANDLING_LOCAL = "local";

    protected static final String CONFIG_PROPERTY_SESSION_HANDLING_NONE = "none";

    @VisibleForTesting
    static final String BEGIN_PUBLIC_KEY = "-----BEGIN PUBLIC KEY-----";

    @VisibleForTesting
    static final String END_PUBLIC_KEY = "-----END PUBLIC KEY-----";

    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private HttpServer httpServer;

    @Override
    public void start(Promise<Void> startPromise) {
        Router router = Router.router(vertx);
        router.route().failureHandler(ErrorHandler.create(/* use default error template */));
        router.route().handler(LoggerHandler.create());
        router.route().handler(BodyHandler.create(false /* do not handle file uploads */));

        JsonObject config = config();
        router.route().handler(CorrelationIdHandler.create(CorrelationIdHandler.Strategy
                .valueOf(config.getString("correlationStrategy", "request_header").toUpperCase(Locale.ENGLISH))));
        long timeoutMillis = TimeUnit.SECONDS.toMillis(config.getInteger("timeout", DEFAULT_ROUTER_TIMEOUT));
        router.route().handler(TimeoutHandler.create(timeoutMillis,
                config.getInteger("timeoutErrorCode", HttpURLConnection.HTTP_GATEWAY_TIMEOUT)));
        router.route().handler(CacheControlHandler.create());
        router.route().handler(InstanceInfoHandler.create());

        // session / cookie handling
        String sessionHandling = determineSessionHandling(vertx, config);
        switch (sessionHandling) {
        case CONFIG_PROPERTY_SESSION_HANDLING_LOCAL:
            // With this store, sessions are stored locally in memory in a shared local map and only available in
            // this instance.
            router.route().handler(SessionHandler.create(LocalSessionStore.create(vertx))
                    .setSessionCookieName(config.getString("sessionCookieName", DEFAULT_SESSION_COOKIE_NAME)));
            break;
        case CONFIG_PROPERTY_SESSION_HANDLING_CLUSTERED:
            // With this store, sessions are stored in a distributed map which is accessible across the Vert.x
            // cluster.
            router.route().handler(SessionHandler.create(ClusteredSessionStore.create(vertx))
                    .setSessionCookieName(config.getString("sessionCookieName", DEFAULT_SESSION_COOKIE_NAME)));
            break;
        default: // case CONFIG_PROPERTY_SESSION_HANDLING_NONE:
            /* nothing to do here, no session handling, so neither add a cookie, nor a session handler */
        }

        // authentication handling
        List<AuthenticationHandler> authHandlers = config.getJsonArray("authenticationChain", new JsonArray()).stream()
                .map(JsonObject.class::cast).map(this::createAuthHandler).collect(Collectors.toList());
        if (!authHandlers.isEmpty()) {
            ChainAuthHandler authenticationHandler =
                    authHandlers.stream().reduce(ChainAuthHandler.any(), ChainAuthHandler::add, ChainAuthHandler::add);

            router.route().handler(authenticationHandler);
        }

        // Execute hook of type ONCE_PER_REQUEST here
        router.route().handler(HooksHandler.create());

        // add any endpoint handlers as sub-routes here
        JsonObject endpointsConfig = config.getJsonObject("endpoints", new JsonObject());
        addEndpointRouter(router, DEFAULT_ODATA_BASE_PATH, EMPTY,
                endpointsConfig.getJsonObject("odata", new JsonObject()),
                (basePath, endpointConfig) -> ODataEndpointHandler.router(vertx, basePath,
                        UriConversion.byName(endpointConfig.getString("uriConversion", "strict"))));
        addEndpointHandler(router, DEFAULT_RAW_BASE_PATH, EMPTY, endpointsConfig.getJsonObject("raw", new JsonObject()),
                (basePath, endpointConfig) -> RawDataEndpointHandler.create(endpointConfig));
        addEndpointHandler(router, DEFAULT_METRICS_BASE_PATH, EMPTY,
                endpointsConfig.getJsonObject("metrics", new JsonObject()), (basePath,
                        endpointConfig) -> PrometheusScrapingHandler.create(endpointConfig.getString("registryName")));
        router.route().handler(NotFoundHandler.create());

        NeonBeeOptions options = NeonBee.instance(vertx).getOptions();
        int port = Optional.ofNullable(options.getServerVerticlePort())
                .orElse(config.getInteger(CONFIG_PROPERTY_PORT_KEY, DEFAULT_PORT));

        httpServer = vertx.createHttpServer(new HttpServerOptions()
                .setDecompressionSupported(config.getBoolean("decompressionSupported", true))
                .setCompressionSupported(config.getBoolean("compressionSupported", true))
                .setCompressionLevel(config.getInteger("compressionLevel", 1))
                .setMaxHeaderSize(config.getInteger("maxHeaderSize", DEFAULT_MAX_HEADER_SIZE))
                .setMaxInitialLineLength(config.getInteger("maxInitialLineLength", DEFAULT_MAX_INITIAL_LINE_LENGTH))
                .setUseAlpn(config.getBoolean("useAlpn", true))).exceptionHandler(throwable -> {
                    LOGGER.error("HTTP Socket Exception", throwable);
                }).requestHandler(router).listen(port, asyncResult -> {
                    if (asyncResult.succeeded()) {
                        LOGGER.info("HTTP server started on port {}", port);
                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug("HTTP server configured with routes: {}",
                                    router.getRoutes().stream().map(Route::toString).collect(Collectors.joining(",")));
                        }
                        startPromise.complete();
                    } else {
                        LOGGER.error("HTTP server could not be started on port {}", port, asyncResult.cause()); // NOPMD
                        startPromise.fail(asyncResult.cause());
                    }
                });
    }

    @Override
    public void stop(Promise<Void> stopPromise) throws Exception {
        if (Objects.isNull(httpServer)) {
            stopPromise.complete();
        } else {
            httpServer.close(stopPromise);
        }
    }

    private void addEndpointRouter(Router router, String defaultBasePath, String basePathSuffix, JsonObject config,
            BiFunction<String, JsonObject, Router> endpoint) {
        if (config.getBoolean("enabled", true)) {
            String basePath = config.getString("basePath", defaultBasePath) + basePathSuffix;
            router.mountSubRouter(basePath, endpoint.apply(basePath, config));
        }
    }

    private void addEndpointHandler(Router router, String defaultBasePath, String basePathSuffix, JsonObject config,
            BiFunction<String, JsonObject, Handler<RoutingContext>> endpoint) {
        addEndpointRouter(router, defaultBasePath, basePathSuffix, config, (basePath, endpointConfig) -> {
            Router subRouter = Router.router(vertx);
            subRouter.route().handler(endpoint.apply(basePath, endpointConfig));
            return subRouter;
        });
    }

    /**
     * Creates a new AuthHanlder based on the passed configuration, which is used to protect all NeonBee web end points.
     *
     * @param config the configuration for the AuthHandler
     * @return the created AuthHanlder
     */
    protected AuthenticationHandler createAuthHandler(JsonObject config) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Configuring auth. handler with config: {}", config.copy().put("provider", (String) null));
        }

        JsonObject provider = config.getJsonObject("provider", new JsonObject());
        switch (config.getString("type", EMPTY)) {
        case "basic":
            // TODO return BasicAuthHandler.create(authProvider, realm);
            throw new UnsupportedOperationException("Basic authentication handler is not implemented yet");
        case "digest":
            // TODO return DigestAuthHandler.create(authProvider, nonceExpireTimeout);
            throw new UnsupportedOperationException("Digest authentication handler is not implemented yet");
        case "jwt":
            if (!"jwt".equals(provider.getString("type"))) {
                throw new IllegalArgumentException(
                        "Cannot configure a JWT authentication handler with any other authentication provider type");
            }
            return JWTAuthHandler.create((JWTAuth) createAuthProvider(provider));
        case "oauth2":
            // TODO return OAuth2AuthHandler.create(authProvider, callbackURL);
            throw new UnsupportedOperationException("OAuth2 authentication handler is not implemented yet");
        case "redirect":
            // TODO return RedirectAuthHandler.create(authProvider, loginRedirectURL, returnURLParam);
            throw new UnsupportedOperationException("Redirect authentication handler is not implemented yet");
        default:
            throw new IllegalArgumentException("Unknown authentication handler type in server configuration");
        }
    }

    private AuthenticationProvider createAuthProvider(JsonObject options) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Configuring auth. provider with options: {}", options);
        }

        switch (options.getString("type", EMPTY)) {
        case "htdigest":
            // TODO return HtdigestAuth.create(vertx, htfile);
            throw new UnsupportedOperationException("HtDigest authentication provider is not implemented yet");
        case "htpasswd":
            // TODO return HtpasswdAuth.create(vertx, new HtpasswdAuthOptions(jsonConfig));
            throw new UnsupportedOperationException("HtPasswd authentication provider is not implemented yet");
        case "jdbc":
            // TODO return JDBCAuth.create(vertx, client).setAllSettersHere;
            throw new UnsupportedOperationException("JDBC authentication provider is not implemented yet");
        case "jwt":
            JsonArray pubSecKeys = options.getJsonArray("pubSecKeys");
            return JWTAuth.create(vertx, new JWTAuthOptions().setPubSecKeys(extractPubSecKeys(pubSecKeys)));
        case "mongo":
            // TODO return MongoAuth.create(mongoClient, jsonConfig);
            throw new UnsupportedOperationException("MongoDB authentication provider is not implemented yet");
        case "oauth2":
            // TODO return OAuth2Auth.create(vertx, new OAuth2ClientOptions(jsonConfig));
            throw new UnsupportedOperationException("OAuth 2.0 authentication provider is not implemented yet");
        case "shiro":
            // TODO return ShiroAuth.create(vertx, new ShiroAuthOptions(jsonConfig));
            throw new UnsupportedOperationException("Shiro authentication provider is not implemented yet");
        default:
            throw new IllegalArgumentException("Unknown authentication provider type in server configuration");
        }
    }

    @VisibleForTesting
    static List<PubSecKeyOptions> extractPubSecKeys(JsonArray keys) {
        return keys.stream().map(JsonObject.class::cast).map(pubKeyJson -> {
            String algorithm = pubKeyJson.getString("algorithm");
            String pubKey = pubKeyJson.getString("publicKey");

            return new PubSecKeyOptions().setAlgorithm(algorithm).setBuffer(convertToPEM(pubKey));
        }).collect(Collectors.toList());
    }

    /**
     * Converts a given public key into a PEM format.
     *
     * @param publicKey The public key
     * @return A String representing a Vert.x compatible PEM format
     */
    private static String convertToPEM(String publicKey) {
        return new StringBuilder(BEGIN_PUBLIC_KEY).append('\n').append(publicKey).append('\n').append(END_PUBLIC_KEY)
                .toString();
    }

    /**
     * Determine the session handling (none/local/clustered).
     *
     * @param config the verticle's config
     * @return the session handling config value (none/local/clustered). In case the session handling is set to
     *         clustered, but NeonBee does not run in clustered mode, fallback to the local session handling.
     */
    @VisibleForTesting
    static String determineSessionHandling(Vertx vertx, JsonObject config) {
        String sessionHandling =
                config.getString(CONFIG_PROPERTY_SESSION_HANDLING, CONFIG_PROPERTY_SESSION_HANDLING_NONE);
        if (!Arrays.asList(CONFIG_PROPERTY_SESSION_HANDLING_NONE, CONFIG_PROPERTY_SESSION_HANDLING_LOCAL,
                CONFIG_PROPERTY_SESSION_HANDLING_CLUSTERED).contains(sessionHandling)) {
            sessionHandling = CONFIG_PROPERTY_SESSION_HANDLING_NONE;
        } else if (CONFIG_PROPERTY_SESSION_HANDLING_CLUSTERED.equals(sessionHandling) && !vertx.isClustered()) {
            sessionHandling = CONFIG_PROPERTY_SESSION_HANDLING_LOCAL;
        }
        return sessionHandling;
    }
}
