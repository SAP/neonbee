package io.neonbee.data;

import static io.neonbee.test.helper.DeploymentHelper.deployVerticle;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.neonbee.NeonBee;
import io.neonbee.NeonBeeExtension;
import io.neonbee.NeonBeeInstanceConfiguration;
import io.neonbee.data.internal.DataContextImpl;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(NeonBeeExtension.class)
public class DataVerticleBroadcastTest {
    private static final String BROADCAST_ADDRESS = "PublishSubscriber";

    private static DataVerticle<Void> getBroadcastReceiver(Checkpoint cp) {
        return new DataVerticle<>() {
            @Override
            public String getName() {
                return BROADCAST_ADDRESS;
            }

            @Override
            public Future<Void> updateData(DataQuery query, DataContext context) {
                cp.flag();
                return Future.succeededFuture().mapEmpty();
            }
        };
    }

    @Test
    @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
    @DisplayName("Test that broadcasting to multiple instances of the same verticle works as expected")
    public void testBroadcastInClusteredMode(@NeonBeeInstanceConfiguration(clustered = true) NeonBee node1,
            @NeonBeeInstanceConfiguration(clustered = true) NeonBee node2,
            @NeonBeeInstanceConfiguration(clustered = true) NeonBee node3, VertxTestContext testContext) {
        Checkpoint cp = testContext.checkpoint(3);

        CompositeFuture.all(deployVerticle(node1.getVertx(), getBroadcastReceiver(cp)),
                deployVerticle(node2.getVertx(), getBroadcastReceiver(cp)),
                deployVerticle(node3.getVertx(), getBroadcastReceiver(cp))).compose(v -> {
                    DataRequest request =
                            new DataRequest(BROADCAST_ADDRESS, new DataQuery(DataAction.UPDATE)).setBroadcasting(true);
                    return DataVerticle.requestData(node1.getVertx(), request, new DataContextImpl());
                }).onComplete(testContext.succeeding(v -> {}));
    }
}
