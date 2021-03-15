package io.neonbee.test.endpoint.odata;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.internal.Helper.EMPTY;
import static io.neonbee.internal.handler.ODataEndpointHandler.UriConversion.CDS;
import static io.neonbee.internal.handler.ODataEndpointHandler.UriConversion.LOOSE;
import static io.neonbee.internal.handler.ODataEndpointHandler.UriConversion.STRICT;
import static io.neonbee.test.endpoint.odata.verticle.TestService1EntityVerticle.EXPECTED_ENTITY_DATA_1;
import static io.neonbee.test.endpoint.odata.verticle.TestService1EntityVerticle.EXPECTED_ENTITY_DATA_2;
import static io.neonbee.test.endpoint.odata.verticle.TestService1EntityVerticle.EXPECTED_ENTITY_DATA_3;
import static io.neonbee.test.endpoint.odata.verticle.TestService1EntityVerticle.EXPECTED_ENTITY_DATA_4;
import static io.neonbee.test.endpoint.odata.verticle.TestService1EntityVerticle.EXPECTED_ENTITY_DATA_5;
import static io.neonbee.test.endpoint.odata.verticle.TestService1EntityVerticle.TEST_ENTITY_SET_FQN;
import static io.neonbee.test.endpoint.odata.verticle.TestService1EntityVerticle.getDeclaredEntityModel;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.olingo.commons.api.edm.FullQualifiedName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import io.neonbee.entity.EntityVerticle;
import io.neonbee.internal.handler.ODataEndpointHandler.UriConversion;
import io.neonbee.internal.verticle.ServerVerticle;
import io.neonbee.test.base.ODataEndpointTestBase;
import io.neonbee.test.base.ODataRequest;
import io.neonbee.test.endpoint.odata.verticle.TestService1EntityVerticle;
import io.neonbee.test.helper.WorkingDirectoryBuilder;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxTestContext;

class ODataReadEntitiesTest extends ODataEndpointTestBase {

    @Override
    protected List<Path> provideEntityModels() {
        return List.of(getDeclaredEntityModel());
    }

    @Override
    protected WorkingDirectoryBuilder provideWorkingDirectoryBuilder(TestInfo testInfo, VertxTestContext testContext) {
        return super.provideWorkingDirectoryBuilder(testInfo, testContext).setCustomTask(root -> {
            // the server verticle should either use strict, cds or loose URI mapping
            String testMethodName = testInfo.getTestMethod().map(Method::getName).orElse(EMPTY);
            UriConversion uriConversion = STRICT;
            if (testMethodName.contains("LooseUriConversion")) {
                uriConversion = LOOSE;
            } else if (testMethodName.contains("CDSUriConversion")) {
                uriConversion = CDS;
            }

            DeploymentOptions opts = WorkingDirectoryBuilder.readDeploymentOptions(ServerVerticle.class, root);
            opts.getConfig().put("endpoints",
                    new JsonObject().put("odata", new JsonObject().put("uriConversion", uriConversion.toString())));
            WorkingDirectoryBuilder.writeDeploymentOptions(ServerVerticle.class, opts, root);
        });
    }

    @BeforeEach
    void setUp(VertxTestContext testContext) {
        deployVerticle(new TestService1EntityVerticle()).onComplete(testContext.succeedingThenComplete());
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("Respond with 404 if an non existing entity set is requested")
    void nonExistingEntityTest(VertxTestContext testContext) {
        FullQualifiedName notExistingEntities =
                new FullQualifiedName(TEST_ENTITY_SET_FQN.getNamespace(), "NotExistingEntities");

        new ODataRequest(notExistingEntities).send(getNeonBee())
                .onComplete(testContext.succeeding(response -> testContext.verify(() -> {
                    JsonObject jsonResponse = response.bodyAsJsonObject().getJsonObject("error");
                    assertThat(jsonResponse.getString("code")).isNull();
                    assertThat(jsonResponse.getString("message")).isEqualTo(
                            "Cannot find EntitySet, Singleton, ActionImport or FunctionImport with name 'NotExistingEntities'.");
                    testContext.completeNow();
                })));
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("Respond with 200 if the service is existing and has test entities")
    void existingEntitiesTest(VertxTestContext testContext) {
        assertODataEntitySetContainsExactly(
                requestOData(new ODataRequest(TEST_ENTITY_SET_FQN)), List.of(EXPECTED_ENTITY_DATA_1,
                        EXPECTED_ENTITY_DATA_2, EXPECTED_ENTITY_DATA_3, EXPECTED_ENTITY_DATA_4, EXPECTED_ENTITY_DATA_5),
                testContext).onComplete(testContext.succeedingThenComplete());
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("Respond with 200 and the correct count of entities")
    void existingEntitiesCountTest(VertxTestContext testContext) {
        assertOData(requestOData(new ODataRequest(TEST_ENTITY_SET_FQN).setCount()), "5", testContext)
                .onComplete(testContext.succeedingThenComplete());
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("Test /$count returns 0 if no entities are found")
    void countNoEntitiesFoundTest(VertxTestContext testContext) {
        EntityVerticle dummy = createDummyEntityVerticle(TEST_ENTITY_SET_FQN).withStaticResponse(List.of());
        undeployVerticles(TestService1EntityVerticle.class).compose(v -> deployVerticle(dummy)).compose(
                v -> assertOData(requestOData(new ODataRequest(TEST_ENTITY_SET_FQN).setCount()), "0", testContext))
                .onComplete(testContext.succeedingThenComplete());
    }

    @Test
    @Timeout(value = 3, timeUnit = TimeUnit.SECONDS)
    @DisplayName("Respond with 200 and the correct count of entities with filter query")
    void existingEntitiesCountWithFilterTest(VertxTestContext testContext) {
        Map<String, String> filterQuery = Map.of("$filter", "KeyPropertyString in ('id.3', 'id-1')");
        assertOData(requestOData(new ODataRequest(TEST_ENTITY_SET_FQN).setCount().setQuery(filterQuery)), "2",
                testContext).onComplete(testContext.succeedingThenComplete());
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("Respond with 200 and the correct count of entities with filter query when loose URL mapping work is used")
    // Please note that the test method name contains "LooseUri" and therefore the behaviour is different.
    void existingEntitiesCountWithFilterCDSUriConversionTest(VertxTestContext testContext) {
        Map<String, String> filterQuery = Map.of("$filter", "KeyPropertyString in ('id.3', 'id-1')");
        FullQualifiedName looseFQN = new FullQualifiedName("test-service1", "AllPropertiesNullable");
        assertOData(requestOData(new ODataRequest(looseFQN).setCount().setQuery(filterQuery)), "2", testContext)
                .onComplete(testContext.succeedingThenComplete());
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("Respond with 200 and the correct count of entities without filter query when loose URL mapping work is used")
    // Please note that the test method name contains "LooseUri" and therefore the behavior is different.
    void existingEntitiesCountWithoutFilterLooseUriConversionTest(VertxTestContext testContext) {
        FullQualifiedName looseFQN = new FullQualifiedName("io-neonbee-test-test-service1", "AllPropertiesNullable");
        assertOData(requestOData(new ODataRequest(looseFQN).setCount()), "5", testContext)
                .onComplete(testContext.succeedingThenComplete());
    }
}
