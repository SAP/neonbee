package io.neonbee.endpoint.health;

import static com.google.common.truth.Truth.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.neonbee.NeonBee;
import io.neonbee.NeonBeeOptions;
import io.neonbee.test.helper.SystemHelper;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class HealthEndpointTest {
    private static int port;

    @BeforeEach
    void setUp() throws IOException {
        port = SystemHelper.getFreePort();
    }

    @Test
    @Timeout(value = 1, timeUnit = TimeUnit.MINUTES)
    @DisplayName("registers default and clustered checks on /health endpoint")
    void testClusteredHealthChecks(Vertx vertx, VertxTestContext testContext) {
        Checkpoint cp = testContext.checkpoint();
        NeonBeeOptions.Mutable mutable = new NeonBeeOptions.Mutable().setServerPort(port).setClustered(true)
                .setClusterConfigResource("hazelcast-local.xml");
        mutable.setWorkingDirectory(Path.of("src", "test", "resources", "io", "neonbee", "endpoint", "health"));

        NeonBee.create(mutable).onComplete(testContext
                .succeeding(event -> httpGet(vertx, "/health/").onComplete(testContext.succeeding(httpResponse -> {
                    httpResponse.bodyHandler(bodyBuffer -> testContext.verify(() -> {
                        JsonArray checks = bodyBuffer.toJsonObject().getJsonArray("checks");
                        assertThat(checks.size()).isEqualTo(3);
                        assertThat(findCheck("physical-memory", checks).getString("status")).isAnyOf("UP", "DOWN");
                        assertThat(findCheck("cluster", checks).getString("status")).isAnyOf("UP", "DOWN");
                        assertThat(findCheck("node", checks).getString("status")).isAnyOf("UP", "DOWN");
                        assertThat(httpResponse.statusCode()).isEqualTo(HttpResponseStatus.SERVICE_UNAVAILABLE.code());
                        cp.flag();
                    }));
                }))));
    }

    private static Future<HttpClientResponse> httpGet(Vertx vertx, String requestUri) {
        return vertx.createHttpClient().request(HttpMethod.GET, port, "localhost", requestUri)
                .compose(HttpClientRequest::send);
    }

    private static JsonObject findCheck(String id, JsonArray checks) {
        return checks.stream().map(j -> (JsonObject) j).filter(c -> id.equals(c.getString("id"))).findFirst()
                .orElse(new JsonObject());
    }
}
