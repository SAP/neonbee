package io.neonbee.endpoint.odatav4;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.endpoint.odatav4.ODataProxyEndpoint.CONFIG_RAW_BATCH_PROCESSING;
import static io.neonbee.endpoint.odatav4.ODataV4Endpoint.CONFIG_URI_CONVERSION;
import static io.neonbee.endpoint.odatav4.ODataV4Endpoint.UriConversion.STRICT;
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
import io.neonbee.NeonBeeDeployable;
import io.neonbee.config.EndpointConfig;
import io.neonbee.config.ServerConfig;
import io.neonbee.data.DataContext;
import io.neonbee.data.DataQuery;
import io.neonbee.data.DataVerticle;
import io.neonbee.endpoint.odatav4.rawbatch.RawBatchDecision;
import io.neonbee.endpoint.odatav4.rawbatch.RawBatchResult;
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
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.junit5.VertxTestContext;

/**
 * Tests for raw batch processing: interception of ODataProxy $batch requests and forwarding to a configured
 * DataVerticle, plus decision-based delegation back to default ODataProxy processing.
 */
class ODataProxyRawBatchTest extends ODataEndpointTestBase {

    private static final FullQualifiedName TEST_ENTITY = new FullQualifiedName("test.Service", "TestEntity");

    private static final String RAW_BATCH_VERTICLE = "test/_RawBatchVerticle";

    private static final String SCHEMA_NAMESPACE = "test.Service";

    @Override
    protected List<Path> provideEntityModels() {
        return List.of(TEST_RESOURCES.resolveRelated("TestService4.csn"));
    }

    @Override
    protected WorkingDirectoryBuilder provideWorkingDirectoryBuilder(TestInfo testInfo, VertxTestContext testContext) {
        return super.provideWorkingDirectoryBuilder(testInfo, testContext).setCustomTask(root -> {
            DeploymentOptions opts = WorkingDirectoryBuilder.readDeploymentOptions(ServerVerticle.class, root);
            JsonObject additionalConfig = new JsonObject()
                    .put(CONFIG_URI_CONVERSION, STRICT.toString())
                    .put(CONFIG_RAW_BATCH_PROCESSING,
                            new JsonObject().put(SCHEMA_NAMESPACE, RAW_BATCH_VERTICLE));
            EndpointConfig epc = new EndpointConfig()
                    .setType(ODataProxyEndpoint.class.getName())
                    .setEnabled(true)
                    .setBasePath("/odataproxy/")
                    .setAdditionalConfig(additionalConfig);
            ServerConfig sc = new ServerConfig(opts.getConfig()).setEndpointConfigs(List.of(epc));
            opts.setConfig(sc.toJson());
            WorkingDirectoryBuilder.writeDeploymentOptions(ServerVerticle.class, opts, root);
        });
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    @DisplayName("$batch request for service in rawBatchProcessing is forwarded to DataVerticle and response is 1:1")
    void rawBatchInterceptedAndForwarded(VertxTestContext testContext) {
        String batchPath = String.format("/odataproxy/%s/$batch", SCHEMA_NAMESPACE);
        String batchBody = "--batch_123\r\n"
                + "Content-Type: application/http\r\n"
                + "Content-Transfer-Encoding: binary\r\n"
                + "\r\n"
                + "GET TestEntity HTTP/1.1\r\n"
                + "\r\n"
                + "\r\n"
                + "--batch_123--\r\n";
        MultiMap headers = MultiMap.caseInsensitiveMultiMap()
                .add("Content-Type", "multipart/mixed; boundary=batch_123");

        deployVerticle(new RawBatchEchoVerticle())
                .compose(v -> send(batchPath, HttpMethod.POST, headers, MultiMap.caseInsensitiveMultiMap(),
                        Buffer.buffer(batchBody)))
                .onComplete(testContext.succeeding(resp -> testContext.verify(() -> {
                    assertThat(resp.statusCode()).isEqualTo(200);
                    assertThat(resp.bodyAsString()).isEqualTo(RawBatchEchoVerticle.RESPONSE_BODY);
                    testContext.completeNow();
                })));
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    @DisplayName("DataVerticle can delegate to default ODataProxy batch via RawBatchDecision")
    void rawBatchDelegatesToDefaultProcessing(VertxTestContext testContext) {
        String batchPath = String.format("/odataproxy/%s/$batch", SCHEMA_NAMESPACE);
        String batchRequestBody = "--batch_id\r\n"
                + "Content-Type:application/http\r\n"
                + "Content-Transfer-Encoding:binary\r\n"
                + "\r\n"
                + "GET TestEntity HTTP/1.1\r\n"
                + "Accept:application/json\r\n"
                + "\r\n"
                + "\r\n"
                + "--batch_id--\r\n";
        MultiMap headers = MultiMap.caseInsensitiveMultiMap()
                .add("Content-Type", "multipart/mixed; boundary=batch_id");

        deployVerticle(new ODataProxyEntityVerticle())
                .compose(v -> deployVerticle(new RawBatchDelegateToDefaultVerticle()))
                .compose(v -> send(batchPath, HttpMethod.POST, headers, MultiMap.caseInsensitiveMultiMap(),
                        Buffer.buffer(batchRequestBody)))
                .onComplete(testContext.succeeding(resp -> testContext.verify(() -> {
                    assertThat(resp.statusCode()).isEqualTo(202);
                    testContext.completeNow();
                })));
    }

    private Future<HttpResponse<Buffer>> send(String requestURI, HttpMethod method, MultiMap headers, MultiMap query,
            Buffer body) {
        NeonBee neonBee = getNeonBee();
        Vertx vertx = neonBee.getVertx();
        return readServerConfig(neonBee).compose(config -> {
            int port = config.getPort();
            WebClientOptions clientOpts = new WebClientOptions().setDefaultHost("localhost").setDefaultPort(port);
            var httpRequest = WebClient.create(vertx, clientOpts).request(method, requestURI);
            httpRequest.putHeaders(headers);
            httpRequest.queryParams().addAll(query);
            return Optional.ofNullable(body).map(httpRequest::sendBuffer).orElse(httpRequest.send());
        });
    }

    /**
     * DataVerticle that handles raw batch by returning a fixed body (to verify 1:1 forwarding). Raw $batch is sent as
     * POST, so the verticle is invoked via createData, not retrieveData.
     */
    @NeonBeeDeployable(namespace = "test", autoDeploy = false)
    public static class RawBatchEchoVerticle extends DataVerticle<RawBatchResult> {
        static final String RESPONSE_BODY = "raw-batch-response";

        @Override
        public String getName() {
            return "_RawBatchVerticle";
        }

        @Override
        public Future<RawBatchResult> createData(DataQuery query, DataContext context) {
            context.responseData().put(DataContext.STATUS_CODE_HINT, 200);
            return succeededFuture(RawBatchResult.buffer(Buffer.buffer(RESPONSE_BODY)));
        }
    }

    /**
     * DataVerticle that delegates to default ODataProxy batch processing. Raw $batch is sent as POST, so the verticle
     * is invoked via createData, not retrieveData.
     */
    @NeonBeeDeployable(namespace = "test", autoDeploy = false)
    public static class RawBatchDelegateToDefaultVerticle extends DataVerticle<RawBatchResult> {
        @Override
        public String getName() {
            return "_RawBatchVerticle";
        }

        @Override
        public Future<RawBatchResult> createData(DataQuery query, DataContext context) {
            return succeededFuture(RawBatchResult.decision(RawBatchDecision.DELEGATE_TO_DEFAULT));
        }
    }

    /**
     * Entity verticle for default batch path (returns entity data).
     */
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
