package io.neonbee.endpoint.metrics;

import static com.google.common.truth.Truth.assertThat;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.neonbee.NeonBee;
import io.neonbee.NeonBeeOptions;
import io.neonbee.config.EndpointConfig;
import io.neonbee.endpoint.Endpoint;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.micrometer.backends.BackendRegistries;

@ExtendWith(VertxExtension.class)
@SuppressWarnings("PMD.AvoidUnnecessaryTestClassesModifier")
public class NeonBeeMetricsTest {
    private static final int PORT = 10808;

    private static final String HOST = "localhost";

    private static final String TEST_ENDPOINT_URI = "/testendpoint/";

    private static final String METRICS_ENDPOINT_URI = "/metrics/";

    private static TestEndpointHandler testEndpointHandler;

    private static final int OK = HttpResponseStatus.OK.code();

    @BeforeEach
    void init() {
        testEndpointHandler = new TestEndpointHandler();
    }

    @AfterEach
    @SuppressWarnings("PMD.NullAssignment")
    void reset() {
        testEndpointHandler = null;
    }

    @Test
    @Timeout(value = 1, timeUnit = TimeUnit.MINUTES)
    void testCustomMetric(Vertx vertx, VertxTestContext context) {
        Checkpoint prometheusCheckpoint = context.checkpoint(1);

        NeonBeeOptions.Mutable mutable = new NeonBeeOptions.Mutable();
        mutable.setServerPort(PORT);
        mutable.setWorkingDirectory(Path.of("src", "test", "resources", "io", "neonbee", "endpoint", "metrics"));

        NeonBee.create(mutable).onComplete(context.succeeding(event -> httpGet(vertx, TEST_ENDPOINT_URI)
                .onComplete(response -> context.succeeding(
                        httpResponse -> context.verify(() -> assertThat(response.result().statusCode()).isEqualTo(OK))))
                .compose(
                        response -> httpGet(vertx, METRICS_ENDPOINT_URI).onComplete(context.succeeding(httpResponse -> {
                            context.verify(() -> assertThat(httpResponse.statusCode()).isEqualTo(OK));

                            httpResponse.bodyHandler(bodyBuffer -> context.verify(() -> {
                                assertThat(bodyBuffer.toString())
                                        .contains("TestEndpointCounter_total{TestTag1=\"TestValue\",} 1.0");
                                prometheusCheckpoint.flag();
                            }));
                        })))));
    }

    static Future<HttpClientResponse> httpGet(Vertx vertx, String requestUri) {
        return vertx.createHttpClient().request(HttpMethod.GET, PORT, HOST, requestUri)
                .compose(HttpClientRequest::send);
    }

    public static class TestEndpoint implements Endpoint {

        @Override
        public EndpointConfig getDefaultConfig() {
            return new EndpointConfig().setType(MetricsEndpoint.class.getName()).setBasePath(TEST_ENDPOINT_URI);
        }

        @Override
        public Future<Router> createEndpointRouter(Vertx vertx, String basePath, JsonObject config) {
            return Future.succeededFuture(Endpoint.createRouter(vertx, testEndpointHandler));
        }
    }

    private static class TestEndpointHandler implements Handler<RoutingContext> {

        @Override
        public void handle(RoutingContext rc) {
            MeterRegistry backendRegistry = BackendRegistries.getDefaultNow();
            double count = Double.NaN;
            if (backendRegistry != null) {
                Counter counter = backendRegistry.counter("TestEndpointCounter", "TestTag1", "TestValue");
                counter.increment();
                count = counter.count();
            }
            rc.response().setStatusCode(OK).end(Double.toString(count));
        }
    }
}
