package io.neonbee.internal.verticle;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.internal.verticle.HealthCheckVerticle.SHARED_MAP_KEY;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.parallel.Isolated;

import io.neonbee.NeonBeeOptions;
import io.neonbee.data.DataQuery;
import io.neonbee.data.DataRequest;
import io.neonbee.internal.deploy.DeployableVerticle;
import io.neonbee.internal.registry.Registry;
import io.neonbee.test.base.DataVerticleTestBase;
import io.neonbee.test.helper.ReflectionHelper;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxTestContext;

@Isolated("Some of the methods in this test class run clustered and use the FakeClusterManager for it. The FakeClusterManager uses a static state and can therefore not be run with other clustered tests.")
class HealthCheckVerticleTest extends DataVerticleTestBase {
    @Override
    protected void adaptOptions(TestInfo testInfo, NeonBeeOptions.Mutable options) {
        if ("testSharedMap".equals(testInfo.getTestMethod().get().getName())) {
            options.setClustered(true);
        }
    }

    @Test
    @DisplayName("should respond with the check result of the registered check")
    void testRetrieveData(VertxTestContext testContext) {
        DataRequest req = new DataRequest(HealthCheckVerticle.QUALIFIED_NAME, new DataQuery());
        Future<JsonArray> response = requestData(req);

        assertData(response, resp -> {
            JsonObject firstCheck = resp.getJsonObject(0);
            assertThat(firstCheck.containsKey("id")).isTrue();
            assertThat(firstCheck.containsKey("status")).isTrue();
            assertThat(firstCheck.containsKey("outcome")).isFalse();
        }, testContext).onComplete(testContext.succeedingThenComplete());
    }

    @Test
    @DisplayName("should register itself in shared map")
    void testSharedMap(VertxTestContext testContext) throws NoSuchFieldException, IllegalAccessException {
        Registry<String> registry = ReflectionHelper.getValueOfPrivateField(getNeonBee().getHealthCheckRegistry(),
                "healthVerticleRegistry");

        registry.get(SHARED_MAP_KEY)
                .onComplete(testContext.succeeding(qualifiedNamesOrNull -> testContext.verify(() -> {
                    String expectedName = HealthCheckVerticle.QUALIFIED_NAME;
                    assertThat(qualifiedNamesOrNull).containsExactly(expectedName);
                    testContext.completeNow();
                })));
    }

    @Test
    @DisplayName("should not register in shared map when non-clustered mode")
    void testStartNonClustered(Vertx vertx, VertxTestContext testContext) throws NoSuchFieldException,
            IllegalAccessException {
        Registry<String> registry = ReflectionHelper.getValueOfPrivateField(getNeonBee().getHealthCheckRegistry(),
                "healthVerticleRegistry");

        DeployableVerticle.fromVerticle(vertx, new HealthCheckVerticle(), new JsonObject())
                .compose(deployable -> deployable.deploy(getNeonBee()))
                .compose(v -> registry.getKeys())
                .onComplete(testContext.succeeding(keys -> testContext.verify(() -> assertThat(keys).isEmpty())))
                .onComplete(testContext.succeedingThenComplete());
    }
}
