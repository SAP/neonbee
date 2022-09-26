package io.neonbee.cluster;

import static io.neonbee.NeonBeeInstanceConfiguration.ClusterManager.HAZELCAST;
import static io.neonbee.NeonBeeInstanceConfiguration.ClusterManager.INFINISPAN;
import static io.neonbee.data.DataVerticle.requestData;
import static io.neonbee.test.helper.DeploymentHelper.deployVerticle;
import static io.neonbee.test.helper.DummyVerticleHelper.createDummyDataVerticle;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.neonbee.NeonBee;
import io.neonbee.NeonBeeExtension;
import io.neonbee.NeonBeeInstanceConfiguration;
import io.neonbee.data.DataRequest;
import io.neonbee.data.internal.DataContextImpl;
import io.neonbee.test.helper.DataResponseVerifier;
import io.vertx.core.Future;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(NeonBeeExtension.class)
class NativeClusterManagerTest implements DataResponseVerifier {

    @Test
    @Timeout(value = 20, timeUnit = TimeUnit.SECONDS)
    @DisplayName("Test native Infinispan cluster")
    void testInfinispan(
            @NeonBeeInstanceConfiguration(clustered = true, activeProfiles = {},
                    clusterManager = INFINISPAN) NeonBee node1,
            @NeonBeeInstanceConfiguration(clustered = true, activeProfiles = {},
                    clusterManager = INFINISPAN) NeonBee node2,
            VertxTestContext testContext) {

        doTest(node1, node2, testContext).onComplete(testContext.succeedingThenComplete());
    }

    @Test
    @Timeout(value = 20, timeUnit = TimeUnit.SECONDS)
    @DisplayName("Test native Hazelcast cluster")
    void testHazelcast(
            @NeonBeeInstanceConfiguration(clustered = true, activeProfiles = {}, clusterManager = HAZELCAST,
                    clusterConfigFile = "hazelcast-localtcp.xml") NeonBee node1,
            @NeonBeeInstanceConfiguration(clustered = true, activeProfiles = {}, clusterManager = HAZELCAST,
                    clusterConfigFile = "hazelcast-localtcp.xml") NeonBee node2,
            VertxTestContext testContext) {

        doTest(node1, node2, testContext).onComplete(testContext.succeedingThenComplete());
    }

    private Future<Void> doTest(NeonBee node1, NeonBee node2, VertxTestContext testContext) {
        String expectedResponse = "Expected Response";
        String node1FQN = "node1DV";

        return deployVerticle(node1.getVertx(), createDummyDataVerticle(node1FQN).withStaticResponse(expectedResponse))
                .compose(s -> {
                    Future<String> resp =
                            requestData(node2.getVertx(), new DataRequest(node1FQN), new DataContextImpl());
                    return assertDataEquals(resp, expectedResponse, testContext);
                });
    }
}
