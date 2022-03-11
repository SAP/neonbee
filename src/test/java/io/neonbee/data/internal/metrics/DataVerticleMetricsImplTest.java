package io.neonbee.data.internal.metrics;

import static com.google.common.truth.Truth.assertThat;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.neonbee.NeonBee;
import io.neonbee.NeonBeeOptions;
import io.neonbee.config.NeonBeeConfig;
import io.neonbee.data.DataVerticle;
import io.neonbee.test.helper.SystemHelper;
import io.vertx.core.CompositeFuture;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class DataVerticleMetricsImplTest {

    @Test
    @Timeout(value = 1, timeUnit = TimeUnit.MINUTES)
    void testMetricsOnPrometheusEndpoint(Vertx vertx, VertxTestContext context) throws Exception {
        int port = SystemHelper.getFreePort();

        NeonBeeOptions.Mutable options = new NeonBeeOptions.Mutable();
        options.setServerPort(port);
        options.setIgnoreClassPath(true);

        NeonBeeConfig config = new NeonBeeConfig();
        config.getMetricsConfig().setEnabled(true);

        NeonBee.create(options, config).onComplete(context.succeeding(neonBee -> {

            DeploymentOptions deploymentOptions = new DeploymentOptions();
            deploymentOptions.setConfig(new JsonObject().put(DataVerticle.CONFIG_METRICS_KEY,
                    new JsonObject().put(ConfiguredDataVerticleMetrics.ENABLED, true)));

            CompositeFuture
                    .all(neonBee.getVertx().deployVerticle(new TestSourceDataVerticle(), deploymentOptions),
                            neonBee.getVertx().deployVerticle(new TestRequireDataVerticle(), deploymentOptions))
                    .onComplete(context.succeeding(event -> {
                        HttpRequest<Buffer> request = createRequest(neonBee, HttpMethod.GET,
                                "/raw/" + TestRequireDataVerticle.QUALIFIED_NAME);
                        request.send().onComplete(context.succeeding(resp -> {
                            context.verify(() -> {
                                assertThat(resp.statusCode()).isEqualTo(200);
                                assertThat(resp.bodyAsString())
                                        .isEqualTo("\"TestRequireDataVerticle[TestSourceDataVerticle content]\"");
                            });
                        })).compose(unused -> createRequest(neonBee, HttpMethod.GET, "/metrics").send()
                                .onComplete(context.succeeding(resp -> {
                                    context.verify(() -> {
                                        assertThat(resp.statusCode()).isEqualTo(200);
                                        assertThat(resp.bodyAsString()).contains(
                                                "request_data_timer_test_TestSourceDataVerticle_seconds_count{query=\"\",} ");
                                        assertThat(resp.bodyAsString()).contains(
                                                "request_data_timer_test_TestSourceDataVerticle_seconds_sum{query=\"\",} ");
                                        assertThat(resp.bodyAsString()).contains(
                                                "request_data_timer_test_TestSourceDataVerticle_seconds_max{query=\"\",} ");

                                        assertThat(resp.bodyAsString()).contains(
                                                "request_data_counter_test_TestSourceDataVerticle_total{query=\"\",succeeded=\"true\",} 1.0");
                                        assertThat(resp.bodyAsString()).contains(
                                                "request_data_active_requests_test_TestSourceDataVerticle 0.0");
                                        assertThat(resp.bodyAsString()).contains(
                                                "request_counter_test_TestSourceDataVerticle_total{query=\"\",} 1.0");

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

                                        neonBee.getVertx().close(context.succeeding(e -> context.completeNow()));
                                    });
                                })));
                    }));
        }));
    }

    public HttpRequest<Buffer> createRequest(NeonBee neonBee, HttpMethod method, String path) {
        WebClientOptions opts =
                new WebClientOptions().setDefaultHost("localhost").setDefaultPort(neonBee.getOptions().getServerPort());
        return WebClient.create(neonBee.getVertx(), opts).request(method, path);
    }
}
