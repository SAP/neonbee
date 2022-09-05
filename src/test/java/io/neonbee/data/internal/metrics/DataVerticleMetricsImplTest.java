package io.neonbee.data.internal.metrics;

import static com.google.common.truth.Truth.assertThat;
import static io.vertx.core.http.HttpMethod.GET;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import io.neonbee.config.NeonBeeConfig;
import io.neonbee.data.DataVerticle;
import io.neonbee.test.base.DataVerticleTestBase;
import io.neonbee.test.helper.WorkingDirectoryBuilder;
import io.vertx.core.CompositeFuture;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxTestContext;

class DataVerticleMetricsImplTest extends DataVerticleTestBase {

    @Override
    protected WorkingDirectoryBuilder provideWorkingDirectoryBuilder(TestInfo testInfo, VertxTestContext testContext) {
        NeonBeeConfig config = new NeonBeeConfig();
        config.getMetricsConfig().setEnabled(true);
        return super.provideWorkingDirectoryBuilder(testInfo, testContext).setNeonBeeConfig(config);
    }

    @BeforeEach
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    void setUp(VertxTestContext testContext) {
        DeploymentOptions deploymentOptions = new DeploymentOptions();
        deploymentOptions.setConfig(new JsonObject().put(DataVerticle.CONFIG_METRICS_KEY,
                new JsonObject().put(ConfiguredDataVerticleMetrics.ENABLED, true)));

        CompositeFuture
                .all(deployVerticle(new TestSourceDataVerticle(), deploymentOptions),
                        deployVerticle(new TestRequireDataVerticle(), deploymentOptions))
                .onComplete(testContext.succeedingThenComplete());
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    void testMetricsOnPrometheusEndpoint(VertxTestContext testContext) throws Exception {
        String expected = "TestRequireDataVerticle[TestSourceDataVerticle content]";
        assertDataEquals(requestData(TestRequireDataVerticle.QUALIFIED_NAME), expected, testContext)
                .compose(v -> createRequest(GET, "/metrics").send()).onFailure(testContext::failNow)
                .onSuccess(resp -> testContext.verify(() -> {
                    assertThat(resp.statusCode()).isEqualTo(200);
                    assertThat(resp.bodyAsString())
                            .contains("request_data_timer_test_TestSourceDataVerticle_seconds_count{query=\"\",} ");
                    assertThat(resp.bodyAsString())
                            .contains("request_data_timer_test_TestSourceDataVerticle_seconds_sum{query=\"\",} ");
                    assertThat(resp.bodyAsString())
                            .contains("request_data_timer_test_TestSourceDataVerticle_seconds_max{query=\"\",} ");

                    assertThat(resp.bodyAsString()).contains(
                            "request_data_counter_test_TestSourceDataVerticle_total{query=\"\",succeeded=\"true\",} 1.0");
                    assertThat(resp.bodyAsString())
                            .contains("request_data_active_requests_test_TestSourceDataVerticle 0.0");
                    assertThat(resp.bodyAsString())
                            .contains("request_counter_test_TestSourceDataVerticle_total{query=\"\",} 1.0");

                    assertThat(resp.bodyAsString()).contains(
                            "retrieve_data_timer_DataVerticle_test_TestSourceDataVerticle__seconds_max{name=\"TestSourceDataVerticle\",namespace=\"test\",} ");
                    assertThat(resp.bodyAsString()).contains(
                            "retrieve_data_counter_DataVerticle_test_TestSourceDataVerticle__total{name=\"TestSourceDataVerticle\",namespace=\"test\",succeeded=\"true\",} 1.0");
                    assertThat(resp.bodyAsString()).contains(
                            "retrieve_data_active_requests_DataVerticle_test_TestSourceDataVerticle_{name=\"TestSourceDataVerticle\",namespace=\"test\",} 0.0");
                    assertThat(resp.bodyAsString()).contains(
                            "retrieve_counter_DataVerticle_test_TestSourceDataVerticle__total{name=\"TestSourceDataVerticle\",namespace=\"test\",} 1.0");

                    assertThat(resp.bodyAsString()).contains(
                            "retrieve_data_timer_DataVerticle_test_TestRequireDataVerticle__seconds_count{name=\"TestRequireDataVerticle\",namespace=\"test\",} ");
                    assertThat(resp.bodyAsString()).contains(
                            "retrieve_data_timer_DataVerticle_test_TestRequireDataVerticle__seconds_sum{name=\"TestRequireDataVerticle\",namespace=\"test\",} ");
                    assertThat(resp.bodyAsString()).contains(
                            "retrieve_data_timer_DataVerticle_test_TestRequireDataVerticle__seconds_max{name=\"TestRequireDataVerticle\",namespace=\"test\",} ");

                    assertThat(resp.bodyAsString()).contains(
                            "retrieve_data_counter_DataVerticle_test_TestRequireDataVerticle__total{name=\"TestRequireDataVerticle\",namespace=\"test\",succeeded=\"true\",} 1.0");
                    assertThat(resp.bodyAsString()).contains(
                            "retrieve_data_active_requests_DataVerticle_test_TestRequireDataVerticle_{name=\"TestRequireDataVerticle\",namespace=\"test\",} 0.0");
                    assertThat(resp.bodyAsString()).contains(
                            "retrieve_counter_DataVerticle_test_TestRequireDataVerticle__total{name=\"TestRequireDataVerticle\",namespace=\"test\",} 1.0");
                    testContext.completeNow();
                }));
    }
}
