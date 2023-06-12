package io.neonbee.endpoint.health;

import static com.google.common.truth.Truth.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.neonbee.test.base.NeonBeeTestBase;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxTestContext;

class StatusEndpointTest extends NeonBeeTestBase {

    @Test
    @DisplayName("should return health info of the default checks")
    void testHealthEndpointData(VertxTestContext testContext) {
        createRequest(HttpMethod.GET, "/status").send(testContext.succeeding(response -> testContext.verify(() -> {
            assertThat(response.statusCode()).isEqualTo(200);

            JsonObject result = response.bodyAsJsonObject();
            assertThat(result.containsKey("status")).isTrue();
            assertThat(result.containsKey("version")).isTrue();
            assertThat(result.containsKey("outcome")).isFalse();
            assertThat(result.containsKey("checks")).isFalse();

            testContext.completeNow();
        })));
    }
}
