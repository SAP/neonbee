package io.neonbee.cluster;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.test.helper.DeploymentHelper.deployVerticle;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

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
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(NeonBeeExtension.class)
class LocalRequestClusterTest {
    private static final DataVerticle<JsonObject> LOCAL_TARGET_VERTICLE = new DataVerticle<>() {
        @Override
        public String getName() {
            return "LocalTarget";
        }

        @Override
        public Future<JsonObject> retrieveData(DataQuery query, DataMap dataMap, DataContext context) {
            return Future.succeededFuture(new JsonObject().put("name", "Duke"));
        }
    };

    @Test
    @Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
    @DisplayName("Test that setLocalOnly works as expected")
    void testLocalRequest(@NeonBeeInstanceConfiguration(clustered = true) NeonBee source,
            @NeonBeeInstanceConfiguration(clustered = true) NeonBee target, VertxTestContext testContext) {
        Checkpoint nonLocalRequest = testContext.checkpoint();
        Checkpoint localRequest = testContext.checkpoint();

        deployVerticle(target.getVertx(), LOCAL_TARGET_VERTICLE).compose(s -> {
            // Create a non local request
            DataRequest request = new DataRequest(LOCAL_TARGET_VERTICLE.getName());
            return DataVerticle.<JsonObject>requestData(source.getVertx(), request, new DataContextImpl());
        }).compose(response -> {
            testContext.verify(() -> {
                assertThat(response.getString("name")).isEqualTo("Duke");
                nonLocalRequest.flag();
            });
            // Create a local request
            DataRequest request = new DataRequest(LOCAL_TARGET_VERTICLE.getName()).setLocalOnly(true);
            return DataVerticle.<JsonObject>requestData(source.getVertx(), request, new DataContextImpl());
        }).onComplete(testContext.failing(e -> {
            testContext.verify(() -> {
                assertThat(e).isInstanceOf(DataException.class);
                assertThat(e).hasMessageThat().contains("No handlers for address");
                assertThat(((DataException) e).failureCode()).isEqualTo(DataException.FAILURE_CODE_NO_HANDLERS);
                localRequest.flag();
            });
        }));
    }
}
