package io.neonbee.test.endpoint.odata;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.test.endpoint.odata.verticle.TestService1EntityVerticle.EXPECTED_ENTITY_DATA_1;
import static io.neonbee.test.endpoint.odata.verticle.TestService1EntityVerticle.TEST_ENTITY_SET_FQN;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.google.common.net.MediaType;

import io.neonbee.test.base.ODataEndpointTestBase;
import io.neonbee.test.base.ODataRequest;
import io.neonbee.test.endpoint.odata.verticle.TestService1EntityVerticle;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxTestContext;

class ODataCreateEntityTest extends ODataEndpointTestBase {

    @Override
    protected List<Path> provideEntityModels() {
        return List.of(TestService1EntityVerticle.getDeclaredEntityModel());
    }

    @BeforeEach
    void setUp(VertxTestContext testContext) {
        deployVerticle(new TestService1EntityVerticle()).onComplete(testContext.succeedingThenComplete());
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("Respond with 204 NO CONTENT if an entity was successfully created")
    void createEntityTest(VertxTestContext testContext) {
        ODataRequest oDataRequest = new ODataRequest(TEST_ENTITY_SET_FQN).setMethod(HttpMethod.POST)
                .setBody(EXPECTED_ENTITY_DATA_1.toBuffer())
                .addHeader(HttpHeaders.CONTENT_TYPE.toString(), MediaType.JSON_UTF_8.toString());

        requestOData(oDataRequest).onComplete(testContext.succeeding(response -> {
            testContext.verify(() -> assertThat(response.statusCode()).isEqualTo(204));
            testContext.completeNow();
        }));
    }
}
