package io.neonbee.test.endpoint.odata.verticle;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.endpoint.odatav4.internal.olingo.processor.ProcessorHelper.ODATA_COUNT_SIZE_KEY;
import static io.neonbee.test.endpoint.odata.verticle.TestService1EntityVerticle.TEST_ENTITY_SET_FQN;
import static io.neonbee.test.endpoint.odata.verticle.TestService1EntityVerticle.getDeclaredEntityModel;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.neonbee.entity.EntityWrapper;
import io.neonbee.test.base.ODataEndpointTestBase;
import io.neonbee.test.base.ODataRequest;
import io.vertx.core.Future;
import io.vertx.junit5.VertxTestContext;

class ODataCountTest extends ODataEndpointTestBase {

    @Override
    protected List<Path> provideEntityModels() {
        return List.of(getDeclaredEntityModel());
    }

    private Future<Void> deployVerticleWithResponseHintCount(int count) {
        return deployVerticle(createDummyEntityVerticle(TEST_ENTITY_SET_FQN).withDynamicResponse((query, context) -> {
            context.responseData().put(ODATA_COUNT_SIZE_KEY, count);
            return new EntityWrapper(TEST_ENTITY_SET_FQN, List.of());
        })).mapEmpty();
    }

    @Test
    @DisplayName("Respond with 200 and includes inline count read from the response hint")
    void entitiesWithInlineCountFromHintTest(VertxTestContext testContext) {
        deployVerticleWithResponseHintCount(42).onSuccess(v -> {
            Map<String, String> countQuery = Map.of("$count", "true");
            assertOData(requestOData(new ODataRequest(TEST_ENTITY_SET_FQN).setQuery(countQuery)),
                    body -> assertThat(body.toJsonObject().getMap()).containsAtLeast("@odata.count",
                            42),
                    testContext).onComplete(testContext.succeedingThenComplete());
        }).onFailure(testContext::failNow);
    }

    @Test
    @DisplayName("Test /$count returns the value passed via response hint")
    void countEntitiesViaCountFromHintTest(VertxTestContext testContext) {
        deployVerticleWithResponseHintCount(1337)
                .onSuccess(v -> assertOData(requestOData(new ODataRequest(TEST_ENTITY_SET_FQN).setCount()), "1337",
                        testContext).onComplete(testContext.succeedingThenComplete()))
                .onFailure(testContext::failNow);
    }
}
