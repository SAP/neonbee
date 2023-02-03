package io.neonbee.test.endpoint.odata;

import static com.google.common.truth.Truth.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

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

import com.google.common.collect.Range;

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

class ODataBatchTest extends ODataEndpointTestBase {

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
        ODataRequest singleRequest = new ODataRequest(TestService1EntityVerticle.TEST_ENTITY_SET_FQN);
        ODataRequest batchRequest = new ODataRequest(TestService1EntityVerticle.TEST_ENTITY_SET_FQN)
                .setBatch(singleRequest, singleRequest);

        try {
            BatchParser batchParser = new BatchParser();
            BatchOptions batchOptions = BatchOptions.with().isStrict(true).build();
            ByteArrayInputStream inputStream = new ByteArrayInputStream(batchRequest.getBody().getBytes());
            List<BatchRequestPart> partList =
                    batchParser.parseBatchRequest(inputStream, boundary, batchOptions);
            assertThat(partList).hasSize(2);
            testContext.completeNow();
        } catch (BatchDeserializerException ex) {
            testContext.failNow(ex);
        }
    }

    @ParameterizedTest(name = "{index}: with key {0}")
    @MethodSource("withValidKeys")
    @DisplayName("Read single existing entity via batch request")
    void testReadExistingEntity(String id, VertxTestContext testContext) {
        ODataRequest readRequest = new ODataRequest(TestService1EntityVerticle.TEST_ENTITY_SET_FQN).setKey(id);
        ODataRequest batchRequest = new ODataRequest(TestService1EntityVerticle.TEST_ENTITY_SET_FQN)
                .setBatch(readRequest);
        requestOData(batchRequest).onComplete(testContext.succeeding(response -> { // verify response status
            assertThat(response.statusCode()).isIn(Range.closed(200, 299));
            parseBatchResponse(response).onSuccess(parts -> { // verify correct amount of response parts
                assertThat(parts).hasSize(1);
                // verify expected status code of response parts
                ODataBatchResponsePart part = parts.stream().findFirst().get();
                assertThat(part.getStatusCode()).isEqualTo(200);
                // verify expected result value
                JsonObject actualData = part.getPayload().toJsonObject();
                assertThat(actualData.getString("KeyPropertyString")).isEqualTo(id);
                testContext.completeNow();
            })
                    .onFailure(testContext::failNow);
        }));
    }

    @ParameterizedTest(name = "{index}: with key {0}")
    @MethodSource("withInvalidKeys")
    @DisplayName("Read single unkown entity via batch request")
    void testReadUnknownEntity(String id, VertxTestContext testContext) {
        ODataRequest readRequest = new ODataRequest(TestService1EntityVerticle.TEST_ENTITY_SET_FQN).setKey(id);
        ODataRequest batchRequest =
                new ODataRequest(TestService1EntityVerticle.TEST_ENTITY_SET_FQN).setBatch(readRequest);
        requestOData(batchRequest).onComplete(testContext.succeeding(response -> {
            // verify response status
            assertThat(response.statusCode()).isEqualTo(202);
            parseBatchResponse(response).onSuccess(parts -> {
                // verify correct amount of response parts
                assertThat(parts).hasSize(1);

                // verify expected status code of response parts
                ODataBatchResponsePart part = parts.stream().findFirst().get();
                assertThat(part.getStatusCode()).isEqualTo(404);
                testContext.completeNow();
            }).onFailure(testContext::failNow);
        }));
    }

    @Test
    @DisplayName("Test entity filtering")
    void testFilterOnly(VertxTestContext testContext) {
        ODataRequest filterRequest = new ODataRequest(TestService1EntityVerticle.TEST_ENTITY_SET_FQN)
                .setQuery(Map.of("$filter", "KeyPropertyString in ('id-1', 'id-6')"));
        ODataRequest batchRequest = new ODataRequest(TestService1EntityVerticle.TEST_ENTITY_SET_FQN)
                .setBatch(filterRequest);
        requestOData(batchRequest)
                .onComplete(testContext.succeeding(response -> {
                    // verify response status
                    assertThat(response.statusCode()).isIn(Range.closed(200, 299));

                    parseBatchResponse(response).onSuccess(parts -> {
                        // verify correct amount of response parts
                        assertThat(parts).hasSize(1);
                        ODataBatchResponsePart part = parts.stream().findFirst().get();

                        // verify correct amount of results
                        JsonArray values = part.getPayload().toJsonObject().getJsonArray("value");
                        assertThat(values.size()).isEqualTo(2);

                        testContext.completeNow();
                    })
                            .onFailure(testContext::failNow);
                    testContext.completeNow();
                }));
    }

    @ParameterizedTest(name = "{index}: with key {0}")
    @MethodSource("withValidKeys")
    @DisplayName("Verify filter and read operations via one batch request return the same results")
    void testFilterAndRead(String id, VertxTestContext testContext) {
        ODataRequest filterRequest = new ODataRequest(TestService1EntityVerticle.TEST_ENTITY_SET_FQN)
                .setQuery(Map.of("$filter", "KeyPropertyString in ('" + id + "')"));
        ODataRequest readRequest = new ODataRequest(TestService1EntityVerticle.TEST_ENTITY_SET_FQN).setKey(id);
        ODataRequest batchRequest = new ODataRequest(TestService1EntityVerticle.TEST_ENTITY_SET_FQN)
                .setBatch(filterRequest, readRequest);

        requestOData(batchRequest)
                .onComplete(testContext.succeeding(response -> {
                    // verify response status
                    assertThat(response.statusCode()).isIn(Range.closed(200, 299));

                    parseBatchResponse(response).onComplete(testContext.succeeding(parts -> {
                        // verify correct amount of response parts
                        assertThat(parts).hasSize(2);
                        ODataBatchResponsePart[] partsArray = parts.toArray(ODataBatchResponsePart[]::new);
                        ODataBatchResponsePart filterResponse = partsArray[0];
                        ODataBatchResponsePart readResponse = partsArray[1];

                        // verify status code of response parts
                        assertThat(filterResponse.getStatusCode()).isEqualTo(200);
                        assertThat(readResponse.getStatusCode()).isEqualTo(filterResponse.getStatusCode());

                        // verify correct result values get returned
                        JsonArray filteredValues = filterResponse.getPayload().toJsonObject().getJsonArray("value");
                        assertThat(filteredValues.size()).isEqualTo(1);
                        JsonObject valueFromFilter = filteredValues.getJsonObject(0);
                        JsonObject valueFromRead = readResponse.getPayload().toJsonObject();
                        String idFromFilter = valueFromFilter.getString("KeyPropertyString");
                        String idFromRead = valueFromRead.getString("KeyPropertyString");
                        assertThat(idFromFilter).isEqualTo(idFromRead);

                        testContext.completeNow();
                    }));
                }));
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
