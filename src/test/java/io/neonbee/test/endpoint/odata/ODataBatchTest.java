package io.neonbee.test.endpoint.odata;

import static com.google.common.truth.Truth.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import org.apache.olingo.server.api.deserializer.batch.BatchOptions;
import org.apache.olingo.server.api.deserializer.batch.BatchRequestPart;
import org.apache.olingo.server.core.deserializer.batch.BatchParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.neonbee.test.base.ODataEndpointTestBase;
import io.neonbee.test.base.ODataRequest;
import io.neonbee.test.endpoint.odata.verticle.TestService1EntityVerticle;
import io.neonbee.test.helper.MultipartResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxTestContext;

class ODataBatchTest extends ODataEndpointTestBase {

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
        deployVerticle(new TestService1EntityVerticle())
                .onComplete(testContext.succeedingThenComplete());
    }

    @Test
    @DisplayName("Test serialization of ODataRequest to multipart batch body request")
    void testBatchRequestSerialization(VertxTestContext testContext) {
        testContext.verify(() -> {
            String boundary = UUID.randomUUID().toString();
            ODataRequest singleRequest = new ODataRequest(TestService1EntityVerticle.TEST_ENTITY_SET_FQN);
            ODataRequest batchRequest = new ODataRequest(TestService1EntityVerticle.TEST_ENTITY_SET_FQN)
                    .setBatch(boundary, singleRequest, singleRequest);

            BatchParser batchParser = new BatchParser();
            BatchOptions batchOptions = BatchOptions.with().isStrict(true).build();
            ByteArrayInputStream inputStream = new ByteArrayInputStream(batchRequest.getBody().getBytes());
            List<BatchRequestPart> partList =
                    batchParser.parseBatchRequest(inputStream, boundary, batchOptions);
            assertThat(partList).hasSize(2);

            testContext.completeNow();
        });
    }

    @ParameterizedTest(name = "{index}: with key {0}")
    @MethodSource("withValidKeys")
    @DisplayName("Read single existing entity via batch request")
    void testReadExistingEntity(String id, VertxTestContext testContext) {
        ODataRequest readRequest = new ODataRequest(TestService1EntityVerticle.TEST_ENTITY_SET_FQN).setKey(id);
        ODataRequest batchRequest = new ODataRequest(TestService1EntityVerticle.TEST_ENTITY_SET_FQN)
                .setBatch(readRequest);

        assertODataBatch(requestOData(batchRequest), multipartResponse -> {
            List<MultipartResponse.Part> parts = multipartResponse.getParts();
            assertThat(parts).hasSize(1);

            MultipartResponse.Part part = parts.get(0);
            JsonObject acutal = part.getPayload().toJsonObject();
            assertThat(acutal.getString("KeyPropertyString")).isEqualTo(id);

            testContext.completeNow();
        }, testContext);
    }

    @ParameterizedTest(name = "{index}: with key {0}")
    @MethodSource("withInvalidKeys")
    @DisplayName("Read single unkown entity via batch request")
    void testReadUnknownEntity(String id, VertxTestContext testContext) {
        ODataRequest readRequest = new ODataRequest(TestService1EntityVerticle.TEST_ENTITY_SET_FQN).setKey(id);
        ODataRequest batchRequest =
                new ODataRequest(TestService1EntityVerticle.TEST_ENTITY_SET_FQN).setBatch(readRequest);

        assertODataBatch(requestOData(batchRequest), multipartResponse -> {
            // verify correct amount of response parts
            List<MultipartResponse.Part> parts = multipartResponse.getParts();
            assertThat(parts).hasSize(1);

            // verify expected status code of response parts
            MultipartResponse.Part part = parts.get(0);
            assertThat(part.getStatusCode()).isEqualTo(404);

            testContext.completeNow();
        }, testContext);
    }

    @Test
    @DisplayName("Test entity filtering")
    void testFilterOnly(VertxTestContext testContext) {
        ODataRequest filterRequest = new ODataRequest(TestService1EntityVerticle.TEST_ENTITY_SET_FQN)
                .setQuery(Map.of("$filter", "KeyPropertyString in ('id-1', 'id-6')"));
        ODataRequest batchRequest = new ODataRequest(TestService1EntityVerticle.TEST_ENTITY_SET_FQN)
                .setBatch(filterRequest);

        assertODataBatch(requestOData(batchRequest), multipartResponse -> {
            // verify correct amount of response parts
            List<MultipartResponse.Part> parts = multipartResponse.getParts();
            assertThat(parts).hasSize(1);
            MultipartResponse.Part part = parts.get(0);

            // verify correct amount of results
            JsonArray values = part.getPayload().toJsonObject().getJsonArray("value");
            assertThat(values.size()).isEqualTo(2);

            testContext.completeNow();
        }, testContext);
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

        assertODataBatch(requestOData(batchRequest), multipartResponse -> {
            // verify correct amount of response parts
            List<MultipartResponse.Part> parts = multipartResponse.getParts();
            assertThat(parts).hasSize(2);
            MultipartResponse.Part[] partsArray = parts.toArray(MultipartResponse.Part[]::new);
            MultipartResponse.Part filterResponse = partsArray[0];
            MultipartResponse.Part readResponse = partsArray[1];

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
        }, testContext);
    }
}
