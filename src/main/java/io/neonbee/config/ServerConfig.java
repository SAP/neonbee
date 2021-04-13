package io.neonbee.config;

import static io.netty.handler.codec.http.HttpResponseStatus.GATEWAY_TIMEOUT;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.google.common.base.Strings;

import io.neonbee.endpoint.metrics.MetricsEndpoint;
import io.neonbee.endpoint.odatav4.ODataV4Endpoint;
import io.neonbee.endpoint.raw.RawEndpoint;
import io.neonbee.internal.json.ImmutableJsonObject;
import io.neonbee.internal.verticle.ServerVerticle;
import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.ClientAuth;
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
 *   timeoutErrorCode: number, // the error code for the default timeout, defaults to 504
 *   sessionHandling: string, // one of: none, local or enabled, defaults to none
 *   sessionCookieName: string, // the name of the session cookie, defaults to neonbee-web.session
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
public class ServerConfig extends HttpServerOptions {
    public enum SessionHandling {
        /**
         * No session handling
         */
        NONE,
        /**
         * Local session handling or in clustered operation on each cluster node
         */
        LOCAL,
        /**
         * Clustered session handling in a shared map across the whole cluster
         */
        CLUSTERED
    }

    public enum CorrelationStrategy {
        /**
         * Generates a random UUID for every incoming request.
         */
        GENERATE_UUID(routingContext -> UUID.randomUUID().toString()),
        /**
         * Generates a random UUID for every incoming request.
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

        public String getCorrelationId(RoutingContext routingContext) {
            return mapper.apply(routingContext);
        }
    }

    public static final String PROPERTY_PORT = "port";

    public static final int DEFAULT_PORT = 8080;

    public static final boolean DEFAULT_USE_ALPN = true;

    public static final boolean DEFAULT_COMPRESSION_SUPPORTED = true;

    public static final int DEFAULT_COMPRESSION_LEVEL = 1;

    public static final boolean DEFAULT_DECOMPRESSION_SUPPORTED = true;

    public static final int DEFAULT_TIMEOUT = 30;

    public static final String DEFAULT_SESSION_COOKIE_NAME = "neonbee-web.session";

    private static final List<EndpointConfig> DEFAULT_ENDPOINT_CONFIGS = Collections.unmodifiableList(Arrays
            .stream(new Class<?>[] { RawEndpoint.class, ODataV4Endpoint.class, MetricsEndpoint.class })
            .map(endpointClass -> new EndpointConfig().setType(endpointClass.getName())).collect(Collectors.toList()));

    private int timeout = DEFAULT_TIMEOUT;

    private int timeoutErrorCode = GATEWAY_TIMEOUT.code();

    private SessionHandling sessionHandling = SessionHandling.NONE;

    private String sessionCookieName = DEFAULT_SESSION_COOKIE_NAME;

    private CorrelationStrategy correlationStrategy = CorrelationStrategy.REQUEST_HEADER;

    private List<EndpointConfig> endpointConfigs = new ArrayList<>(DEFAULT_ENDPOINT_CONFIGS);

    private List<AuthHandlerConfig> authChainConfig;

    public ServerConfig() {
        super();
        overrideDefaults(null);
    }

    public ServerConfig(JsonObject json) {
        super(json);
        overrideDefaults(json);
        ServerConfigConverter.fromJson(json, this);
    }

    private void overrideDefaults(JsonObject json) {
        if (json == null) {
            json = ImmutableJsonObject.EMPTY;
        }

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
        return json;
    }

    public int getTimeout() {
        return timeout;
    }

    public ServerConfig setTimeout(int timeout) {
        this.timeout = timeout;
        return this;
    }

    public SessionHandling getSessionHandling() {
        return sessionHandling;
    }

    public ServerConfig setSessionHandling(SessionHandling sessionHandling) {
        this.sessionHandling = sessionHandling;
        return this;
    }

    public String getSessionCookieName() {
        return sessionCookieName;
    }

    public ServerConfig setSessionCookieName(String sessionCookieName) {
        this.sessionCookieName = sessionCookieName;
        return this;
    }

    public CorrelationStrategy getCorrelationStrategy() {
        return correlationStrategy;
    }

    public ServerConfig setCorrelationStrategy(CorrelationStrategy correlationStrategy) {
        this.correlationStrategy = correlationStrategy;
        return this;
    }

    public int getTimeoutErrorCode() {
        return timeoutErrorCode;
    }

    public ServerConfig setTimeoutErrorCode(int timeoutErrorCode) {
        this.timeoutErrorCode = timeoutErrorCode;
        return this;
    }

    public List<EndpointConfig> getEndpointConfigs() {
        return endpointConfigs;
    }

    public ServerConfig setEndpointConfigs(List<EndpointConfig> endpointConfigs) {
        this.endpointConfigs = endpointConfigs;
        return this;
    }

    public List<AuthHandlerConfig> getAuthChainConfig() {
        return authChainConfig;
    }

    public ServerConfig setAuthChainConfig(List<AuthHandlerConfig> authChainConfig) {
        this.authChainConfig = authChainConfig;
        return this;
    }

    /*
     * Override all HttpServerOptions setters, to return the right type for chaining
     */

    @Override
    public ServerConfig addCrlPath(String crlPath) throws NullPointerException {
        super.addCrlPath(crlPath);
        return this;
    }

    @Override
    public ServerConfig addCrlValue(Buffer crlValue) throws NullPointerException {
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
