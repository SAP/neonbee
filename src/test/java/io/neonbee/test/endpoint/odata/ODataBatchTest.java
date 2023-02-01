package io.neonbee.test.endpoint.odata;

import com.google.common.collect.Range;
import com.google.common.truth.Truth;
import io.neonbee.test.base.BatchContext;
import io.neonbee.test.base.ODataBatchRequestBody;
import io.neonbee.test.base.ODataBatchResponseParser;
import io.neonbee.test.base.ODataBatchResponsePart;
import io.neonbee.test.base.ODataEndpointTestBase;
import io.neonbee.test.base.ODataRequest;
import io.neonbee.test.endpoint.odata.verticle.TestService1EntityVerticle;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.junit5.VertxTestContext;
import org.apache.olingo.server.api.deserializer.batch.BatchDeserializerException;
import org.apache.olingo.server.api.deserializer.batch.BatchOptions;
import org.apache.olingo.server.api.deserializer.batch.BatchRequestPart;
import org.apache.olingo.server.core.deserializer.batch.BatchParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ODataBatchTest extends ODataEndpointTestBase {

    private String boundary;

    static Stream<Arguments> withValidKeys() {
        return Stream.of(Arguments.arguments("id-1"));
    }

    static Stream<Arguments> withInvalidKeys() {
        return Stream.of(Arguments.arguments("1337"));
    }

    @Override
    protected List<Path> provideEntityModels() {
        return List.of(TestService1EntityVerticle.getDeclaredEntityModel());
    }

    @BeforeEach
    void setUp(VertxTestContext testContext) {
        boundary = "batch_" + UUID.randomUUID();

        deployVerticle(new TestService1EntityVerticle())
            .onComplete(testContext.succeedingThenComplete());
    }

    @Test
    @DisplayName("Test serialization of ODataRequest to multipart batch body request")
    void testBatchRequestSerialization(VertxTestContext testContext) {
        BatchContext batchContext = new BatchContext(boundary);
        ODataRequest oDataRequest = new ODataRequest(TestService1EntityVerticle.TEST_ENTITY_SET_FQN);
        ODataBatchRequestBody body = new ODataBatchRequestBody(new ODataBatchRequestBody.Request(oDataRequest), new ODataBatchRequestBody.Request(oDataRequest));

        Buffer finalContent = body.writeTo(Buffer.buffer(), batchContext);
        try {
            BatchParser batchParser = new BatchParser();
            BatchOptions batchOptions = BatchOptions.with().isStrict(true).build();
            ByteArrayInputStream inputStream = new ByteArrayInputStream(finalContent.getBytes());
            List<BatchRequestPart> partList = batchParser.parseBatchRequest(inputStream, boundary, batchOptions);
            Truth.assertThat(partList).hasSize(2);
            testContext.completeNow();
        } catch (BatchDeserializerException ex) {
            testContext.failNow(ex);
        }
    }

    @ParameterizedTest(name = "{index}: with key {0}")
    @MethodSource("withValidKeys")
    @DisplayName("Read single existing entity via batch request")
    void testReadExistingEntity(String id, VertxTestContext testContext) {
        ODataRequest oDataRequest = new ODataRequest(TestService1EntityVerticle.TEST_ENTITY_SET_FQN).setKey(id);
        sendBatchRequest(oDataRequest)
            .onComplete(testContext.succeeding(response -> {
                // verify response status
                Truth.assertThat(response.statusCode()).isIn(Range.closed(200, 299));

                parseBatchResponse(response).onSuccess(parts -> {
                        // verify correct amount of response parts
                        Truth.assertThat(parts).hasSize(1);

                        // verify expected status code of response parts
                        ODataBatchResponsePart part = parts.stream().findFirst().get();
                        Truth.assertThat(part.getStatusCode()).isEqualTo(200);

                        // verify expected result value
                        JsonObject actualData = part.getPayload().toJsonObject();
                        Truth.assertThat(actualData.getString("KeyPropertyString")).isEqualTo(id);
                        testContext.completeNow();
                    })
                    .onFailure(testContext::failNow);
            }));
    }

    @ParameterizedTest(name = "{index}: with key {0}")
    @MethodSource("withInvalidKeys")
    @DisplayName("Read single unkown entity via batch request")
    void testReadUnknownEntity(String id, VertxTestContext testContext) {
        ODataRequest oDataRequest = new ODataRequest(TestService1EntityVerticle.TEST_ENTITY_SET_FQN).setKey(id);
        sendBatchRequest(oDataRequest)
            .onComplete(testContext.succeeding(response -> {
                // verify response status
                Truth.assertThat(response.statusCode()).isEqualTo(202);

                parseBatchResponse(response).onSuccess(parts -> {
                        // verify correct amount of response parts
                        Truth.assertThat(parts).hasSize(1);

                        // verify expected status code of response parts
                        ODataBatchResponsePart part = parts.stream().findFirst().get();
                        Truth.assertThat(part.getStatusCode()).isEqualTo(404);
                        testContext.completeNow();
                    })
                    .onFailure(testContext::failNow);
            }));
    }

    @Test
    @DisplayName("Test entity filtering")
    void testFilterOnly(VertxTestContext testContext) {
        ODataRequest filterRequest = new ODataRequest(TestService1EntityVerticle.TEST_ENTITY_SET_FQN).setQuery(Map.of("$filter", "KeyPropertyString in ('id-1', 'id-6')"));
        sendBatchRequest(filterRequest)
            .onComplete(testContext.succeeding(response -> {
                // verify response status
                Truth.assertThat(response.statusCode()).isIn(Range.closed(200, 299));

                parseBatchResponse(response).onSuccess(parts -> {
                        // verify correct amount of response parts
                        Truth.assertThat(parts).hasSize(1);
                        ODataBatchResponsePart part = parts.stream().findFirst().get();

                        // verify correct amount of results
                        JsonArray values = part.getPayload().toJsonObject().getJsonArray("value");
                        Truth.assertThat(values.size()).isEqualTo(2);

                        testContext.completeNow();
                    })
                    .onFailure(testContext::failNow);
                testContext.completeNow();
            }));
    }

    @ParameterizedTest(name = "{index}: with key {0} for {1}")
    @MethodSource("withValidKeys")
    @DisplayName("Verify filter and read operations via one batch request return the same results")
    void testFilterAndRead(String id, VertxTestContext testContext) {
        ODataRequest filterRequest = new ODataRequest(TestService1EntityVerticle.TEST_ENTITY_SET_FQN).setQuery(Map.of("$filter", "KeyPropertyString in ('" + id + "')"));
        ODataRequest readRequest = new ODataRequest(TestService1EntityVerticle.TEST_ENTITY_SET_FQN).setKey(id);
        sendBatchRequest(filterRequest, readRequest)
            .onComplete(testContext.succeeding(response -> {
                // verify response status
                Truth.assertThat(response.statusCode()).isIn(Range.closed(200, 299));

                parseBatchResponse(response).onComplete(testContext.succeeding(parts -> {
                    // verify correct amount of response parts
                    Truth.assertThat(parts).hasSize(2);
                    ODataBatchResponsePart[] partsArray = parts.toArray(ODataBatchResponsePart[]::new);
                    ODataBatchResponsePart filterResponse = partsArray[0],
                        readResponse = partsArray[1];

                    // verify status code of response parts
                    Truth.assertThat(filterResponse.getStatusCode()).isEqualTo(200);
                    Truth.assertThat(readResponse.getStatusCode()).isEqualTo(filterResponse.getStatusCode());

                    // verify correct result values get returned
                    JsonArray filteredValues = filterResponse.getPayload().toJsonObject().getJsonArray("value");
                    Truth.assertThat(filteredValues.size()).isEqualTo(1);
                    JsonObject valueFromFilter = filteredValues.getJsonObject(0),
                        valueFromRead = readResponse.getPayload().toJsonObject();
                    String idFromFilter = valueFromFilter.getString("KeyPropertyString"),
                        idFromRead = valueFromRead.getString("KeyPropertyString");
                    Truth.assertThat(idFromFilter).isEqualTo(idFromRead);

                    testContext.completeNow();
                }));
            }));
    }

    private Future<HttpResponse<Buffer>> sendBatchRequest(ODataRequest... batch) {
        return Future.future(promise -> {
            List<ODataBatchRequestBody.Request> partsList = Arrays.stream(batch)
                .map(ODataBatchRequestBody.Request::new)
                .collect(Collectors.toList());
            sendBatchRequest(new ODataBatchRequestBody(partsList))
                .onSuccess(promise::complete)
                .onFailure(promise::fail);
        });
    }

    private Future<HttpResponse<Buffer>> sendBatchRequest(ODataBatchRequestBody body) {
        BatchContext batchContext = new BatchContext(boundary);
        Future<ODataRequest> future = Future.future(promise -> {
            Buffer requestBody = body.writeTo(Buffer.buffer(), batchContext);
            ODataRequest batchRequest = ODataRequest.batch(TestService1EntityVerticle.TEST_ENTITY_SET_FQN.getNamespace(), batchContext);
            promise.complete(batchRequest.setBody(requestBody));
        });
        return future.compose(this::requestOData);
    }

    private Future<Collection<ODataBatchResponsePart>> parseBatchResponse(HttpResponse<Buffer> response) {
        return Future.future(promise -> {
            ODataBatchResponseParser parser = new ODataBatchResponseParser(response.body());
            try {
                Collection<ODataBatchResponsePart> parts = parser.parse();
                promise.complete(parts);
            } catch (IOException e) {
                promise.fail(e);
            }
        });
    }
}
