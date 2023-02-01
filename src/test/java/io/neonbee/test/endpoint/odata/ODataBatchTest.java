package io.neonbee.test.endpoint.odata;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.neonbee.test.base.ODataBatchRequest;
import io.neonbee.test.base.ODataEndpointTestBase;
import io.neonbee.test.base.ODataRequest;
import io.neonbee.test.endpoint.odata.verticle.TestService1EntityVerticle;
import io.neonbee.test.helper.MultipartResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxTestContext;

class ODataBatchTest extends ODataEndpointTestBase {

    static Stream<Arguments> provideReadArguments() {
        return Stream.of(arguments("id-1", "existing entity", (Consumer<MultipartResponse.Part>) part -> {
            JsonObject actual = part.getBody().toJsonObject();
            assertThat(actual.getString("KeyPropertyString")).isEqualTo("id-1");
        }), arguments("1337", "unknown entity",
                (Consumer<MultipartResponse.Part>) part -> assertThat(part.getStatusCode()).isEqualTo(404)));
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

    @ParameterizedTest(name = "{index}: with key {0} testing {1}")
    @MethodSource("provideReadArguments")
    @DisplayName("Read single entity via batch request")
    void testReadSingleEntity(String id, String testCase, Consumer<MultipartResponse.Part> assertHandler,
            VertxTestContext testContext) {
        ODataBatchRequest batchRequest = new ODataBatchRequest(TestService1EntityVerticle.TEST_ENTITY_SET_FQN)
                .addRequests(new ODataRequest(TestService1EntityVerticle.TEST_ENTITY_SET_FQN).setKey(id));

        assertODataBatch(requestOData(batchRequest), multipartResponse -> {
            List<MultipartResponse.Part> parts = multipartResponse.getParts();
            assertThat(parts).hasSize(1);

            MultipartResponse.Part part = parts.get(0);
            assertHandler.accept(part);

            testContext.completeNow();
        }, testContext);
    }

    @Test
    @DisplayName("Test entity filtering")
    void testFilterOnly(VertxTestContext testContext) {
        ODataBatchRequest batchRequest = new ODataBatchRequest(TestService1EntityVerticle.TEST_ENTITY_SET_FQN)
                .addRequests(new ODataRequest(TestService1EntityVerticle.TEST_ENTITY_SET_FQN)
                        .setQuery(Map.of("$filter", "KeyPropertyString in ('id-1', 'id-6')")));

        assertODataBatch(requestOData(batchRequest), multipartResponse -> {
            // verify correct amount of response parts
            List<MultipartResponse.Part> parts = multipartResponse.getParts();
            assertThat(parts).hasSize(1);

            MultipartResponse.Part part = parts.get(0);

            // verify correct amount of results
            JsonArray values = part.getBody().toJsonObject().getJsonArray("value");
            assertThat(values).hasSize(2);

            testContext.completeNow();
        }, testContext);
    }

    @Test
    @DisplayName("Test batch request containing more than one part")
    void testMultipleParts(VertxTestContext testContext) {
        String key = "id-1";
        ODataRequest filterRequest = new ODataRequest(TestService1EntityVerticle.TEST_ENTITY_SET_FQN)
                .setQuery(Map.of("$filter", "KeyPropertyString in ('" + key + "')"));
        ODataRequest readRequest = new ODataRequest(TestService1EntityVerticle.TEST_ENTITY_SET_FQN).setKey(key);
        ODataBatchRequest batchRequest = new ODataBatchRequest(TestService1EntityVerticle.TEST_ENTITY_SET_FQN)
                .addRequests(filterRequest, readRequest);

        assertODataBatch(requestOData(batchRequest), multipartResponse -> {
            // verify correct amount of response parts
            List<MultipartResponse.Part> parts = multipartResponse.getParts();
            assertThat(parts).hasSize(2);
            MultipartResponse.Part filterResponse = parts.get(0);
            MultipartResponse.Part readResponse = parts.get(1);

            // verify status code of response parts
            assertThat(filterResponse.getStatusCode()).isEqualTo(200);
            assertThat(readResponse.getStatusCode()).isEqualTo(200);

            // verify correct result values get returned
            JsonArray filteredValues = filterResponse.getBody().toJsonObject().getJsonArray("value");
            assertThat(filteredValues).hasSize(1);

            JsonObject valueFromFilter = filteredValues.getJsonObject(0);
            JsonObject valueFromRead = readResponse.getBody().toJsonObject();
            String idFromFilter = valueFromFilter.getString("KeyPropertyString");
            String idFromRead = valueFromRead.getString("KeyPropertyString");
            assertThat(idFromFilter).isEqualTo(idFromRead);

            testContext.completeNow();
        }, testContext);
    }
}
