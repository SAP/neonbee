package io.neonbee.endpoint.health;

import static com.google.common.truth.Truth.assertThat;
import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.neonbee.test.base.NeonBeeTestBase;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxTestContext;

class HealthEndpointTest extends NeonBeeTestBase {

    Function<HttpResponse<Buffer>, JsonObject> validateResponse = response -> {
        assertThat(response.statusCode()).isEqualTo(200);
        JsonObject body = response.bodyAsJsonObject();
        assertThat(body.containsKey("outcome")).isTrue();
        assertThat(body.containsKey("status")).isTrue();
        assertThat(body.containsKey("checks")).isTrue();
        return body;
    };

    Function<JsonObject, List<String>> getIds =
            result -> result.getJsonArray("checks").stream().map(JsonObject.class::cast)
                    .map(c -> c.getString("id")).collect(toList());

    @Test
    @DisplayName("should return health info of the default checks")
    void testHealthEndpointAll(VertxTestContext testContext) {
        createRequest(HttpMethod.GET, "/health/").send(testContext.succeeding(response -> testContext.verify(() -> {
            JsonObject body = validateResponse.apply(response);
            List<String> ids = getIds.apply(body);
            assertThat(ids.size()).isGreaterThan(1);
            assertThat(ids).contains(String.format("node.%s.os.memory", getNeonBee().getNodeId()));
            testContext.completeNow();
        })));
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("should only return health info of passed check")
    void testHealthEndpointSingle(VertxTestContext testContext) {
        createRequest(HttpMethod.GET, "/health/os.memory")
                .send(testContext.succeeding(response -> testContext.verify(() -> {
                    JsonObject body = validateResponse.apply(response);
                    assertThat(getIds.apply(body))
                            .containsExactly(String.format("node.%s.os.memory", getNeonBee().getNodeId()));
                    testContext.completeNow();
                })));
    }
}
