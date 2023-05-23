package io.neonbee.test.endpoint.odata;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.test.endpoint.odata.verticle.TestService3EntityVerticle.ENTITY_DATA_1;
import static io.neonbee.test.endpoint.odata.verticle.TestService3EntityVerticle.ENTITY_URL;
import static io.neonbee.test.endpoint.odata.verticle.TestService3EntityVerticle.TEST_ENTITY_SET_FQN;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.google.common.net.MediaType;

import io.neonbee.test.base.ODataEndpointTestBase;
import io.neonbee.test.base.ODataRequest;
import io.neonbee.test.endpoint.odata.verticle.TestService3EntityVerticle;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxTestContext;

class ODataCreateEntityTest extends ODataEndpointTestBase {

    @Override
    protected List<Path> provideEntityModels() {
        return List.of(TestService3EntityVerticle.getDeclaredEntityModel());
    }

    @BeforeEach
    void setUp(VertxTestContext testContext) {
        deployVerticle(new TestService3EntityVerticle()).onComplete(testContext.succeedingThenComplete());
    }

    @Test
    @DisplayName("Respond with 201 CREATED with Location header if an entity was successfully created")
    void createEntityTest(VertxTestContext testContext) {
        ODataRequest oDataRequest = new ODataRequest(TEST_ENTITY_SET_FQN).setMethod(HttpMethod.POST)
                .setBody(ENTITY_DATA_1.toBuffer())
                .addHeader(HttpHeaders.CONTENT_TYPE.toString(), MediaType.JSON_UTF_8.toString());

        requestOData(oDataRequest).onComplete(testContext.succeeding(response -> {
            testContext.verify(() -> {
                assertThat(response.statusCode()).isEqualTo(201);
                assertThat(response.getHeader("Location")).isEqualTo(ENTITY_URL);
                JsonObject body = response.body().toJsonObject();
                assertThat(body.getString("ID")).isEqualTo(ENTITY_DATA_1.getString("ID"));
                assertThat(body.getString("name")).isEqualTo(ENTITY_DATA_1.getString("name"));
                assertThat(body.getString("description")).isEqualTo(ENTITY_DATA_1.getString("description"));
            });
            testContext.completeNow();
        }));
    }

    @Test
    @DisplayName("Respond with 400")
    void createEntityTestWithWrongPayload(VertxTestContext testContext) {
        ODataRequest oDataRequest = new ODataRequest(TEST_ENTITY_SET_FQN).setMethod(HttpMethod.POST)
                .setBody(Buffer.buffer("wrong JSON"))
                .addHeader(HttpHeaders.CONTENT_TYPE.toString(), MediaType.JSON_UTF_8.toString());

        requestOData(oDataRequest).onComplete(testContext.succeeding(response -> {
            testContext.verify(() -> {
                assertThat(response.statusCode()).isEqualTo(400);
            });
            testContext.completeNow();
        }));
    }
}
