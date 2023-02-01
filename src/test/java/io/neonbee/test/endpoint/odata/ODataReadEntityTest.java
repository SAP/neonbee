package io.neonbee.test.endpoint.odata;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.internal.helper.StringHelper.EMPTY;
import static io.neonbee.test.endpoint.odata.verticle.TestService1EntityVerticle.EXPECTED_ENTITY_DATA_2;
import static io.neonbee.test.endpoint.odata.verticle.TestService1EntityVerticle.EXPECTED_ENTITY_DATA_4;
import static io.neonbee.test.endpoint.odata.verticle.TestService1EntityVerticle.EXPECTED_ENTITY_DATA_5;
import static io.neonbee.test.endpoint.odata.verticle.TestService1EntityVerticle.TEST_ENTITY_SET_FQN;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.apache.olingo.commons.api.edm.FullQualifiedName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.google.common.base.Strings;

import io.neonbee.test.base.ODataEndpointTestBase;
import io.neonbee.test.base.ODataRequest;
import io.neonbee.test.endpoint.odata.verticle.TestService1EntityVerticle;
import io.neonbee.test.endpoint.odata.verticle.TestService3EntityVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.junit5.VertxTestContext;

class ODataReadEntityTest extends ODataEndpointTestBase {
    private ODataRequestMod oDataRequest;

    @Override
    protected List<Path> provideEntityModels() {
        return List.of(TestService1EntityVerticle.getDeclaredEntityModel(),
                TestService3EntityVerticle.getDeclaredEntityModel());
    }

    @BeforeEach
    void setUp(VertxTestContext testContext) {
        CompositeFuture
                .all(deployVerticle(new TestService1EntityVerticle()), deployVerticle(new TestService3EntityVerticle()))
                .onComplete(testContext.succeedingThenComplete());
        oDataRequest = new ODataRequestMod(TEST_ENTITY_SET_FQN);
    }

    static Stream<Arguments> withInvalidKeys() {
        return Stream.of(Arguments.of("(1337)", "wrong type"), Arguments.of("(+123+)", "invalid format"));
    }

    @ParameterizedTest(name = "{index}: with key {0} ({1})")
    @MethodSource("withInvalidKeys")
    @DisplayName("Respond with 400 if an entity of an existing service is requested")
    @SuppressWarnings("PMD.UnusedFormalParameter") // Required for display name
    void testInvalidKeys(String id, String reason, VertxTestContext testContext) {
        requestOData(oDataRequest.setKeyPredicate(id)).onComplete(testContext.succeeding(response -> {
            assertThat(response.statusCode()).isEqualTo(400);
            JsonObject jsonResponse = response.bodyAsJsonObject().getJsonObject("error");
            assertThat(jsonResponse.getString("code")).isNull();
            assertThat(jsonResponse.getString("message")).isEqualTo("The key value '' is invalid.");
            testContext.completeNow();
        }));
    }

    static Stream<Arguments> withValidKeys() {
        return Stream.of(Arguments.of("id-1", EXPECTED_ENTITY_DATA_2), Arguments.of("id.3", EXPECTED_ENTITY_DATA_4));
    }

    @ParameterizedTest(name = "{index}: with key {0}")
    @MethodSource("withValidKeys")
    @DisplayName("Respond with 200 if the service exists and has entity")
    void testFilter(String id, JsonObject expected, VertxTestContext testContext) {
        assertODataEntity(requestOData(oDataRequest.setKey(id)), expected, testContext)
                .onComplete(testContext.succeedingThenComplete());
    }

    @Test
    @DisplayName("Respond with the value of the requests attribute of the entity")
    void readEntityPropertyTest(VertxTestContext testContext) {
        assertODataEntity(requestOData(oDataRequest.setKey("id-4").setProperty("PropertyInt32")),
                new JsonObject().put("value", 4), testContext).onComplete(testContext.succeedingThenComplete());
    }

    @Test
    @DisplayName("Respond with 200 if the service is existing and has entity with id")
    void existingEntity5WithUrlEncodedSingleQuotesTest(VertxTestContext testContext) {
        oDataRequest.setKeyPredicate("(%27id-4%27)");
        assertODataEntity(requestOData(oDataRequest), EXPECTED_ENTITY_DATA_5, testContext)
                .onComplete(testContext.succeedingThenComplete());
    }

    @Test
    @DisplayName("Respond with 200 if the service is existing and has entity with id")
    void existingEntity5WithUrlEncodedParenthesisAndSingleQuotesTest(VertxTestContext testContext) {
        oDataRequest.setKeyPredicate("%28%27id-4%27%29");
        assertODataEntity(requestOData(oDataRequest), EXPECTED_ENTITY_DATA_5, testContext)
                .onComplete(testContext.succeedingThenComplete());
    }

    @Test
    @DisplayName("Respond with 200 if the service is existing and has entity with (Edm.Int32) id")
    void existingEntity205Test(VertxTestContext testContext) {
        oDataRequest = new ODataRequestMod(TestService3EntityVerticle.TEST_ENTITY_SET_FQN);
        oDataRequest.setKey(205L);
        assertODataEntity(requestOData(oDataRequest), TestService3EntityVerticle.ENTITY_DATA_3, testContext)
                .onComplete(testContext.succeedingThenComplete());
    }

    static class ODataRequestMod extends ODataRequest {

        private String keyPredicate;

        ODataRequestMod(FullQualifiedName entity) {
            super(entity);
        }

        @Override
        public ODataRequest setMethod(HttpMethod method) {
            super.setMethod(method);
            return this;
        }

        @Override
        public ODataRequest setBody(Buffer body) {
            super.setBody(body);
            return this;
        }

        @Override
        public ODataRequest setMetadata() {
            super.setMetadata();
            return this;
        }

        @Override
        public ODataRequest setCount() {
            super.setCount();
            return this;
        }

        @Override
        public ODataRequestMod setKey(String id) {
            super.setKey(id);
            return this;
        }

        @Override
        public ODataRequest setKey(long id) {
            super.setKey(id);
            return this;
        }

        @Override
        public ODataRequest setKey(Map<String, Object> compositeKey) {
            super.setKey(compositeKey);
            return this;
        }

        @Override
        public ODataRequest setProperty(String propertyName) {
            super.setProperty(propertyName);
            return this;
        }

        @Override
        public ODataRequest addQueryParam(String key, String value) {
            super.addQueryParam(key, value);
            return this;
        }

        @Override
        public ODataRequest setQuery(Map<String, String> parameters) {
            super.setQuery(parameters);
            return this;
        }

        @Override
        public ODataRequest addHeader(String key, String value) {
            super.addHeader(key, value);
            return this;
        }

        @Override
        public ODataRequest setHeaders(MultiMap headers) {
            super.setHeaders(headers);
            return this;
        }

        @Override
        public ODataRequest interceptRequest(Consumer<HttpRequest<Buffer>> rawRequest) {
            super.interceptRequest(rawRequest);
            return this;
        }

        @Override
        protected String getUri() {
            if (Strings.isNullOrEmpty(keyPredicate)) {
                return super.getUri();
            }
            return Strings.isNullOrEmpty(entity.getNamespace()) ? EMPTY
                    : entity.getNamespace() + '/' + entity.getName() + keyPredicate;
        }

        public ODataRequestMod setKeyPredicate(String key) {
            this.keyPredicate = key;
            return this;
        }
    }
}
