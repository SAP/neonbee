package io.neonbee.endpoint.odatav4;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.endpoint.odatav4.ODataV4Endpoint.CONFIG_URI_CONVERSION;
import static io.neonbee.endpoint.odatav4.ODataV4Endpoint.UriConversion.LOOSE;
import static io.neonbee.test.helper.ResourceHelper.TEST_RESOURCES;
import static io.vertx.core.Future.succeededFuture;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.olingo.commons.api.edm.FullQualifiedName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;

import io.neonbee.NeonBee;
import io.neonbee.config.EndpointConfig;
import io.neonbee.config.ServerConfig;
import io.neonbee.data.DataContext;
import io.neonbee.data.DataQuery;
import io.neonbee.endpoint.odatav4.ODataV4Endpoint.UriConversion;
import io.neonbee.entity.AbstractEntityVerticle;
import io.neonbee.internal.verticle.ServerVerticle;
import io.neonbee.test.base.ODataEndpointTestBase;
import io.neonbee.test.helper.WorkingDirectoryBuilder;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.junit5.VertxTestContext;

class ODataProxyEndpointTest2 extends ODataEndpointTestBase {

    private static final FullQualifiedName TEST_ENTITY = new FullQualifiedName("test.Service", "TestEntity");

    private static final UriConversion URI_CONVERSION = LOOSE;

    @Override
    protected List<Path> provideEntityModels() {
        return List.of(TEST_RESOURCES.resolveRelated("TestService4.csn"));
    }

    @Override
    protected WorkingDirectoryBuilder provideWorkingDirectoryBuilder(TestInfo testInfo, VertxTestContext testContext) {
        return super.provideWorkingDirectoryBuilder(testInfo, testContext).setCustomTask(root -> {
            // the server verticle should either use strict, cds or loose URI mapping

            DeploymentOptions opts = WorkingDirectoryBuilder.readDeploymentOptions(ServerVerticle.class, root);
            EndpointConfig epc = new EndpointConfig()
                    .setType(ODataProxyEndpoint.class.getName())
                    .setEnabled(true).setBasePath("/odataproxy/")
                    .setAdditionalConfig(new JsonObject().put(CONFIG_URI_CONVERSION, URI_CONVERSION.toString()));
            ServerConfig sc = new ServerConfig(opts.getConfig()).setEndpointConfigs(List.of(epc));
            opts.setConfig(sc.toJson());
            WorkingDirectoryBuilder.writeDeploymentOptions(ServerVerticle.class, opts, root);
        });
    }

    @Test
    void testEntityRequest(VertxTestContext testContext) {
        deployVerticle(new ODataProxyEntityVerticle())
                .compose(v -> send(String.format("/%s/%s/%s", "odataproxy", "test", TEST_ENTITY.getName()),
                        HttpMethod.GET,
                        MultiMap.caseInsensitiveMultiMap(),
                        MultiMap.caseInsensitiveMultiMap(),
                        null))
                .onComplete(testContext.succeeding(resp -> testContext.verify(() -> {
                    // ensure we received a successful response
                    assertThat(resp.statusCode()).isEqualTo(200);
                    testContext.completeNow();
                })));
    }

    @Test
    void testBatchRequest(VertxTestContext testContext) {
        // Simulate the batch request from the given curl: one GET on ContactSet with a filter, wrapped in
        // multipart/mixed
        String batchRequestBody = "--batch_id-1763633769299-80\r\n"
                + "Content-Type:application/http\r\n"
                + "Content-Transfer-Encoding:binary\r\n"
                + "\r\n"
                + "GET TestEntity HTTP/1.1\r\n"
                + "Accept:application/json;odata.metadata=minimal;IEEE754Compatible=true\r\n"
                + "Accept-Language:en-GB\r\n"
                + "Content-Type:application/json;charset=UTF-8;IEEE754Compatible=true\r\n"
                + "\r\n"
                + "\r\n"
                + "--batch_id-1763633769299-80--\r\n";

        MultiMap headers = MultiMap.caseInsensitiveMultiMap()
                .add("Accept", "multipart/mixed")
                .add("Cache-Control", "no-cache")
                .add("Content-Type", "multipart/mixed; boundary=batch_id-1763633769299-80")
                .add("MIME-Version", "1.0")
                .add("OData-MaxVersion", "4.0")
                .add("OData-Version", "4.0");

        deployVerticle(new ODataProxyEntityVerticle())
                .compose(v -> send(String.format("/%s/%s/%s", "odataproxy", "test", "$batch"),
                        HttpMethod.POST,
                        headers,
                        MultiMap.caseInsensitiveMultiMap(),
                        Buffer.buffer(batchRequestBody)))
                .onComplete(testContext.succeeding(resp -> testContext.verify(() -> {
                    // ensure we received a successful response
                    assertThat(resp.statusCode()).isEqualTo(202);
                    testContext.completeNow();
                })));
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.MINUTES)
    @DisplayName("metadata request returns EDMX document")
    void testMetadata(VertxTestContext testContext) {

        Future<HttpResponse<Buffer>> request = send(String.format("/%s/%s/%s", "odataproxy", "test", "$metadata"),
                HttpMethod.GET,
                MultiMap.caseInsensitiveMultiMap(),
                MultiMap.caseInsensitiveMultiMap(),
                null);

        assertOData(request,
                body -> assertThat(body.toString()).contains("<edmx:Edmx"), testContext)
                        .onComplete(testContext.succeedingThenComplete());
    }

    /**
     * Constructs an {@link HttpRequest} based on the underlying OData request and sends it.
     *
     * @return A {@link Future} with the {@link HttpResponse} which is received from sending the OData request.
     */
    public Future<HttpResponse<Buffer>> send(
            String requestURI,
            HttpMethod method,
            MultiMap headers,
            MultiMap query,
            Buffer body) {
        NeonBee neonBee = getNeonBee();
        Vertx vertx = neonBee.getVertx();
        return readServerConfig(neonBee).compose(config -> {
            int port = config.getPort();

            WebClientOptions clientOpts = new WebClientOptions().setDefaultHost("localhost").setDefaultPort(port);
            HttpRequest<Buffer> httpRequest = WebClient.create(vertx, clientOpts).request(method, requestURI);

            httpRequest.putHeaders(headers);
            httpRequest.queryParams().addAll(query);

            return Optional.ofNullable(body).map(httpRequest::sendBuffer).orElse(httpRequest.send());
        });
    }

    public static class ODataProxyEntityVerticle extends AbstractEntityVerticle<Buffer> {

        @Override
        public Future<Buffer> retrieveData(DataQuery query, DataContext context) {
            context.responseData().put(DataContext.STATUS_CODE_HINT, 200);
            return succeededFuture(JsonObject.of("key", "value").toBuffer());
        }

        @Override
        public Future<Set<FullQualifiedName>> entityTypeNames() {
            return succeededFuture(Set.of(TEST_ENTITY));
        }
    }
}
