package io.neonbee.test.endpoint.odata;

import static com.google.common.truth.Truth.assertThat;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.edm.FullQualifiedName;
import org.apache.olingo.commons.api.format.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.google.common.net.MediaType;

import io.neonbee.data.DataAdapter;
import io.neonbee.data.DataContext;
import io.neonbee.data.DataQuery;
import io.neonbee.entity.EntityVerticle;
import io.neonbee.entity.EntityWrapper;
import io.neonbee.internal.codec.EntityWrapperMessageCodec;
import io.neonbee.test.base.ODataEndpointTestBase;
import io.neonbee.test.base.ODataRequest;
import io.neonbee.test.endpoint.odata.verticle.TestService1EntityVerticle;
import io.neonbee.test.endpoint.odata.verticle.TestServiceCompoundKeyEntityVerticle;
import io.neonbee.test.helper.EntityHelper;
import io.vertx.core.Future;
import io.vertx.core.Verticle;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxTestContext;

class ODataUpdateEntityTest extends ODataEndpointTestBase {
    private ODataRequest oDataRequest;

    @Override
    protected List<Path> provideEntityModels() {
        return List.of(TestService1EntityVerticle.getDeclaredEntityModel(),
                TestServiceCompoundKeyEntityVerticle.getDeclaredEntityModel());
    }

    @BeforeEach
    void setUp() {
        oDataRequest = new ODataRequest(TestService1EntityVerticle.TEST_ENTITY_SET_FQN)
                .addHeader(HttpHeaders.CONTENT_TYPE.toString(), MediaType.JSON_UTF_8.toString());
    }

    static Stream<Arguments> withHTTPMethods() {
        return Stream.of(Arguments.of(HttpMethod.PUT), Arguments.of(HttpMethod.PATCH));
    }

    @ParameterizedTest(name = "{index}: with Method {0}")
    @MethodSource("withHTTPMethods")
    @DisplayName("Respond with 405 METHOD NOT ALLOWED if the HTTP request was sent to the entity set url instead of a dedicated entity")
    void updateEntitySetTest(HttpMethod method, VertxTestContext testContext) {
        oDataRequest.setMethod(method).setBody(TestService1EntityVerticle.EXPECTED_ENTITY_DATA_1.toBuffer());

        deployVerticle(new TestService1EntityVerticle()).compose(v -> requestOData(oDataRequest))
                .onComplete(testContext.succeeding(response -> {
                    testContext.verify(() -> assertThat(response.statusCode()).isEqualTo(405));
                    testContext.completeNow();
                }));
    }

    @ParameterizedTest(name = "{index}: with Method {0}")
    @MethodSource("withHTTPMethods")
    @DisplayName("Respond with 400 BAD REQUEST if the request body is invalid JSON")
    void updateEntityInvalidBodyTest(HttpMethod method, VertxTestContext testContext) {
        oDataRequest.setMethod(method).setBody(Buffer.buffer("I am not a JSON String")).setKey("id-1");

        deployVerticle(new TestService1EntityVerticle()).compose(v -> requestOData(oDataRequest))
                .onComplete(testContext.succeeding(response -> {
                    testContext.verify(() -> assertThat(response.statusCode()).isEqualTo(400));
                    testContext.completeNow();
                }));
    }

    static Stream<Arguments> withHTTPMethodsAndBody() {
        JsonObject putBody = TestService1EntityVerticle.EXPECTED_ENTITY_DATA_1.copy();
        putBody.remove("KeyPropertyString");
        JsonObject patchBody = new JsonObject().put("PropertyString", "New String");

        return Stream.of(Arguments.of(HttpMethod.PUT, putBody), Arguments.of(HttpMethod.PATCH, patchBody));
    }

    @ParameterizedTest(name = "{index}: with Method {0}")
    @MethodSource("withHTTPMethodsAndBody")
    @DisplayName("Respond with 204 NO CONTENT if an entity was successfully updated")
    void updateEntityTest(HttpMethod method, JsonObject body, VertxTestContext testContext) {
        oDataRequest.setMethod(method).setBody(body.toBuffer()).setKey("id-1");

        Checkpoint cp = testContext.checkpoint();
        Verticle dummy = getDummyEntityVerticle(TestService1EntityVerticle.TEST_ENTITY_SET_FQN, ew -> {
            if (HttpMethod.PUT.equals(method)) {
                EntityHelper.compareLazy(EntityHelper.createEntity(body), ew.getEntity(), testContext,
                        "KeyPropertyString", "PropertyDate", "PropertyDateTime");
            } else {
                String changedProperty = "PropertyString";
                assertThat(body.getString(changedProperty))
                        .isEqualTo(ew.getEntity().getProperty("PropertyString").getValue());
            }
        }, testContext);

        deployVerticle(dummy).compose(v -> requestOData(oDataRequest)).onComplete(testContext.succeeding(response -> {
            testContext.verify(() -> assertThat(response.statusCode()).isEqualTo(204));
            cp.flag();
        }));
    }

    @Test
    @DisplayName("Respond with 204 NO CONTENT if an entity with a compound key was successfully updated")
    void updateEntityWithCompoundKeyViaPutTest(VertxTestContext testContext) {
        String updatedName = "Updated Car";
        JsonObject expectedContent =
                TestServiceCompoundKeyEntityVerticle.createEntityData(0, "2020-02-02", updatedName, null);
        Entity expectedEntity = EntityHelper.createEntity(expectedContent);

        Checkpoint cp = testContext.checkpoint();
        Verticle verticle = getDummyEntityVerticle(TestServiceCompoundKeyEntityVerticle.TEST_ENTITY_SET_FQN,
                ew -> EntityHelper.compareLazy(expectedEntity, ew.getEntity(), testContext, "date"), testContext);

        deployVerticle(verticle).compose(s -> {
            JsonObject bodyContent = new JsonObject().put("name", updatedName);
            oDataRequest = new ODataRequest(TestServiceCompoundKeyEntityVerticle.TEST_ENTITY_SET_FQN)
                    .setMethod(HttpMethod.PUT).setBody(bodyContent.toBuffer())
                    .setKey(Map.of("ID", 0L, "date", LocalDate.of(2020, 2, 2)))
                    .addHeader(HttpHeaders.CONTENT_TYPE.toString(), ContentType.APPLICATION_JSON.toContentTypeString());

            return requestOData(oDataRequest).onComplete(testContext.succeeding(response -> {
                testContext.verify(() -> assertThat(response.statusCode()).isEqualTo(204));
                cp.flag();
            }));
        });
    }

    private EntityVerticle getDummyEntityVerticle(FullQualifiedName fqn, Consumer<EntityWrapper> verifyPayload,
            VertxTestContext testContext) {
        Checkpoint dummyWasCalled = testContext.checkpoint();
        return createDummyEntityVerticle(fqn).withDataAdapter(new DataAdapter<EntityWrapper>() {

            @Override
            public Future<EntityWrapper> updateData(DataQuery query, DataContext context) {
                dummyWasCalled.flag();
                EntityWrapper ew =
                        new EntityWrapperMessageCodec(getNeonBee().getVertx()).decodeFromWire(0, query.getBody());
                testContext.verify(() -> verifyPayload.accept(ew));
                return Future.succeededFuture(new EntityWrapper(fqn, (Entity) null));
            }
        });
    }
}
