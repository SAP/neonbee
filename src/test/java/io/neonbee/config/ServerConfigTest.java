package io.neonbee.config;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.config.ServerConfig.DEFAULT_COMPRESSION_LEVEL;
import static io.neonbee.config.ServerConfig.DEFAULT_COMPRESSION_SUPPORTED;
import static io.neonbee.config.ServerConfig.DEFAULT_DECOMPRESSION_SUPPORTED;
import static io.neonbee.config.ServerConfig.DEFAULT_HANDLER_FACTORIES_CLASS_NAMES;
import static io.neonbee.config.ServerConfig.DEFAULT_PORT;
import static io.neonbee.config.ServerConfig.DEFAULT_USE_ALPN;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.neonbee.config.AuthHandlerConfig.AuthHandlerType;
import io.neonbee.config.ServerConfig.CorrelationStrategy;
import io.neonbee.config.ServerConfig.SessionHandling;
import io.neonbee.endpoint.raw.RawEndpoint;
import io.neonbee.internal.handler.factories.CacheControlHandlerFactory;
import io.neonbee.internal.handler.factories.CorrelationIdHandlerFactory;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.ClientAuth;
import io.vertx.core.http.Http2Settings;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.json.JsonArray;
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

class ServerConfigTest {
    private static final String ERROR_HANDLER = "io.neonbee.CustomErrorHandler";

    private static final String ERROR_TEMPLATE = "custom-template.html";

    private static final List<String> FACTORY_CLASS_NAME_LIST =
            List.of(CacheControlHandlerFactory.class.getName(), CorrelationIdHandlerFactory.class.getName());

    @Test
    @DisplayName("test toJson")
    void testToJson() {
        int timeout = 1;
        SessionHandling sessionHandling = SessionHandling.LOCAL;
        String sessionCookieName = "setSessionCookieName";
        CorrelationStrategy correlationStrategy = CorrelationStrategy.GENERATE_UUID;
        int timeoutStatusCode = 2;
        EndpointConfig epc = new EndpointConfig().setType("hodor");
        List<EndpointConfig> endpointConfigs = List.of(epc);
        AuthHandlerConfig ahc = new AuthHandlerConfig().setType(AuthHandlerType.BASIC);
        List<AuthHandlerConfig> authHandlerConfig = List.of(ahc);

        ServerConfig sc = new ServerConfig();
        sc.setTimeout(timeout).setSessionHandling(sessionHandling).setSessionCookieName(sessionCookieName);
        sc.setCorrelationStrategy(correlationStrategy).setTimeoutStatusCode(timeoutStatusCode);
        sc.setEndpointConfigs(endpointConfigs).setAuthChainConfig(authHandlerConfig);
        sc.setErrorHandlerClassName(ERROR_HANDLER).setErrorHandlerTemplate(ERROR_TEMPLATE);
        sc.setHandlerFactoriesClassNames(FACTORY_CLASS_NAME_LIST);

        JsonObject expected = new JsonObject().put("timeout", timeout).put("sessionHandling", sessionHandling.name());
        expected.put("sessionCookieName", sessionCookieName).put("correlationStrategy", correlationStrategy.name());
        expected.put("timeoutStatusCode", timeoutStatusCode).put("endpoints", new JsonArray().add(epc.toJson()));
        expected.put("authenticationChain", new JsonArray().add(ahc.toJson()));
        expected.put("errorHandler", ERROR_HANDLER).put("errorTemplate", ERROR_TEMPLATE);

        JsonArray expectedHandlerFactories = new JsonArray();
        FACTORY_CLASS_NAME_LIST.forEach(expectedHandlerFactories::add);
        expected.put("handlerFactories", expectedHandlerFactories);

        assertThat(sc.toJson()).containsAtLeastElementsIn(expected);
    }

    @Test
    @DisplayName("test JsonConstructor")
    void testJsonConstructor() {
        int timeout = 1;
        SessionHandling sessionHandling = SessionHandling.LOCAL;
        String sessionCookieName = "setSessionCookieName";
        CorrelationStrategy correlationStrategy = CorrelationStrategy.GENERATE_UUID;
        int timeoutStatusCode = 2;
        EndpointConfig epc = new EndpointConfig().setType("hodor");
        AuthHandlerConfig ahc = new AuthHandlerConfig().setType(AuthHandlerType.BASIC);
        CorsConfig corsConfig = new CorsConfig().setOrigins(List.of("http://foo.bar")).setEnabled(true);

        JsonObject json = new JsonObject().put("timeout", timeout).put("sessionHandling", sessionHandling.name());
        json.put("sessionCookieName", sessionCookieName).put("correlationStrategy", correlationStrategy.name());
        json.put("timeoutStatusCode", timeoutStatusCode).put("endpoints", new JsonArray().add(epc.toJson()));
        json.put("authenticationChain", new JsonArray().add(ahc.toJson()));
        json.put("errorHandler", ERROR_HANDLER);
        json.put("errorTemplate", ERROR_TEMPLATE);
        json.put("cors", corsConfig.toJson());

        JsonArray handlerFactories = new JsonArray();
        FACTORY_CLASS_NAME_LIST.forEach(handlerFactories::add);
        json.put("handlerFactories", handlerFactories);

        ServerConfig sc = new ServerConfig(json);
        assertThat(sc.getTimeout()).isEqualTo(timeout);
        assertThat(sc.getSessionHandling()).isEqualTo(sessionHandling);
        assertThat(sc.getSessionCookieName()).isEqualTo(sessionCookieName);
        assertThat(sc.getCorrelationStrategy()).isEqualTo(correlationStrategy);
        assertThat(sc.getTimeoutStatusCode()).isEqualTo(timeoutStatusCode);
        assertThat(sc.getEndpointConfigs()).contains(epc);
        assertThat(sc.getAuthChainConfig()).contains(ahc);
        assertThat(sc.getErrorHandlerClassName()).isEqualTo(ERROR_HANDLER);
        assertThat(sc.getErrorHandlerTemplate()).isEqualTo(ERROR_TEMPLATE);
        assertThat(sc.getCorsConfig()).isEqualTo(corsConfig);
    }

    @Test
    @DisplayName("test the default constructor")
    void testDefaultConstructor() {
        ServerConfig sc = new ServerConfig();

        assertThat(sc.getPort()).isEqualTo(DEFAULT_PORT);
        assertThat(sc.isUseAlpn()).isEqualTo(DEFAULT_USE_ALPN);
        assertThat(sc.isCompressionSupported()).isEqualTo(DEFAULT_COMPRESSION_SUPPORTED);
        assertThat(sc.getCompressionLevel()).isEqualTo(DEFAULT_COMPRESSION_LEVEL);
        assertThat(sc.isDecompressionSupported()).isEqualTo(DEFAULT_DECOMPRESSION_SUPPORTED);
        assertThat(sc.getHandlerFactoriesClassNames()).isEqualTo(DEFAULT_HANDLER_FACTORIES_CLASS_NAMES);
    }

    @Test
    @DisplayName("test getters and setters")
    void testGettersAndSetters() {
        ServerConfig sc = new ServerConfig();

        assertThat(sc.setTimeout(1)).isSameInstanceAs(sc);
        assertThat(sc.getTimeout()).isEqualTo(1);

        SessionHandling sessionHandling = SessionHandling.LOCAL;
        assertThat(sc.setSessionHandling(sessionHandling)).isSameInstanceAs(sc);
        assertThat(sc.getSessionHandling()).isEqualTo(sessionHandling);

        assertThat(sc.setSessionCookieName("setSessionCookieName")).isSameInstanceAs(sc);
        assertThat(sc.getSessionCookieName()).isEqualTo("setSessionCookieName");

        CorrelationStrategy correlationStrategy = CorrelationStrategy.GENERATE_UUID;
        assertThat(sc.setCorrelationStrategy(correlationStrategy)).isSameInstanceAs(sc);
        assertThat(sc.getCorrelationStrategy()).isEqualTo(correlationStrategy);

        assertThat(sc.setTimeoutStatusCode(2)).isSameInstanceAs(sc);
        assertThat(sc.getTimeoutStatusCode()).isEqualTo(2);

        List<EndpointConfig> endpointConfigs = List.of(new RawEndpoint().getDefaultConfig());
        assertThat(sc.setEndpointConfigs(endpointConfigs)).isSameInstanceAs(sc);
        assertThat(sc.getEndpointConfigs()).containsExactlyElementsIn(endpointConfigs);

        List<AuthHandlerConfig> authHandlerConfig = List.of(new AuthHandlerConfig());
        assertThat(sc.setAuthChainConfig(authHandlerConfig)).isSameInstanceAs(sc);
        assertThat(sc.getAuthChainConfig()).containsExactlyElementsIn(authHandlerConfig);

        assertThat(sc.setErrorHandlerClassName(ERROR_HANDLER)).isSameInstanceAs(sc);
        assertThat(sc.getErrorHandlerClassName()).isEqualTo(ERROR_HANDLER);

        assertThat(sc.setErrorHandlerTemplate(ERROR_TEMPLATE)).isSameInstanceAs(sc);
        assertThat(sc.getErrorHandlerTemplate()).isEqualTo(ERROR_TEMPLATE);

        assertThat(sc.setHandlerFactoriesClassNames(FACTORY_CLASS_NAME_LIST)).isSameInstanceAs(sc);
        assertThat(sc.getHandlerFactoriesClassNames()).isEqualTo(FACTORY_CLASS_NAME_LIST);
    }

    @Test
    @DisplayName("Overridden setters must be fluent and call super")
    void testOverriddenSetters() {
        ServerConfig sc = new ServerConfig();

        assertThat(sc.addCrlPath("addCrlPath")).isSameInstanceAs(sc);
        assertThat(sc.getCrlPaths()).containsExactly("addCrlPath");

        Buffer crlValue = Buffer.buffer("addCrlValue");
        assertThat(sc.addCrlValue(crlValue)).isSameInstanceAs(sc);
        assertThat(sc.getCrlValues()).containsExactly(crlValue);

        assertThat(sc.addEnabledCipherSuite("addEnabledCipherSuite")).isSameInstanceAs(sc);
        assertThat(sc.getEnabledCipherSuites()).containsExactly("addEnabledCipherSuite");

        assertThat(sc.addEnabledSecureTransportProtocol("addEnabledSecureTransportProtocol")).isSameInstanceAs(sc);
        assertThat(sc.getEnabledSecureTransportProtocols()).contains("addEnabledSecureTransportProtocol");

        assertThat(sc.removeEnabledSecureTransportProtocol("addEnabledSecureTransportProtocol")).isSameInstanceAs(sc);
        assertThat(sc.getEnabledSecureTransportProtocols()).doesNotContain("addEnabledSecureTransportProtocol");

        assertThat(sc.addWebSocketSubProtocol("addWebSocketSubProtocol")).isSameInstanceAs(sc);
        assertThat(sc.getWebSocketSubProtocols()).containsExactly("addWebSocketSubProtocol");

        assertThat(sc.setAcceptBacklog(1)).isSameInstanceAs(sc);
        assertThat(sc.getAcceptBacklog()).isEqualTo(1);

        assertThat(sc.setAcceptUnmaskedFrames(false)).isSameInstanceAs(sc);
        assertThat(sc.isAcceptUnmaskedFrames()).isEqualTo(false);

        List<HttpVersion> alpnVersions = List.of(HttpVersion.HTTP_1_0);
        assertThat(sc.setAlpnVersions(alpnVersions)).isSameInstanceAs(sc);
        assertThat(sc.getAlpnVersions()).containsExactlyElementsIn(alpnVersions);

        ClientAuth clientAuth = ClientAuth.REQUEST;
        assertThat(sc.setClientAuth(clientAuth)).isSameInstanceAs(sc);
        assertThat(sc.getClientAuth()).isEqualTo(clientAuth);

        assertThat(sc.setCompressionLevel(2)).isSameInstanceAs(sc);
        assertThat(sc.getCompressionLevel()).isEqualTo(2);

        assertThat(sc.setCompressionSupported(false)).isSameInstanceAs(sc);
        assertThat(sc.isCompressionSupported()).isEqualTo(false);

        assertThat(sc.setDecoderInitialBufferSize(3)).isSameInstanceAs(sc);
        assertThat(sc.getDecoderInitialBufferSize()).isEqualTo(3);

        assertThat(sc.setDecompressionSupported(false)).isSameInstanceAs(sc);
        assertThat(sc.isDecompressionSupported()).isEqualTo(false);

        Set<String> protocols = Set.of("setEnabledSecureTransportProtocols");
        assertThat(sc.setEnabledSecureTransportProtocols(protocols)).isSameInstanceAs(sc);
        assertThat(sc.getEnabledSecureTransportProtocols()).containsExactlyElementsIn(protocols);

        assertThat(sc.setHandle100ContinueAutomatically(false)).isSameInstanceAs(sc);
        assertThat(sc.isHandle100ContinueAutomatically()).isEqualTo(false);

        assertThat(sc.setHost("setHost")).isSameInstanceAs(sc);
        assertThat(sc.getHost()).isEqualTo("setHost");

        assertThat(sc.setHttp2ConnectionWindowSize(4)).isSameInstanceAs(sc);
        assertThat(sc.getHttp2ConnectionWindowSize()).isEqualTo(4);

        assertThat(sc.setIdleTimeout(5)).isSameInstanceAs(sc);
        assertThat(sc.getIdleTimeout()).isEqualTo(5);

        TimeUnit timeUnit = TimeUnit.DAYS;
        assertThat(sc.setIdleTimeoutUnit(timeUnit)).isSameInstanceAs(sc);
        assertThat(sc.getIdleTimeoutUnit()).isEqualTo(timeUnit);

        Http2Settings http2Settings = new Http2Settings();
        assertThat(sc.setInitialSettings(http2Settings)).isSameInstanceAs(sc);
        assertThat(sc.getInitialSettings()).isEqualTo(http2Settings);

        JdkSSLEngineOptions jdkSSLEngineOptions = new JdkSSLEngineOptions();
        assertThat(sc.setJdkSslEngineOptions(jdkSSLEngineOptions)).isSameInstanceAs(sc);
        assertThat(sc.getJdkSslEngineOptions()).isEqualTo(jdkSSLEngineOptions);

        KeyCertOptions keyCertOptions = Mockito.mock(KeyCertOptions.class);
        assertThat(sc.setKeyCertOptions(keyCertOptions)).isSameInstanceAs(sc);
        assertThat(sc.getKeyCertOptions()).isEqualTo(keyCertOptions);

        JksOptions jksOptions = new JksOptions();
        assertThat(sc.setKeyStoreOptions(jksOptions)).isSameInstanceAs(sc);
        assertThat(sc.getKeyStoreOptions()).isEqualTo(jksOptions);

        assertThat(sc.setLogActivity(false)).isSameInstanceAs(sc);
        assertThat(sc.getLogActivity()).isEqualTo(false);

        assertThat(sc.setMaxChunkSize(6)).isSameInstanceAs(sc);
        assertThat(sc.getMaxChunkSize()).isEqualTo(6);

        assertThat(sc.setMaxHeaderSize(7)).isSameInstanceAs(sc);
        assertThat(sc.getMaxHeaderSize()).isEqualTo(7);

        assertThat(sc.setMaxInitialLineLength(8)).isSameInstanceAs(sc);
        assertThat(sc.getMaxInitialLineLength()).isEqualTo(8);

        assertThat(sc.setMaxWebSocketFrameSize(9)).isSameInstanceAs(sc);
        assertThat(sc.getMaxWebSocketFrameSize()).isEqualTo(9);

        assertThat(sc.setMaxWebSocketMessageSize(10)).isSameInstanceAs(sc);
        assertThat(sc.getMaxWebSocketMessageSize()).isEqualTo(10);

        OpenSSLEngineOptions openSSLEngineOptions = new OpenSSLEngineOptions();
        assertThat(sc.setOpenSslEngineOptions(openSSLEngineOptions)).isSameInstanceAs(sc);
        assertThat(sc.getOpenSslEngineOptions()).isEqualTo(openSSLEngineOptions);

        PemKeyCertOptions pemKeyCertOptions = new PemKeyCertOptions();
        assertThat(sc.setPemKeyCertOptions(pemKeyCertOptions)).isSameInstanceAs(sc);
        assertThat(sc.getPemKeyCertOptions()).isEqualTo(pemKeyCertOptions);

        PemTrustOptions pemTrustOptions = new PemTrustOptions();
        assertThat(sc.setPemTrustOptions(pemTrustOptions)).isSameInstanceAs(sc);
        assertThat(sc.getPemTrustOptions()).isEqualTo(pemTrustOptions);

        assertThat(sc.setPerFrameWebSocketCompressionSupported(false)).isSameInstanceAs(sc);
        assertThat(sc.getPerFrameWebSocketCompressionSupported()).isEqualTo(false);

        assertThat(sc.setPerMessageWebSocketCompressionSupported(false)).isSameInstanceAs(sc);
        assertThat(sc.getPerMessageWebSocketCompressionSupported()).isEqualTo(false);

        PfxOptions pfxOptions = new PfxOptions();
        assertThat(sc.setPfxKeyCertOptions(pfxOptions)).isSameInstanceAs(sc);
        assertThat(sc.getPfxKeyCertOptions()).isEqualTo(pfxOptions);

        assertThat(sc.setPfxTrustOptions(pfxOptions)).isSameInstanceAs(sc);
        assertThat(sc.getPfxTrustOptions()).isEqualTo(pfxOptions);

        assertThat(sc.setPort(11)).isSameInstanceAs(sc);
        assertThat(sc.getPort()).isEqualTo(11);

        assertThat(sc.setProxyProtocolTimeout(12L)).isSameInstanceAs(sc);
        assertThat(sc.getProxyProtocolTimeout()).isEqualTo(12L);

        timeUnit = TimeUnit.HOURS;
        assertThat(sc.setProxyProtocolTimeoutUnit(timeUnit)).isSameInstanceAs(sc);
        assertThat(sc.getProxyProtocolTimeoutUnit()).isEqualTo(timeUnit);

        assertThat(sc.setReceiveBufferSize(13)).isSameInstanceAs(sc);
        assertThat(sc.getReceiveBufferSize()).isEqualTo(13);

        assertThat(sc.setReuseAddress(false)).isSameInstanceAs(sc);
        assertThat(sc.isReuseAddress()).isEqualTo(false);

        assertThat(sc.setReusePort(false)).isSameInstanceAs(sc);
        assertThat(sc.isReusePort()).isEqualTo(false);

        assertThat(sc.setSendBufferSize(14)).isSameInstanceAs(sc);
        assertThat(sc.getSendBufferSize()).isEqualTo(14);

        assertThat(sc.setSni(false)).isSameInstanceAs(sc);
        assertThat(sc.isSni()).isEqualTo(false);

        assertThat(sc.setSoLinger(15)).isSameInstanceAs(sc);
        assertThat(sc.getSoLinger()).isEqualTo(15);

        assertThat(sc.setSsl(false)).isSameInstanceAs(sc);
        assertThat(sc.isSsl()).isEqualTo(false);

        SSLEngineOptions sSLEngineOptions = Mockito.mock(SSLEngineOptions.class);
        assertThat(sc.setSslEngineOptions(sSLEngineOptions)).isSameInstanceAs(sc);
        assertThat(sc.getSslEngineOptions()).isEqualTo(sSLEngineOptions);

        assertThat(sc.setSslHandshakeTimeout(16L)).isSameInstanceAs(sc);
        assertThat(sc.getSslHandshakeTimeout()).isEqualTo(16L);

        timeUnit = TimeUnit.MINUTES;
        assertThat(sc.setProxyProtocolTimeoutUnit(timeUnit)).isSameInstanceAs(sc);
        assertThat(sc.getProxyProtocolTimeoutUnit()).isEqualTo(timeUnit);

        assertThat(sc.setTcpCork(false)).isSameInstanceAs(sc);
        assertThat(sc.isTcpCork()).isEqualTo(false);

        assertThat(sc.setTcpFastOpen(false)).isSameInstanceAs(sc);
        assertThat(sc.isTcpFastOpen()).isEqualTo(false);

        assertThat(sc.setTcpKeepAlive(false)).isSameInstanceAs(sc);
        assertThat(sc.isTcpKeepAlive()).isEqualTo(false);

        assertThat(sc.setTcpNoDelay(false)).isSameInstanceAs(sc);
        assertThat(sc.isTcpNoDelay()).isEqualTo(false);

        assertThat(sc.setTcpQuickAck(false)).isSameInstanceAs(sc);
        assertThat(sc.isTcpQuickAck()).isEqualTo(false);

        TracingPolicy tracingPolicy = TracingPolicy.ALWAYS;
        assertThat(sc.setTracingPolicy(tracingPolicy)).isSameInstanceAs(sc);
        assertThat(sc.getTracingPolicy()).isEqualTo(tracingPolicy);

        assertThat(sc.setTrafficClass(17)).isSameInstanceAs(sc);
        assertThat(sc.getTrafficClass()).isEqualTo(17);

        TrustOptions trustOptions = Mockito.mock(TrustOptions.class);
        assertThat(sc.setTrustOptions(trustOptions)).isSameInstanceAs(sc);
        assertThat(sc.getTrustOptions()).isEqualTo(trustOptions);

        assertThat(sc.setTrustStoreOptions(jksOptions)).isSameInstanceAs(sc);
        assertThat(sc.getTrustStoreOptions()).isEqualTo(jksOptions);

        assertThat(sc.setUseAlpn(false)).isSameInstanceAs(sc);
        assertThat(sc.isUseAlpn()).isEqualTo(false);

        assertThat(sc.setUseProxyProtocol(false)).isSameInstanceAs(sc);
        assertThat(sc.isUseProxyProtocol()).isEqualTo(false);

        assertThat(sc.setWebSocketAllowServerNoContext(false)).isSameInstanceAs(sc);
        assertThat(sc.getWebSocketAllowServerNoContext()).isEqualTo(false);

        assertThat(sc.setWebSocketCompressionLevel(18)).isSameInstanceAs(sc);
        assertThat(sc.getWebSocketCompressionLevel()).isEqualTo(18);

        assertThat(sc.setWebSocketPreferredClientNoContext(false)).isSameInstanceAs(sc);
        assertThat(sc.getWebSocketPreferredClientNoContext()).isEqualTo(false);

        List<String> subProtocols = List.of("");
        assertThat(sc.setWebSocketSubProtocols(subProtocols)).isSameInstanceAs(sc);
        assertThat(sc.getWebSocketSubProtocols()).containsExactlyElementsIn(subProtocols);

        assertThat(sc.setHandlerFactoriesClassNames(FACTORY_CLASS_NAME_LIST)).isSameInstanceAs(sc);
        assertThat(sc.getHandlerFactoriesClassNames()).containsExactlyElementsIn(FACTORY_CLASS_NAME_LIST);
    }
}
