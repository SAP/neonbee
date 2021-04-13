package io.neonbee.test.endpoint.odata;

import static com.google.common.truth.Truth.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.neonbee.test.base.ODataEndpointTestBase;
import io.neonbee.test.base.ODataRequest;
import io.neonbee.test.endpoint.odata.verticle.TestService1EntityVerticle;
import io.vertx.core.http.HttpMethod;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxTestContext;

class ODataDeleteEntityTest extends ODataEndpointTestBase {
    private ODataRequest oDataRequest;

    @Override
    protected List<Path> provideEntityModels() {
        return List.of(TestService1EntityVerticle.getDeclaredEntityModel());
    }

    @BeforeEach
    void setUp(VertxTestContext testContext) {
        deployVerticle(new TestService1EntityVerticle()).onComplete(testContext.succeedingThenComplete());
        oDataRequest = new ODataRequest(TestService1EntityVerticle.TEST_ENTITY_SET_FQN).setMethod(HttpMethod.DELETE);
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.HOURS)
    @DisplayName("Respond with 405 METHOD NOT ALLOWED if the request was sent to the entity set url instead of a dedicated entity")
    void deleteEntitySetTest(VertxTestContext testContext) {
        requestOData(oDataRequest).onComplete(testContext.succeeding(response -> {
            testContext.verify(() -> assertThat(response.statusCode()).isEqualTo(405));
            testContext.completeNow();
        }));
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("Respond with 204 NO CONTENT if an entity was successfully deleted ")
    void deleteEntityTest(VertxTestContext testContext) {
        oDataRequest.setKey("id-5");

        requestOData(oDataRequest).onComplete(testContext.succeeding(response -> {
            testContext.verify(() -> assertThat(response.statusCode()).isEqualTo(204));
            testContext.completeNow();
        }));
    }
}
