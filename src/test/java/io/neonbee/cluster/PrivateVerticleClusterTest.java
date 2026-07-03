package io.neonbee.cluster;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.test.helper.DeploymentHelper.deployVerticle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.neonbee.NeonBee;
import io.neonbee.NeonBeeExtension;
import io.neonbee.NeonBeeInstanceConfiguration;
import io.neonbee.data.DataContext;
import io.neonbee.data.DataException;
import io.neonbee.data.DataMap;
import io.neonbee.data.DataQuery;
import io.neonbee.data.DataRequest;
import io.neonbee.data.DataVerticle;
import io.neonbee.data.internal.DataContextImpl;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxTestContext;

class PrivateVerticleClusterTest extends NeonBeeExtension.TestBase {
    private static final DataVerticle<JsonObject> PRIVATE_TARGET_VERTICLE = new DataVerticle<>() {
        @Override
        public String getName() {
            return "#foo";
        }

        @Override
        public Future<JsonObject> retrieveData(DataQuery query, DataMap dataMap, DataContext context) {
            return Future.succeededFuture(new JsonObject().put("name", "Bar"));
        }
    };

    @Test
    @DisplayName("Test that private verticles are local-only in clustered setup")
    void testPrivateVerticleIsNotClusterExposed(
            @NeonBeeInstanceConfiguration(clustered = true, activeProfiles = {}) NeonBee source,
            @NeonBeeInstanceConfiguration(clustered = true, activeProfiles = {}) NeonBee target,
            VertxTestContext testContext) {
        Checkpoint localRequest = testContext.checkpoint();
        Checkpoint remoteRequest = testContext.checkpoint();

        deployVerticle(target.getVertx(), PRIVATE_TARGET_VERTICLE).compose(s -> {
            // Request from the same node should succeed for private verticles.
            DataRequest request = new DataRequest(PRIVATE_TARGET_VERTICLE.getName());
            return DataVerticle.<JsonObject>requestData(target.getVertx(), request, new DataContextImpl());
        }).compose(response -> {
            testContext.verify(() -> {
                assertThat(response.getString("name")).isEqualTo("Bar");
                localRequest.flag();
            });

            // Request from another cluster node should fail because consumer is local-only.
            DataRequest request = new DataRequest(PRIVATE_TARGET_VERTICLE.getName());
            return DataVerticle.<JsonObject>requestData(source.getVertx(), request, new DataContextImpl());
        }).onComplete(testContext.failing(e -> {
            testContext.verify(() -> {
                assertThat(e).isInstanceOf(DataException.class);
                assertThat(e).hasMessageThat().contains("No handlers for address");
                assertThat(((DataException) e).failureCode()).isEqualTo(DataException.FAILURE_CODE_NO_HANDLERS);
                remoteRequest.flag();
            });
        }));
    }
}
