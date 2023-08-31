package io.neonbee.config;

import static io.neonbee.internal.helper.ConfigHelper.rephraseConfigNames;
import static io.netty.handler.codec.http.HttpResponseStatus.GATEWAY_TIMEOUT;
import static io.vertx.ext.web.handler.SessionHandler.DEFAULT_COOKIE_HTTP_ONLY_FLAG;
import static io.vertx.ext.web.handler.SessionHandler.DEFAULT_COOKIE_SECURE_FLAG;
import static io.vertx.ext.web.handler.SessionHandler.DEFAULT_SESSION_COOKIE_PATH;
import static io.vertx.ext.web.handler.SessionHandler.DEFAULT_SESSION_TIMEOUT;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableBiMap;

import io.neonbee.endpoint.health.HealthEndpoint;
import io.neonbee.endpoint.health.StatusEndpoint;
import io.neonbee.endpoint.metrics.MetricsEndpoint;
import io.neonbee.endpoint.odatav4.ODataV4Endpoint;
import io.neonbee.endpoint.raw.RawEndpoint;
import io.neonbee.internal.handler.DefaultErrorHandler;
import io.neonbee.internal.handler.factories.CacheControlHandlerFactory;
import io.neonbee.internal.handler.factories.CorrelationIdHandlerFactory;
import io.neonbee.internal.handler.factories.CorsHandlerFactory;
import io.neonbee.internal.handler.factories.DisallowingFileUploadBodyHandlerFactory;
import io.neonbee.internal.handler.factories.InstanceInfoHandlerFactory;
import io.neonbee.internal.handler.factories.LoggerHandlerFactory;
import io.neonbee.internal.handler.factories.RoutingHandlerFactory;
import io.neonbee.internal.handler.factories.SessionHandlerFactory;
import io.neonbee.internal.handler.factories.TimeoutHandlerFactory;
import io.neonbee.internal.json.ImmutableJsonObject;
import io.neonbee.internal.verticle.ServerVerticle;
import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.ClientAuth;
import io.vertx.core.http.CookieSameSite;
import io.vertx.core.http.Http2Settings;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.JdkSSLEngineOptions;
import io.vertx.core.net.JksOptions;
import io.vertx.core.net.KeyCertOptions;
import io.vertx.core.net.OpenSSLEngineOptions;
import io.vertx.core.net.PemKeyCertOptions;
import io.vertx.core.net.PemTrustOptions;
import io.vertx.core.net.PfxOptions;
import io.vertx.core.net.SSLEngineOptions;
import io.vertx.core.net.TrustOptions;
import io.vertx.core.tracing.TracingPolicy;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.ErrorHandler;

/**
 * Handles the configuration for the {@linkplain ServerVerticle}, extending the {@linkplain HttpServerOptions} providing
 * some different defaults and deals with JSON objects of the following structure:
 * <p>
 * <code>
 * {
 *   port: number, // the port number to use for the HTTP server, defaults to 8080
 *   useAlpn: boolean, // sets whether to use application-layer protocol negotiation or not, defaults to true
 *   compressionSupported: boolean, // sets whether the server supports compression, defaults to true
 *   compressionLevel: number, // if compression is supported, sets the level of compression, defaults to 1
 *      (note that according to https://vertx.io/docs/vertx-core/java/#_http_compression compression levels higher
 *      than 1-2 will result in just some bytes worth of saved bandwidth, so go with the minimum of 1 as a default)
 *   decompressionSupported: boolean, // sets whether the server should decompress request bodies, defaults to true
 *   // ... any other io.vertx.core.http.HttpServerOptions with the given defaults of the class
 *   timeout: number, // the number of seconds before the router timeout applies, defaults to 30
 *   timeoutStatusCode: number, // the status code for the default timeout, defaults to 504
 *   sessionHandling: string, // one of: none, local or clustered, defaults to none
 *   sessionTimeout: integer, // the session timeout in minutes, defaults to 30
 *   sessionCookieName: string, // the name of the session cookie, defaults to neonbee-web.session
 *   sessionCookiePath: string, // the path of the session cookie, defaults to /
 *   secureSessionCookie: boolean, // sets whether to set the `secure` flag of the session cookie, defaults to false
 *   httpOnlySessionCookie: boolean, // sets whether to set the `HttpOnly` flag of the session cookie, defaults to false
 *   sessionCookieSameSitePolicy: string, // one of: null, none, strict, or lax, defaults to null
 *   minSessionIdLength: integer, // the minimum length of the session id, defaults to 32
 *   correlationStrategy: string, // one of: request_header, generate_uuid, defaults to request_header
 *   endpoints: [ // endpoint configurations, defaults to the objects seen below
 *     {
 *       type: "io.neonbee.endpoint.odatav4.ODataV4Endpoint", // provide a OData V4 compliant endpoint, for accessing entity verticles
 *       enabled: boolean, // enable the OData endpoint, defaults to true
 *       basePath: string, // the base path to map this endpoint to, defaults to /odata/
 *       authenticationChain: array, // a specific authentication chain for this endpoint, defaults to the general auth. chain
 *       uriConversion: string // namespace and service name URI mapping (strict, or loose based on CDS)
 *     },
 *     {
 *       type: "io.neonbee.endpoint.raw.RawEndpoint", // provides a REST endpoint (JSON, text, binary), for accessing data verticle
 *       enabled: boolean, // enable the raw endpoint, defaults to true
 *       basePath: string // the base path to map this endpoint to, defaults to /raw/
 *       authenticationChain: array, // a specific authentication chain for this endpoint, defaults to the general auth. chain
 *       exposeHiddenVerticles: false // whether or not to expose hidden verticle, defaults to false
 *     },
 *     metrics: {
 *       type: "io.neonbee.endpoint.metrics.MetricsEndpoint", // provides an Prometheus scraping endpoint for Micrometer.io metrics
 *       enabled: boolean, // enable the metrics endpoint, defaults to true
 *       basePath: string // the base path to map this endpoint to, defaults to /metrics/
 *       authenticationChain: array, // a specific authentication chain for this endpoint, defaults to an empty array / no auth.
 *     }
 *     health: {
 *       type: "io.neonbee.endpoint.health.HealthEndpoint", // provides an endpoint with verbose health information
 *       enabled: boolean, // enable the health endpoint, defaults to true
 *       basePath: string // the base path to map this endpoint to, defaults to /health/
 *       authenticationChain: array, // a specific authentication chain for this endpoint, defaults to an empty array / no auth.
 *     }
 *     status: {
 *       type: "io.neonbee.endpoint.health.StatusEndpoint", // provides an endpoint with non-verbose health information
 *       enabled: boolean, // enable the status endpoint, defaults to true
 *       basePath: string // the base path to map this endpoint to, defaults to /status/
 *       authenticationChain: array, // a specific authentication chain for this endpoint, defaults to an empty array / no auth.
 *     }
 *   ],
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
@DataObject(generateConverter = true, publicConverter = false)
@SuppressWarnings({ "PMD.ExcessivePublicCount", "PMD.CyclomaticComplexity", "PMD.TooManyMethods", "PMD.GodClass" })
public class ServerConfig extends HttpServerOptions {

    public enum SessionHandling {
        /**
         * No session handling.
         */
        NONE,
        /**
         * Local session handling or in clustered operation on each cluster node.
         *
         * Sessions are stored locally in a shared local map and only available on this instance.
         */
        LOCAL,
        /**
         * Clustered session handling in a shared map across the whole cluster.
         */
        CLUSTERED
    }

    public enum CorrelationStrategy {
        /**
         * Generates a random UUID for every incoming request.
         */
        GENERATE_UUID(routingContext -> UUID.randomUUID().toString()),
        /**
         * Tries to get the correlation id from the request header, otherwise generates a random UUID.
         */
        REQUEST_HEADER(routingContext -> {
            HttpServerRequest request = routingContext.request();
            String correlationId = request.getHeader("X-CorrelationID");
            if (Strings.isNullOrEmpty(correlationId)) {
                correlationId = request.getHeader("x-vcap-request-id"); // used on CloudFoundry
            }
            if (Strings.isNullOrEmpty(correlationId)) {
                correlationId = GENERATE_UUID.getCorrelationId(routingContext);
            }
            return correlationId;
        });

        @SuppressWarnings("ImmutableEnumChecker")
        final Function<RoutingContext, String> mapper;

        CorrelationStrategy(Function<RoutingContext, String> mapper) {
            this.mapper = mapper;
        }

        /**
         * Returns the correlation ID based on the selected strategy.
         *
         * @param routingContext the {@link RoutingContext} to determine the correlation ID for
         * @return the correlation ID, depending on the strategy related to this routing context
         */
        public String getCorrelationId(RoutingContext routingContext) {
            return mapper.apply(routingContext);
        }
    }

    /**
     * The default port of NeonBee.
     */
    public static final int DEFAULT_PORT = 8080;

    /**
     * By default NeonBee is configured to use ALPN (HTTP/2).
     */
    public static final boolean DEFAULT_USE_ALPN = true;

    /**
     * By default NeonBees server verticle is using compression support.
     */
    public static final boolean DEFAULT_COMPRESSION_SUPPORTED = true;

    /**
     * The default compression level is 1, which means minimal compression, but less resource consumption.
     */
    public static final int DEFAULT_COMPRESSION_LEVEL = 1;

    /**
     * By default NeonBees server verticle is using decompression support.
     */
    public static final boolean DEFAULT_DECOMPRESSION_SUPPORTED = true;

    /**
     * The default timeout for web requests to the {@link ServerVerticle} is 30 seconds.
     */
    public static final int DEFAULT_TIMEOUT = 30;

    /**
     * The default name to store the session information in a cookie.
     */
    public static final String DEFAULT_SESSION_COOKIE_NAME = "neonbee-web.session";

    /**
     * The default minimum length of the session ID.
     */
    public static final int DEFAULT_SESSIONID_MIN_LENGTH = 32;

    /**
     * List of instances of {@link RoutingHandlerFactory} that are loaded by default.
     */
    public static final List<String> DEFAULT_HANDLER_FACTORIES_CLASS_NAMES =
            List.of(LoggerHandlerFactory.class.getName(), InstanceInfoHandlerFactory.class.getName(),
                    CorrelationIdHandlerFactory.class.getName(), TimeoutHandlerFactory.class.getName(),
                    SessionHandlerFactory.class.getName(), CacheControlHandlerFactory.class.getName(),
                    CorsHandlerFactory.class.getName(), DisallowingFileUploadBodyHandlerFactory.class.getName());

    private static final String PROPERTY_PORT = "port";

    private static final List<EndpointConfig> DEFAULT_ENDPOINT_CONFIGS = Collections.unmodifiableList(Stream
            .of(RawEndpoint.class, ODataV4Endpoint.class, MetricsEndpoint.class, HealthEndpoint.class,
                    StatusEndpoint.class)
            .map(endpointClass -> new EndpointConfig().setType(endpointClass.getName())).collect(Collectors.toList()));

    private static final ImmutableBiMap<String, String> REPHRASE_MAP = ImmutableBiMap.of("endpointConfigs", "endpoints",
            "authChainConfig", "authenticationChain", "errorHandlerClassName", "errorHandler", "errorHandlerTemplate",
            "errorTemplate", "handlerFactoriesClassNames", "handlerFactories", "corsConfig", "cors");

    private int timeout = DEFAULT_TIMEOUT;

    private int timeoutStatusCode = GATEWAY_TIMEOUT.code();

    private SessionHandling sessionHandling = SessionHandling.NONE;

    private int sessionTimeout = (int) TimeUnit.MICROSECONDS.toSeconds(DEFAULT_SESSION_TIMEOUT);

    private String sessionCookieName = DEFAULT_SESSION_COOKIE_NAME;

    private String sessionCookiePath = DEFAULT_SESSION_COOKIE_PATH;

    private boolean secureSessionCookie = DEFAULT_COOKIE_SECURE_FLAG;

    private boolean httpOnlySessionCookie = DEFAULT_COOKIE_HTTP_ONLY_FLAG;

    private CookieSameSite sessionCookieSameSitePolicy;

    private int minSessionIdLength = DEFAULT_SESSIONID_MIN_LENGTH;

    private CorrelationStrategy correlationStrategy = CorrelationStrategy.REQUEST_HEADER;

    private List<EndpointConfig> endpointConfigs = new ArrayList<>(DEFAULT_ENDPOINT_CONFIGS);

    private List<AuthHandlerConfig> authChainConfig;

    private List<String> handlerFactoriesClassNames = DEFAULT_HANDLER_FACTORIES_CLASS_NAMES;

    private String errorHandlerClassName;

    private String errorHandlerTemplate;

    private CorsConfig corsConfig = new CorsConfig();

    /**
     * Create a default server configuration.
     */
    public ServerConfig() {
        super();
        overrideDefaults(ImmutableJsonObject.EMPTY);
    }

    /**
     * Create a server configuration based on a given JSON object.
     *
     * @param json the JSON object to parse
     */
    public ServerConfig(JsonObject json) {
        super(json);
        JsonObject newJson = rephraseConfigNames(json.copy(), REPHRASE_MAP, true);
        overrideDefaults(newJson);
        ServerConfigConverter.fromJson(newJson, this);
    }

    private void overrideDefaults(JsonObject json) {
        // we override some settings like the default port, use alpn and compression settings
        setPort(json.getInteger(PROPERTY_PORT, DEFAULT_PORT));
        setUseAlpn(json.getBoolean("useAlpn", DEFAULT_USE_ALPN));
        setCompressionSupported(json.getBoolean("compressionSupported", DEFAULT_COMPRESSION_SUPPORTED));
        setCompressionLevel(json.getInteger("compressionLevel", DEFAULT_COMPRESSION_LEVEL));
        setDecompressionSupported(json.getBoolean("decompressionSupported", DEFAULT_DECOMPRESSION_SUPPORTED));
    }

    @Override
    public JsonObject toJson() {
        JsonObject json = super.toJson();
        ServerConfigConverter.toJson(this, json);
        rephraseConfigNames(json, REPHRASE_MAP, false);
        return json;
    }

    /**
     * Get the CORS configuration of the server verticle.
     *
     * @return the CORS configuration
     */
    public CorsConfig getCorsConfig() {
        return corsConfig;
    }

    /**
     * Set the CORS configuration of the server verticle.
     *
     * @param corsConfig the CORS configuration
     * @return the {@link ServerConfig} for chaining
     */
    @Fluent
    public ServerConfig setCorsConfig(CorsConfig corsConfig) {
        this.corsConfig = corsConfig;
        return this;
    }

    /**
     * Get the timeout of the server verticle in seconds.
     *
     * @return the timeout in seconds
     */
    public int getTimeout() {
        return timeout;
    }

    /**
     * Set the timeout of the server verticle in seconds.
     *
     * @param timeout the timeout in seconds
     * @return the {@link ServerConfig} for chaining
     */
    @Fluent
    public ServerConfig setTimeout(int timeout) {
        this.timeout = timeout;
        return this;
    }

    /**
     * Get the session handling strategy configured.
     *
     * @return the currently set session handling strategy
     */
    public SessionHandling getSessionHandling() {
        return sessionHandling;
    }

    /**
     * Set the session handling strategy to use by the {@link ServerVerticle}.
     *
     * @param sessionHandling the session handling strategy
     * @return the {@link ServerConfig} for chaining
     */
    @Fluent
    public ServerConfig setSessionHandling(SessionHandling sessionHandling) {
        this.sessionHandling = sessionHandling;
        return this;
    }

    /**
     * Get the session timeout in minutes.
     *
     * @return the session timeout in minutes
     */
    public int getSessionTimeout() {
        return sessionTimeout;
    }

    /**
     * Set the session timeout in minutes.
     *
     * @param sessionTimeout the session timeout in minutes
     * @return the {@link ServerConfig} for chaining
     */
    @Fluent
    public ServerConfig setSessionTimeout(int sessionTimeout) {
        this.sessionTimeout = sessionTimeout;
        return this;
    }

    /**
     * Get the cookie name to use to store the session identifier.
     *
     * @return the cookie name in use
     */
    public String getSessionCookieName() {
        return sessionCookieName;
    }

    /**
     * Set the cookie name to use for storing the session identifier.
     *
     * @param sessionCookieName the cookie name to use
     * @return the {@link ServerConfig} for chaining
     */
    @Fluent
    public ServerConfig setSessionCookieName(String sessionCookieName) {
        this.sessionCookieName = sessionCookieName;
        return this;
    }

    /**
     * Get the path to use for storing the session cookie.
     *
     * @return the cookie path to use
     */
    public String getSessionCookiePath() {
        return sessionCookiePath;
    }

    /**
     * Set the cookie path to use for storing the session cookie.
     *
     * @param sessionCookiePath the cookie path to set
     * @return the {@link ServerConfig} for chaining
     */
    @Fluent
    public ServerConfig setSessionCookiePath(String sessionCookiePath) {
        this.sessionCookiePath = sessionCookiePath;
        return this;
    }

    /**
     * Returns if the {@code Secure} property should be used for the session cookie.
     *
     * @return true if the {@code Secure} property should be used for the session cookie
     */
    public boolean useSecureSessionCookie() {
        return secureSessionCookie;
    }

    /**
     * Returns if the {@code Secure} property should be used for the session cookie.
     *
     * Note that this method only used for the Vert.x code generation, as "use..." are not recognized as getters.
     *
     * @see #useSecureSessionCookie()
     * @return true if the {@code Secure} property should be used for the session cookie
     */
    public boolean isSecureSessionCookie() {
        return useSecureSessionCookie();
    }

    /**
     * Set the {@code Secure} property for the session cookie.
     *
     * @param secureSessionCookie if the {@code Secure} property should be set for the session cookie
     * @return the {@link ServerConfig} for chaining
     */
    @Fluent
    public ServerConfig setSecureSessionCookie(boolean secureSessionCookie) {
        this.secureSessionCookie = secureSessionCookie;
        return this;
    }

    /**
     * Returns if the {@code HttpOnly} property should be used for the session cookie.
     *
     * @return true if the {@code HttpOnly} property should be used for the session cookie
     */
    @GenIgnore
    public boolean useHttpOnlySessionCookie() {
        return httpOnlySessionCookie;
    }

    /**
     * Returns if the {@code HttpOnly} property should be used for the session cookie.
     *
     * Note that this method only used for the Vert.x code generation, as "use..." are not recognized as getters.
     *
     * @see #useHttpOnlySessionCookie()
     * @return true if the {@code HttpOnly} property should be used for the session cookie
     */
    public boolean isHttpOnlySessionCookie() {
        return useHttpOnlySessionCookie();
    }

    /**
     * Set the {@code HttpOnly} property for the session cookie.
     *
     * @param httpOnlySessionCookie if the {@code HttpOnly} property should be set for the session cookie
     * @return the {@link ServerConfig} for chaining
     */
    @Fluent
    public ServerConfig setHttpOnlySessionCookie(boolean httpOnlySessionCookie) {
        this.httpOnlySessionCookie = httpOnlySessionCookie;
        return this;
    }

    /**
     * Get the {@code SameSite} policy for the session cookie.
     *
     * @return the {@code SameSite} property
     */
    public CookieSameSite getSessionCookieSameSitePolicy() {
        return sessionCookieSameSitePolicy;
    }

    /**
     * Set the {@code SameSite} policy for the session cookie.
     *
     * @param sessionCookieSameSitePolicy the {@code SameSite} property
     * @return the {@link ServerConfig} for chaining
     */
    @Fluent
    @Nullable
    public ServerConfig setSessionCookieSameSitePolicy(CookieSameSite sessionCookieSameSitePolicy) {
        this.sessionCookieSameSitePolicy = sessionCookieSameSitePolicy;
        return this;
    }

    /**
     * Get the minimum length of the session ID.
     *
     * @return the minimum number of characters of the session ID
     */
    public int getMinSessionIdLength() {
        return minSessionIdLength;
    }

    /**
     * Set the minimum length of the session ID.
     *
     * @param minSessionIdLength the minimum number of characters of the session ID
     * @return the {@link ServerConfig} for chaining
     */
    @Fluent
    public ServerConfig setMinSessionIdLength(int minSessionIdLength) {
        this.minSessionIdLength = minSessionIdLength;
        return this;
    }

    /**
     * Get the strategy to determine a correlation ID for a given request.
     *
     * @return the correlation strategy to use
     */
    public CorrelationStrategy getCorrelationStrategy() {
        return correlationStrategy;
    }

    /**
     * Set the strategy to use to determine a correlation ID for a given request.
     *
     * @param correlationStrategy the correlation strategy to use
     * @return the {@link ServerConfig} for chaining
     */
    @Fluent
    public ServerConfig setCorrelationStrategy(CorrelationStrategy correlationStrategy) {
        this.correlationStrategy = correlationStrategy;
        return this;
    }

    /**
     * Get the HTTP status code sent, when the {@link #getTimeout()} is reached. Defaults to 504 Gateway Timeout.
     *
     * @return an HTTP status code
     */
    public int getTimeoutStatusCode() {
        return timeoutStatusCode;
    }

    /**
     * Set the HTTP status code sent, when the {@link #getTimeout()} is reached. Defaults to 504 Gateway Timeout.
     *
     * @param timeoutStatusCode an HTTP status code
     * @return the {@link ServerConfig} for chaining
     */
    @Fluent
    public ServerConfig setTimeoutStatusCode(int timeoutStatusCode) {
        this.timeoutStatusCode = timeoutStatusCode;
        return this;
    }

    /**
     * Return a list of all endpoints to configure for the {@link ServerVerticle}.
     *
     * @return a list of {@link EndpointConfig}
     */
    public List<EndpointConfig> getEndpointConfigs() {
        return endpointConfigs;
    }

    /**
     * Set the endpoints to configure for the server.
     *
     * @param endpointConfigs the endpoint configurations
     * @return the {@link ServerConfig} for chaining
     */
    @Fluent
    public ServerConfig setEndpointConfigs(List<EndpointConfig> endpointConfigs) {
        this.endpointConfigs = endpointConfigs;
        return this;
    }

    /**
     * Get the default authentication chain configuration, as a fallback for any endpoints without an explicit
     * authentication chain configured.
     *
     * @return the default authentication chain configuration for any endpoints that do not set a authentication chain
     *         explicitly
     */
    public List<AuthHandlerConfig> getAuthChainConfig() {
        return authChainConfig;
    }

    /**
     * Set the default authentication chain configuration, which is used as a fallback for any endpoints without an
     * explicit authentication chain configured. Setting {@code null} or an empty list will result in no authentication
     * check performed.
     *
     * @param authChainConfig the authentication chain
     * @return the {@link ServerConfig} for chaining
     */
    @Fluent
    public ServerConfig setAuthChainConfig(List<AuthHandlerConfig> authChainConfig) {
        this.authChainConfig = authChainConfig;
        return this;
    }

    /**
     * Returns a list of {@link RoutingHandlerFactory} names used to instantiate the handler objects that will be added
     * to the root route. The handlers are added to the route in the order in which they are specified. The class must
     * implement the {@link RoutingHandlerFactory} interface and must provide a default constructor.
     *
     * @return list of {@link RoutingHandlerFactory} names.
     */
    public List<String> getHandlerFactoriesClassNames() {
        return handlerFactoriesClassNames;
    }

    /**
     * Set a custom list of {@link RoutingHandlerFactory} names.
     *
     * @param handlerFactoriesClassNames the list of {@link RoutingHandlerFactory} names.
     * @return the {@link ServerConfig} for chaining
     */
    @Fluent
    public ServerConfig setHandlerFactoriesClassNames(List<String> handlerFactoriesClassNames) {
        this.handlerFactoriesClassNames = handlerFactoriesClassNames;
        return this;
    }

    /**
     * Returns a custom error handler class name, which is instantiated as failure handler of the
     * {@link ServerVerticle}. The {@link DefaultErrorHandler} is used in case no value is supplied. The class must
     * implement the {@link ErrorHandler} interface and must provide either a default constructor, or, in case an error
     * handler template is defined using {@link #setErrorHandlerTemplate(String)} a constructor accepting one string.
     * The parameter-less constructor will be used as a fallback, in case no other constructor is found, the set error
     * template will be ignored in that case.
     *
     * @return the class name of the error handler to handle failures in the server verticle or null, in case no custom
     *         error handler should be used. The server verticle will fall back to {@link DefaultErrorHandler} in the
     *         latter case
     */
    public String getErrorHandlerClassName() {
        return errorHandlerClassName;
    }

    /**
     * Sets a custom error handler class name.
     *
     * @see #getErrorHandlerClassName()
     * @param errorHandlerClassName the class name of a class implementing {@link ErrorHandler} or null
     * @return the {@link ServerConfig} for chaining
     */
    @Fluent
    public ServerConfig setErrorHandlerClassName(String errorHandlerClassName) {
        this.errorHandlerClassName = errorHandlerClassName;
        return this;
    }

    /**
     * Returns the path to an error handler template to use either in the {@link DefaultErrorHandler} or in any custom
     * error handler set using {@link #setErrorHandlerClassName(String)}. Note that any custom error handler may ignore
     * the template specified.
     *
     * @return the file name of an error handler template, or null. In the latter case the error handler has the choice
     *         of the template to use
     */
    public String getErrorHandlerTemplate() {
        return errorHandlerTemplate;
    }

    /**
     * Sets the path to an error handler template to use either in the {@link DefaultErrorHandler} or in any custom
     * error handler set using {@link #setErrorHandlerClassName(String)}.
     *
     * @see #getErrorHandlerTemplate()
     * @param errorHandlerTemplate the file path to an error handler template to use
     * @return the {@link ServerConfig} for chaining
     */
    @Fluent
    public ServerConfig setErrorHandlerTemplate(String errorHandlerTemplate) {
        this.errorHandlerTemplate = errorHandlerTemplate;
        return this;
    }

    /*
     * Override all HttpServerOptions setters, to return the right type for chaining
     */

    @Override
    public ServerConfig addCrlPath(String crlPath) {
        super.addCrlPath(crlPath);
        return this;
    }

    @Override
    public ServerConfig addCrlValue(Buffer crlValue) {
        super.addCrlValue(crlValue);
        return this;
    }

    @Override
    public ServerConfig addEnabledCipherSuite(String suite) {
        super.addEnabledCipherSuite(suite);
        return this;
    }

    @Override
    public ServerConfig addEnabledSecureTransportProtocol(String protocol) {
        super.addEnabledSecureTransportProtocol(protocol);
        return this;
    }

    @Override
    public ServerConfig addWebSocketSubProtocol(String subProtocol) {
        super.addWebSocketSubProtocol(subProtocol);
        return this;
    }

    @Override
    public ServerConfig removeEnabledSecureTransportProtocol(String protocol) {
        super.removeEnabledSecureTransportProtocol(protocol);
        return this;
    }

    @Override
    public ServerConfig setAcceptBacklog(int acceptBacklog) {
        super.setAcceptBacklog(acceptBacklog);
        return this;
    }

    @Override
    public ServerConfig setAcceptUnmaskedFrames(boolean acceptUnmaskedFrames) {
        super.setAcceptUnmaskedFrames(acceptUnmaskedFrames);
        return this;
    }

    @Override
    public ServerConfig setAlpnVersions(List<HttpVersion> alpnVersions) {
        super.setAlpnVersions(alpnVersions);
        return this;
    }

    @Override
    public ServerConfig setClientAuth(ClientAuth clientAuth) {
        super.setClientAuth(clientAuth);
        return this;
    }

    @Override
    public ServerConfig setCompressionLevel(int compressionLevel) {
        super.setCompressionLevel(compressionLevel);
        return this;
    }

    @Override
    public ServerConfig setCompressionSupported(boolean compressionSupported) {
        super.setCompressionSupported(compressionSupported);
        return this;
    }

    @Override
    public ServerConfig setDecoderInitialBufferSize(int decoderInitialBufferSize) {
        super.setDecoderInitialBufferSize(decoderInitialBufferSize);
        return this;
    }

    @Override
    public ServerConfig setDecompressionSupported(boolean decompressionSupported) {
        super.setDecompressionSupported(decompressionSupported);
        return this;
    }

    @Override
    public ServerConfig setEnabledSecureTransportProtocols(Set<String> enabledSecureTransportProtocols) {
        super.setEnabledSecureTransportProtocols(enabledSecureTransportProtocols);
        return this;
    }

    @Override
    public ServerConfig setHandle100ContinueAutomatically(boolean handle100ContinueAutomatically) {
        super.setHandle100ContinueAutomatically(handle100ContinueAutomatically);
        return this;
    }

    @Override
    public ServerConfig setHost(String host) {
        super.setHost(host);
        return this;
    }

    @Override
    public ServerConfig setHttp2ConnectionWindowSize(int http2ConnectionWindowSize) {
        super.setHttp2ConnectionWindowSize(http2ConnectionWindowSize);
        return this;
    }

    @Override
    public ServerConfig setIdleTimeout(int idleTimeout) {
        super.setIdleTimeout(idleTimeout);
        return this;
    }

    @Override
    public ServerConfig setIdleTimeoutUnit(TimeUnit idleTimeoutUnit) {
        super.setIdleTimeoutUnit(idleTimeoutUnit);
        return this;
    }

    @Override
    public ServerConfig setInitialSettings(Http2Settings settings) {
        super.setInitialSettings(settings);
        return this;
    }

    @Override
    public ServerConfig setJdkSslEngineOptions(JdkSSLEngineOptions sslEngineOptions) {
        super.setJdkSslEngineOptions(sslEngineOptions);
        return this;
    }

    @Override
    public ServerConfig setKeyCertOptions(KeyCertOptions options) {
        super.setKeyCertOptions(options);
        return this;
    }

    @Override
    public ServerConfig setKeyStoreOptions(JksOptions options) {
        super.setKeyStoreOptions(options);
        return this;
    }

    @Override
    public ServerConfig setLogActivity(boolean logEnabled) {
        super.setLogActivity(logEnabled);
        return this;
    }

    @Override
    public ServerConfig setMaxChunkSize(int maxChunkSize) {
        super.setMaxChunkSize(maxChunkSize);
        return this;
    }

    @Override
    public ServerConfig setMaxHeaderSize(int maxHeaderSize) {
        super.setMaxHeaderSize(maxHeaderSize);
        return this;
    }

    @Override
    public ServerConfig setMaxInitialLineLength(int maxInitialLineLength) {
        super.setMaxInitialLineLength(maxInitialLineLength);
        return this;
    }

    @Override
    public ServerConfig setMaxWebSocketFrameSize(int maxWebSocketFrameSize) {
        super.setMaxWebSocketFrameSize(maxWebSocketFrameSize);
        return this;
    }

    @Override
    public ServerConfig setMaxWebSocketMessageSize(int maxWebSocketMessageSize) {
        super.setMaxWebSocketMessageSize(maxWebSocketMessageSize);
        return this;
    }

    @Override
    public ServerConfig setOpenSslEngineOptions(OpenSSLEngineOptions sslEngineOptions) {
        super.setOpenSslEngineOptions(sslEngineOptions);
        return this;
    }

    @Override
    public ServerConfig setPemKeyCertOptions(PemKeyCertOptions options) {
        super.setPemKeyCertOptions(options);
        return this;
    }

    @Override
    public ServerConfig setPemTrustOptions(PemTrustOptions options) {
        super.setPemTrustOptions(options);
        return this;
    }

    @Override
    public ServerConfig setPerFrameWebSocketCompressionSupported(boolean supported) {
        super.setPerFrameWebSocketCompressionSupported(supported);
        return this;
    }

    @Override
    public ServerConfig setPerMessageWebSocketCompressionSupported(boolean supported) {
        super.setPerMessageWebSocketCompressionSupported(supported);
        return this;
    }

    @Override
    public ServerConfig setPfxKeyCertOptions(PfxOptions options) {
        super.setPfxKeyCertOptions(options);
        return this;
    }

    @Override
    public ServerConfig setPfxTrustOptions(PfxOptions options) {
        super.setPfxTrustOptions(options);
        return this;
    }

    @Override
    public ServerConfig setPort(int port) {
        super.setPort(port);
        return this;
    }

    @Override
    public ServerConfig setProxyProtocolTimeout(long proxyProtocolTimeout) {
        super.setProxyProtocolTimeout(proxyProtocolTimeout);
        return this;
    }

    @Override
    public ServerConfig setProxyProtocolTimeoutUnit(TimeUnit proxyProtocolTimeoutUnit) {
        super.setProxyProtocolTimeoutUnit(proxyProtocolTimeoutUnit);
        return this;
    }

    @Override
    public ServerConfig setReceiveBufferSize(int receiveBufferSize) {
        super.setReceiveBufferSize(receiveBufferSize);
        return this;
    }

    @Override
    public ServerConfig setReuseAddress(boolean reuseAddress) {
        super.setReuseAddress(reuseAddress);
        return this;
    }

    @Override
    public ServerConfig setReusePort(boolean reusePort) {
        super.setReusePort(reusePort);
        return this;
    }

    @Override
    public ServerConfig setSendBufferSize(int sendBufferSize) {
        super.setSendBufferSize(sendBufferSize);
        return this;
    }

    @Override
    public ServerConfig setSni(boolean sni) {
        super.setSni(sni);
        return this;
    }

    @Override
    public ServerConfig setSoLinger(int soLinger) {
        super.setSoLinger(soLinger);
        return this;
    }

    @Override
    public ServerConfig setSsl(boolean ssl) {
        super.setSsl(ssl);
        return this;
    }

    @Override
    public ServerConfig setSslEngineOptions(SSLEngineOptions sslEngineOptions) {
        super.setSslEngineOptions(sslEngineOptions);
        return this;
    }

    @Override
    public ServerConfig setSslHandshakeTimeout(long sslHandshakeTimeout) {
        super.setSslHandshakeTimeout(sslHandshakeTimeout);
        return this;
    }

    @Override
    public ServerConfig setSslHandshakeTimeoutUnit(TimeUnit sslHandshakeTimeoutUnit) {
        super.setSslHandshakeTimeoutUnit(sslHandshakeTimeoutUnit);
        return this;
    }

    @Override
    public ServerConfig setTcpCork(boolean tcpCork) {
        super.setTcpCork(tcpCork);
        return this;
    }

    @Override
    public ServerConfig setTcpFastOpen(boolean tcpFastOpen) {
        super.setTcpFastOpen(tcpFastOpen);
        return this;
    }

    @Override
    public ServerConfig setTcpKeepAlive(boolean tcpKeepAlive) {
        super.setTcpKeepAlive(tcpKeepAlive);
        return this;
    }

    @Override
    public ServerConfig setTcpNoDelay(boolean tcpNoDelay) {
        super.setTcpNoDelay(tcpNoDelay);
        return this;
    }

    @Override
    public ServerConfig setTcpQuickAck(boolean tcpQuickAck) {
        super.setTcpQuickAck(tcpQuickAck);
        return this;
    }

    @Override
    public ServerConfig setTracingPolicy(TracingPolicy tracingPolicy) {
        super.setTracingPolicy(tracingPolicy);
        return this;
    }

    @Override
    public ServerConfig setTrafficClass(int trafficClass) {
        super.setTrafficClass(trafficClass);
        return this;
    }

    @Override
    public ServerConfig setTrustOptions(TrustOptions options) {
        super.setTrustOptions(options);
        return this;
    }

    @Override
    public ServerConfig setTrustStoreOptions(JksOptions options) {
        super.setTrustStoreOptions(options);
        return this;
    }

    @Override
    public ServerConfig setUseAlpn(boolean useAlpn) {
        super.setUseAlpn(useAlpn);
        return this;
    }

    @Override
    public ServerConfig setUseProxyProtocol(boolean useProxyProtocol) {
        super.setUseProxyProtocol(useProxyProtocol);
        return this;
    }

    @Override
    public ServerConfig setWebSocketAllowServerNoContext(boolean accept) {
        super.setWebSocketAllowServerNoContext(accept);
        return this;
    }

    @Override
    public ServerConfig setWebSocketCompressionLevel(int compressionLevel) {
        super.setWebSocketCompressionLevel(compressionLevel);
        return this;
    }

    @Override
    public ServerConfig setWebSocketPreferredClientNoContext(boolean accept) {
        super.setWebSocketPreferredClientNoContext(accept);
        return this;
    }

    @Override
    public ServerConfig setWebSocketSubProtocols(List<String> subProtocols) {
        super.setWebSocketSubProtocols(subProtocols);
        return this;
    }
}
