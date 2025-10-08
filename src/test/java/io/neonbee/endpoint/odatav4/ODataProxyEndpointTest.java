package io.neonbee.endpoint.odatav4;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.endpoint.odatav4.ODataProxyEndpoint.DEFAULT_BASE_PATH;
import static io.neonbee.endpoint.odatav4.ODataProxyEndpointHandler.determineQualifiedName;
import static io.neonbee.test.helper.DeploymentHelper.NEONBEE_NAMESPACE;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;

import org.apache.olingo.commons.api.edm.FullQualifiedName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import io.neonbee.NeonBeeDeployable;
import io.neonbee.config.EndpointConfig;
import io.neonbee.config.ServerConfig;
import io.neonbee.data.DataContext;
import io.neonbee.data.DataException;
import io.neonbee.data.DataQuery;
import io.neonbee.endpoint.Endpoint;
import io.neonbee.entity.EntityVerticle;
import io.neonbee.internal.verticle.ServerVerticle;
import io.neonbee.test.base.DataVerticleTestBase;
import io.neonbee.test.helper.WorkingDirectoryBuilder;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.junit5.VertxTestContext;

class ODataProxyEndpointTest extends DataVerticleTestBase {
    @Override
    protected WorkingDirectoryBuilder provideWorkingDirectoryBuilder(TestInfo testInfo, VertxTestContext testContext) {
        return super.provideWorkingDirectoryBuilder(testInfo, testContext).setCustomTask(root -> {
            DeploymentOptions opts = WorkingDirectoryBuilder.readDeploymentOptions(ServerVerticle.class, root);
            EndpointConfig endpointConfig = new EndpointConfig().setType(ODataProxyEndpoint.class.getName())
                    .setBasePath(DEFAULT_BASE_PATH);
            ServerConfig serverConfig = new ServerConfig(opts.getConfig()).setEndpointConfigs(List.of(endpointConfig));
            opts.setConfig(serverConfig.toJson());
            WorkingDirectoryBuilder.writeDeploymentOptions(ServerVerticle.class, opts, root);
        });
    }

    private static final String CONTENT_TYPE = "Content-Type";

    private static RoutingContext mockRoutingContext(String routingPath) {
        RoutingContext routingContextMock = mock(RoutingContext.class);
        Route routeMock = mock(Route.class);

        when(routingContextMock.normalizedPath()).thenReturn(DEFAULT_BASE_PATH + routingPath);
        when(routingContextMock.mountPoint()).thenReturn(DEFAULT_BASE_PATH);
        when(routingContextMock.currentRoute()).thenReturn(routeMock);
        when(routeMock.getPath()).thenReturn(null);

        return routingContextMock;
    }

    @Test
    @DisplayName("Test determineQualifiedName extracts correct verticle name")
    void testDetermineQualifiedName() {
        assertThat(determineQualifiedName(mockRoutingContext("Verticle"))).isEqualTo("Verticle");
        assertThat(determineQualifiedName(mockRoutingContext("_verticle"))).isEqualTo("_verticle");
        assertThat(determineQualifiedName(mockRoutingContext("namespace/Verticle"))).isEqualTo("namespace/Verticle");
        assertThat(determineQualifiedName(mockRoutingContext("namespace/_Verticle")))
                .isEqualTo("namespace/_Verticle");
        assertThat(determineQualifiedName(mockRoutingContext("nameSPACE/Verticle"))).isEqualTo("namespace/Verticle");
        assertThat(determineQualifiedName(mockRoutingContext("nsA/nsB/Verticle"))).isEqualTo("nsa/nsb/Verticle");
        assertThat(determineQualifiedName(mockRoutingContext("nsA/nsB/_Verticle"))).isEqualTo("nsa/nsb/_Verticle");
        assertThat(determineQualifiedName(mockRoutingContext("Verticle/Path/MorePath"))).isEqualTo("Verticle");
        assertThat(determineQualifiedName(mockRoutingContext("nsA/nsB/Verticle/Path")))
                .isEqualTo("nsa/nsb/Verticle");
        assertThat(determineQualifiedName(mockRoutingContext("nsA/nsB/_Verticle/Path")))
                .isEqualTo("nsa/nsb/_Verticle");
        assertThat(determineQualifiedName(mockRoutingContext("neonbee/TestVerticle-23-42/hodor/hodor")))
                .isEqualTo("neonbee/TestVerticle-23-42");

        assertThat(determineQualifiedName(mockRoutingContext("/odataproxy/x"))).isNull();
        assertThat(determineQualifiedName(mockRoutingContext("verticle"))).isNull();
        assertThat(determineQualifiedName(mockRoutingContext("nameSPACE/verticle"))).isNull();
        assertThat(determineQualifiedName(mockRoutingContext("nsA/nsB/verticle/path"))).isNull();
    }

    @Test
    @DisplayName("Test ODataProxyEndpoint forwards request to proxy verticle")
    void testForwardRequestToVerticle(VertxTestContext testContext) {
        String verticleName = EntityVerticle.getName(TestProxyEntityVerticle.class);
        String expectedResponse = "Test response from verticle";

        TestProxyEntityVerticle verticle = proxyVerticle(
                (query, context) -> Future.succeededFuture(Buffer.buffer(expectedResponse)));

        deployVerticle(verticle).compose(v -> sendProxyRequest(verticleName, "", ""))
                .onComplete(testContext.succeeding(resp -> testContext.verify(() -> {
                    assertThat(resp.statusCode()).isEqualTo(200);
                    assertThat(resp.bodyAsString()).isEqualTo(expectedResponse);
                    assertThat(resp.getHeader(CONTENT_TYPE)).isEqualTo("application/octet-stream");
                    testContext.completeNow();
                })));
    }

    @Test
    @DisplayName("Test ODataProxyEndpoint sets originalUrl in context")
    void testOriginalUrlInDataContext(VertxTestContext testContext) {
        String verticleName = EntityVerticle.getName(TestProxyEntityVerticle.class);

        TestProxyEntityVerticle verticle = proxyVerticle((query, context) -> {
            String origUrl = context.get("origUrl");
            testContext.verify(() -> {
                assertThat(origUrl).isNotNull();
                assertThat(origUrl).contains(DEFAULT_BASE_PATH);
                assertThat(origUrl).contains(verticleName);
            });
            return Future.succeededFuture(Buffer.buffer("Success"));
        });

        deployVerticle(verticle).compose(v -> sendProxyRequest(verticleName, "", ""))
                .onComplete(testContext.succeedingThenComplete());
    }

    @Test
    @DisplayName("Test ODataProxyEndpoint forwards request body")
    void testForwardRequestBody(VertxTestContext testContext) {
        String verticleName = EntityVerticle.getName(TestProxyEntityVerticle.class);
        JsonObject requestBody = new JsonObject().put("test", "data").put("number", 42);

        TestProxyEntityVerticle verticle = proxyVerticle((query, context) -> {
            testContext.verify(() -> {
                Buffer body = query.getBody();
                assertThat(body).isNotNull();
                assertThat(body.toJsonObject()).isEqualTo(requestBody);
            });
            return Future.succeededFuture(Buffer.buffer("Body received"));
        });

        deployVerticle(verticle).compose(v -> sendProxyRequestWithBody(verticleName, "", "", requestBody))
                .onComplete(testContext.succeeding(resp -> testContext.verify(() -> {
                    assertThat(resp.statusCode()).isEqualTo(200);
                    assertThat(resp.bodyAsString()).isEqualTo("Body received");
                    testContext.completeNow();
                })));
    }

    @Test
    @DisplayName("Test ODataProxyEndpoint with URI path")
    void testWithUriPath(VertxTestContext testContext) {
        String verticleName = EntityVerticle.getName(TestProxyEntityVerticle.class);
        String expectedPath = "/additional/path";

        TestProxyEntityVerticle verticle = proxyVerticle((query, context) -> {
            testContext.verify(() -> assertThat(query.getUriPath()).isEqualTo(expectedPath));
            return Future.succeededFuture(Buffer.buffer("Path received"));
        });

        deployVerticle(verticle).compose(v -> sendProxyRequest(verticleName, expectedPath, ""))
                .onComplete(testContext.succeeding(resp -> testContext.verify(() -> {
                    assertThat(resp.statusCode()).isEqualTo(200);
                    testContext.completeNow();
                })));
    }

    @Test
    @DisplayName("Test ODataProxyEndpoint returns NOT_FOUND for non-existent verticle")
    void testNonExistentVerticle(VertxTestContext testContext) {
        String nonExistentVerticle = "NonExistentVerticle" + UUID.randomUUID().toString();

        sendProxyRequest(nonExistentVerticle, "", "")
                .onComplete(testContext.succeeding(resp -> testContext.verify(() -> {
                    assertThat(resp.statusCode()).isEqualTo(NOT_FOUND.code());
                    testContext.completeNow();
                })));
    }

    @Test
    @DisplayName("Test ODataProxyEndpoint returns BAD_REQUEST for empty verticle name")
    void testEmptyVerticleName(VertxTestContext testContext) {
        sendProxyRequest("", "", "")
                .onComplete(testContext.succeeding(resp -> testContext.verify(() -> {
                    assertThat(resp.statusCode()).isEqualTo(BAD_REQUEST.code());
                    testContext.completeNow();
                })));
    }

    @Test
    @DisplayName("Test ODataProxyEndpoint handles DataException")
    void testHandleDataException(VertxTestContext testContext) {
        String verticleName = EntityVerticle.getName(TestProxyEntityVerticle.class);

        TestProxyEntityVerticle verticle = proxyVerticle(
                (query, context) -> Future.failedFuture(new DataException(500, "Internal error")));

        deployVerticle(verticle).compose(v -> sendProxyRequest(verticleName, "", ""))
                .onComplete(testContext.succeeding(resp -> testContext.verify(() -> {
                    assertThat(resp.statusCode()).isEqualTo(500);
                    testContext.completeNow();
                })));
    }

    @Test
    @DisplayName("Test ODataProxyEndpoint sets Content-Type header")
    void testContentTypeHeader(VertxTestContext testContext) {
        String verticleName = EntityVerticle.getName(TestProxyEntityVerticle.class);
        TestProxyEntityVerticle verticle = proxyVerticle((query, context) -> {
            context.responseData().put(Endpoint.CONTENT_TYPE_HINT, "application/custom");
            return Future.succeededFuture(Buffer.buffer(JsonObject.of("result", "success").encode()));
        });

        deployVerticle(verticle).compose(v -> sendProxyRequest(verticleName, "", ""))
                .onComplete(testContext.succeeding(resp -> testContext.verify(() -> {
                    assertThat(resp.getHeader(CONTENT_TYPE)).isEqualTo("application/custom");
                    assertThat(resp.bodyAsJsonObject()).isEqualTo(JsonObject.of("result", "success"));
                    testContext.completeNow();
                })));
    }

    @Test
    @DisplayName("Test ODataProxyEndpoint with response headers")
    void testResponseHeaders(VertxTestContext testContext) {
        String verticleName = EntityVerticle.getName(TestProxyEntityVerticle.class);
        TestProxyEntityVerticle verticle = proxyVerticle((query, context) -> {
            context.responseData().put(Endpoint.RESPONSE_HEADERS_HINT,
                    Map.of("X-Custom-Header", "CustomValue", "X-Another-Header", "AnotherValue"));
            return Future.succeededFuture(Buffer.buffer("Response with headers"));
        });

        deployVerticle(verticle).compose(v -> sendProxyRequest(verticleName, "", ""))
                .onComplete(testContext.succeeding(resp -> testContext.verify(() -> {
                    assertThat(resp.getHeader("X-Custom-Header")).isEqualTo("CustomValue");
                    assertThat(resp.getHeader("X-Another-Header")).isEqualTo("AnotherValue");
                    testContext.completeNow();
                })));
    }

    @Test
    @DisplayName("Test ODataProxyEndpoint returns empty response for null result")
    void testNullResponse(VertxTestContext testContext) {
        String verticleName = EntityVerticle.getName(TestProxyEntityVerticle.class);
        TestProxyEntityVerticle verticle = proxyVerticle(
                (query, context) -> Future.succeededFuture((Buffer) null));

        deployVerticle(verticle).compose(v -> sendProxyRequest(verticleName, "", ""))
                .onComplete(testContext.succeeding(resp -> testContext.verify(() -> {
                    assertThat(resp.statusCode()).isEqualTo(200);
                    assertThat(resp.bodyAsString()).isAnyOf(null, "");
                    testContext.completeNow();
                })));
    }

    @Test
    @DisplayName("Test ODataProxyEndpoint with Buffer response")
    void testBufferResponse(VertxTestContext testContext) {
        String testData = "Binary data or file content";
        String verticleName = EntityVerticle.getName(TestProxyEntityVerticle.class);

        TestProxyEntityVerticle verticle = proxyVerticle(
                (query, context) -> Future.succeededFuture(Buffer.buffer(testData)));

        deployVerticle(verticle).compose(v -> sendProxyRequest(verticleName, "", ""))
                .onComplete(testContext.succeeding(resp -> testContext.verify(() -> {
                    assertThat(resp.statusCode()).isEqualTo(200);
                    assertThat(resp.bodyAsString()).isEqualTo(testData);
                    testContext.completeNow();
                })));
    }

    private static TestProxyEntityVerticle proxyVerticle(
            BiFunction<DataQuery, DataContext, Future<Buffer>> handler) {
        return new TestProxyEntityVerticle(handler);
    }

    @NeonBeeDeployable(namespace = NEONBEE_NAMESPACE, autoDeploy = false)
    private static class TestProxyEntityVerticle extends EntityVerticle {

        private final BiFunction<DataQuery, DataContext, Future<Buffer>> handler;

        TestProxyEntityVerticle(BiFunction<DataQuery, DataContext, Future<Buffer>> handler) {
            super();
            this.handler = handler;
        }

        @Override
        public Future<Set<FullQualifiedName>> entityTypeNames() {
            return Future.succeededFuture(Set.of());
        }

        @Override
        protected boolean supportsODataRequests() {
            return false;
        }

        @Override
        protected boolean supportsProxyRequests() {
            return true;
        }

        @Override
        protected Future<Buffer> retrieveProxyData(DataQuery query, DataContext context) {
            return handler.apply(query, context);
        }
    }

    private Future<HttpResponse<Buffer>> sendProxyRequest(String verticleName, String path, String query) {
        String uriPath = String.format("%s%s/%s%s", DEFAULT_BASE_PATH, NEONBEE_NAMESPACE, verticleName, path);
        if (!query.isEmpty()) {
            uriPath += "?" + query;
        }
        return createRequest(HttpMethod.GET, uriPath).send();
    }

    private Future<HttpResponse<Buffer>> sendProxyRequestWithBody(String verticleName, String path, String query,
            JsonObject body) {
        String uriPath = String.format("%s%s/%s%s", DEFAULT_BASE_PATH, NEONBEE_NAMESPACE, verticleName, path);
        if (!query.isEmpty()) {
            uriPath += "?" + query;
        }
        return createRequest(HttpMethod.POST, uriPath).sendJsonObject(body);
    }
}
