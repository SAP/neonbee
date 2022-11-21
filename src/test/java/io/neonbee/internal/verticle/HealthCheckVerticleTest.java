package io.neonbee.internal.verticle;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.internal.verticle.HealthCheckVerticle.SHARED_MAP_KEY;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.parallel.Isolated;

import io.neonbee.NeonBee;
import io.neonbee.NeonBeeOptions;
import io.neonbee.data.DataQuery;
import io.neonbee.data.DataRequest;
import io.neonbee.internal.deploy.DeployableVerticle;
import io.neonbee.test.base.DataVerticleTestBase;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Timeout;
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
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
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
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    void testSharedMap(VertxTestContext testContext) {
        getNeonBee().getAsyncMap().get(SHARED_MAP_KEY)
                .onComplete(testContext.succeeding(qualifiedNamesOrNull -> testContext.verify(() -> {
                    String expectedName = HealthCheckVerticle.QUALIFIED_NAME;
                    assertThat((JsonArray) qualifiedNamesOrNull).containsExactly(expectedName);
                    testContext.completeNow();
                })));
    }

    @Test
    @DisplayName("should not register in shared map when non-clustered mode")
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    void testStartNonClustered(Vertx vertx, VertxTestContext testContext) {
        NeonBee neonBeeSpy = spy(getNeonBee());

        DeployableVerticle.fromVerticle(vertx, new HealthCheckVerticle(), new JsonObject())
                .compose(deployable -> deployable.deploy(neonBeeSpy))
                .onComplete(testContext.succeeding(r -> testContext.verify(() -> {
                    verify(neonBeeSpy, times(0)).getAsyncMap();
                }))).onComplete(testContext.succeedingThenComplete());
    }
}
