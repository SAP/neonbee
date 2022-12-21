package io.neonbee.endpoint.metrics;

import static com.google.common.truth.Truth.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.neonbee.NeonBee;
import io.neonbee.NeonBeeOptions.Mutable;
import io.neonbee.NeonBeeProfile;
import io.neonbee.config.EndpointConfig;
import io.neonbee.config.ServerConfig;
import io.neonbee.endpoint.Endpoint;
import io.neonbee.internal.verticle.ServerVerticle;
import io.neonbee.test.base.NeonBeeTestBase;
import io.neonbee.test.helper.WorkingDirectoryBuilder;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.junit5.VertxTestContext;
import io.vertx.micrometer.backends.BackendRegistries;

@SuppressWarnings("PMD.AvoidUnnecessaryTestClassesModifier")
public class NeonBeeMetricsTest extends NeonBeeTestBase {
    private static final String TEST_ENDPOINT_URI = "/testendpoint/";

    private static final String METRICS_ENDPOINT_URI = "/metrics/";

    @Override
    protected void adaptOptions(TestInfo testInfo, Mutable options) {
        options.clearActiveProfiles().addActiveProfile(NeonBeeProfile.WEB);
    }

    @Override
    protected WorkingDirectoryBuilder provideWorkingDirectoryBuilder(TestInfo testInfo, VertxTestContext testContext) {
        return super.provideWorkingDirectoryBuilder(testInfo, testContext).setCustomTask(root -> {
            DeploymentOptions opts = WorkingDirectoryBuilder.readDeploymentOptions(ServerVerticle.class, root);
            EndpointConfig epc1 = new EndpointConfig().setType(MetricsEndpoint.class.getName());
            EndpointConfig epc2 = new EndpointConfig().setType(TestEndpoint.class.getName());
            ServerConfig sc = new ServerConfig(opts.getConfig()).setEndpointConfigs(List.of(epc1, epc2));
            opts.setConfig(sc.toJson());
            WorkingDirectoryBuilder.writeDeploymentOptions(ServerVerticle.class, opts, root);
        });
    }

    @Test
    void testCustomMetric(Vertx vertx, VertxTestContext testContext) {
        createRequest(HttpMethod.GET, TEST_ENDPOINT_URI).send()
                .onComplete(testContext.succeeding(httpResponse -> testContext
                        .verify(() -> assertThat(httpResponse.statusCode()).isEqualTo(HttpResponseStatus.OK.code()))))
                .compose(response -> createRequest(HttpMethod.GET, METRICS_ENDPOINT_URI).send()
                        .onComplete(testContext.succeeding(httpResponse -> {
                            testContext.verify(() -> assertThat(httpResponse.statusCode())
                                    .isEqualTo(HttpResponseStatus.OK.code()));
                            assertThat(httpResponse.bodyAsString())
                                    .contains("TestEndpointCounter_total{TestTag1=\"TestValue\",} 1.0");
                            testContext.completeNow();
                        })));
    }

    public static class TestEndpoint implements Endpoint {
        @Override
        public EndpointConfig getDefaultConfig() {
            return new EndpointConfig().setType(TestEndpoint.class.getName()).setBasePath(TEST_ENDPOINT_URI);
        }

        @Override
        public Future<Router> createEndpointRouter(Vertx vertx, String basePath, JsonObject config) {
            return Future.succeededFuture(Endpoint.createRouter(vertx, new TestEndpointHandler()));
        }
    }

    private static class TestEndpointHandler implements Handler<RoutingContext> {
        @Override
        public void handle(RoutingContext rc) {
            MeterRegistry backendRegistry =
                    BackendRegistries.getNow(NeonBee.get(rc.vertx()).getOptions().getMetricsRegistryName());
            double count = Double.NaN;
            if (backendRegistry != null) {
                Counter counter = backendRegistry.counter("TestEndpointCounter", "TestTag1", "TestValue");
                counter.increment();
                count = counter.count();
            }
            rc.response().setStatusCode(HttpResponseStatus.OK.code()).end(Double.toString(count));
        }
    }
}
