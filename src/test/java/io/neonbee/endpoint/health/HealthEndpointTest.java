package io.neonbee.endpoint.health;

import static com.google.common.truth.Truth.assertThat;
import static java.util.stream.Collectors.toList;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.neonbee.test.base.NeonBeeTestBase;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxTestContext;

class HealthEndpointTest extends NeonBeeTestBase {

    @Test
    @DisplayName("should return health info of the default checks")
    void testHealthEndpointData(VertxTestContext testContext) {
        createRequest(HttpMethod.GET, "/health").send(testContext.succeeding(response -> testContext.verify(() -> {
            assertThat(response.statusCode()).isEqualTo(200);

            JsonObject result = response.bodyAsJsonObject();
            assertThat(result.containsKey("outcome")).isTrue();
            assertThat(result.containsKey("status")).isTrue();
            assertThat(result.containsKey("checks")).isTrue();

            List<String> ids = result.getJsonArray("checks").stream().map(JsonObject.class::cast)
                    .map(c -> c.getString("id")).collect(toList());
            assertThat(ids).contains(String.format("node.%s.os.memory", getNeonBee().getNodeId()));

            testContext.completeNow();
        })));
    }
}
