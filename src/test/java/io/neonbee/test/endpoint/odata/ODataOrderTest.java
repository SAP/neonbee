package io.neonbee.test.endpoint.odata;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.test.endpoint.odata.verticle.TestService1EntityVerticle.EXPECTED_ENTITY_DATA_1;
import static io.neonbee.test.endpoint.odata.verticle.TestService1EntityVerticle.EXPECTED_ENTITY_DATA_2;
import static io.neonbee.test.endpoint.odata.verticle.TestService1EntityVerticle.EXPECTED_ENTITY_DATA_3;
import static io.neonbee.test.endpoint.odata.verticle.TestService1EntityVerticle.EXPECTED_ENTITY_DATA_4;
import static io.neonbee.test.endpoint.odata.verticle.TestService1EntityVerticle.EXPECTED_ENTITY_DATA_5;
import static io.neonbee.test.endpoint.odata.verticle.TestService1EntityVerticle.EXPECTED_ENTITY_DATA_6;
import static io.neonbee.test.endpoint.odata.verticle.TestService1EntityVerticle.TEST_ENTITY_SET_FQN;
import static io.neonbee.test.endpoint.odata.verticle.TestService1EntityVerticle.getDeclaredEntityModel;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.neonbee.test.base.ODataEndpointTestBase;
import io.neonbee.test.base.ODataRequest;
import io.neonbee.test.endpoint.odata.verticle.TestService1EntityVerticle;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxTestContext;

class ODataOrderTest extends ODataEndpointTestBase {
    private ODataRequest request;

    @Override
    protected List<Path> provideEntityModels() {
        return List.of(getDeclaredEntityModel());
    }

    @BeforeEach
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    void setUp(VertxTestContext testContext) {
        request = new ODataRequest(TEST_ENTITY_SET_FQN);
        deployVerticle(new TestService1EntityVerticle()).onComplete(testContext.succeedingThenComplete());
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("Respond with 200 if the service is existing and has test entities ordered (ordered by multiple properties asc)")
    void existingEntitiesOrderedMultipleAscTest(VertxTestContext testContext) {
        request.setQuery(Map.of("$orderby", "PropertyString,PropertyInt32 asc"));

        assertODataEntitySet(requestOData(request), entities -> {
            assertThat(entities).containsExactly(EXPECTED_ENTITY_DATA_1, EXPECTED_ENTITY_DATA_3, EXPECTED_ENTITY_DATA_4,
                    EXPECTED_ENTITY_DATA_2, EXPECTED_ENTITY_DATA_5, EXPECTED_ENTITY_DATA_6).inOrder();
        }, testContext).onComplete(testContext.succeedingThenComplete());
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("Respond with 200 if the service is existing and has test entities ordered (ordered by multiple properties desc)")
    void existingEntitiesOrderedMultipleDescTest(VertxTestContext testContext) {
        request.setQuery(Map.of("$orderby", "PropertyString desc,PropertyInt32 desc"));

        assertODataEntitySet(requestOData(request), entities -> {
            assertThat(entities).containsExactly(EXPECTED_ENTITY_DATA_6, EXPECTED_ENTITY_DATA_5, EXPECTED_ENTITY_DATA_2,
                    EXPECTED_ENTITY_DATA_4, EXPECTED_ENTITY_DATA_3, EXPECTED_ENTITY_DATA_1).inOrder();
        }, testContext).onComplete(testContext.succeedingThenComplete());
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("Test ordering of Edm.Date properties in asc order")
    void orderingEdmDateAscTest(VertxTestContext testContext) {
        request.setQuery(Map.of("$orderby", "PropertyDate asc"));

        assertODataEntitySet(requestOData(request), entities -> {
            assertThat(entities).containsExactly(EXPECTED_ENTITY_DATA_5, EXPECTED_ENTITY_DATA_4, EXPECTED_ENTITY_DATA_3,
                    EXPECTED_ENTITY_DATA_2, EXPECTED_ENTITY_DATA_1, EXPECTED_ENTITY_DATA_6).inOrder();
        }, testContext).onComplete(testContext.succeedingThenComplete());
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("Test ordering of Edm.DateTimeOffset properties in desc order")
    void orderingEdmDateTimeOffsetDescTest(VertxTestContext testContext) {
        request.setQuery(Map.of("$orderby", "PropertyDateTime desc"));

        assertODataEntitySet(requestOData(request), entities -> {
            assertThat(entities).containsExactly(EXPECTED_ENTITY_DATA_6, EXPECTED_ENTITY_DATA_1, EXPECTED_ENTITY_DATA_2,
                    EXPECTED_ENTITY_DATA_3, EXPECTED_ENTITY_DATA_4, EXPECTED_ENTITY_DATA_5).inOrder();
        }, testContext).onComplete(testContext.succeedingThenComplete());
    }
}
