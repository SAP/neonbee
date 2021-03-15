package io.neonbee.cluster;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.test.helper.DeploymentHelper.deployVerticle;
import static io.neonbee.test.helper.DeploymentHelper.undeployAllVerticlesOfClass;
import static io.vertx.core.Future.succeededFuture;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import com.google.common.collect.Range;

import io.neonbee.NeonBee;
import io.neonbee.NeonBeeExtension;
import io.neonbee.NeonBeeInstanceConfiguration;
import io.neonbee.data.DataContext;
import io.neonbee.data.DataMap;
import io.neonbee.data.DataQuery;
import io.neonbee.data.DataRequest;
import io.neonbee.data.DataVerticle;
import io.neonbee.data.internal.DataContextImpl;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(NeonBeeExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
class LocalPreferredClusterTest {
    private static final String LOCAL = "local";

    private static final String REMOTE = "remote";

    private static final Long REPETITION = 10L; // must be even

    private NeonBee localNode;

    @BeforeEach
    @Timeout(value = 20, timeUnit = TimeUnit.SECONDS)
    @DisplayName("Setup the cluster nodes and deploy the consumers")
    void setUp(@NeonBeeInstanceConfiguration(clustered = true) NeonBee localNode,
            @NeonBeeInstanceConfiguration(clustered = true) NeonBee remoteNode, VertxTestContext testContext) {
        this.localNode = localNode;
        deployVerticle(localNode.getVertx(), new ConsumerVerticle(LOCAL))
                .compose(v -> deployVerticle(remoteNode.getVertx(), new ConsumerVerticle(REMOTE)))
                .onComplete(testContext.succeedingThenComplete());
    }

    @AfterEach
    void tearDown(VertxTestContext testContext) {
        localNode.getVertx().close(testContext.succeedingThenComplete());
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("Test that localPreferred requests are always dispatched to local consumer")
    void testLocalPreferredRequest(VertxTestContext testContext) {
        // Create a localPreferred request
        DataRequest request = new DataRequest(ConsumerVerticle.NAME);
        fireRequests(request).onComplete(testContext.succeeding(responseMap -> {
            testContext.verify(() -> assertThat(responseMap).containsExactly(LOCAL, REPETITION));
            testContext.completeNow();
        }));
    }

    @Test
    @Timeout(value = 20, timeUnit = TimeUnit.SECONDS)
    @DisplayName("Test that localPreferred requests are dispatched to remote consumers when no local consumer is available")
    void testLocalPreferredRequestWithoutLocalConsumer(VertxTestContext testContext) {
        // Create a localPreferred request
        DataRequest request = new DataRequest(ConsumerVerticle.NAME);
        undeployAllVerticlesOfClass(localNode.getVertx(), ConsumerVerticle.class).compose(v -> fireRequests(request))
                .onComplete(testContext.succeeding(responseMap -> {
                    testContext.verify(() -> assertThat(responseMap).containsExactly(REMOTE, REPETITION));
                    testContext.completeNow();
                }));
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("Test that non localPreferred requests are dispatched to local and remote consumers")
    void testNonLocalPreferredRequest(VertxTestContext testContext) {
        Range<Long> expectedRange = Range.closed(REPETITION / 2 - 1, REPETITION / 2 + 1);

        // Create a non localPreferred request
        DataRequest request = new DataRequest(ConsumerVerticle.NAME).setLocalPreferred(false);
        fireRequests(request).onComplete(testContext.succeeding(responseMap -> {
            testContext.verify(() -> {
                long local = responseMap.get(LOCAL);
                long remote = responseMap.get(REMOTE);
                assertThat(Long.sum(local, remote)).isEqualTo(REPETITION);
                assertThat(local).isIn(expectedRange);
                assertThat(remote).isIn(expectedRange);
            });
            testContext.completeNow();
        }));
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("Test that data verticle are registered and deregistered properly as local consumer")
    void testLocalConsumerRegistration(VertxTestContext testContext) {
        String verticleAddress = "DataVerticle[" + ConsumerVerticle.NAME + "]";
        assertThat(localNode.isLocalConsumerAvailable(verticleAddress)).isTrue();
        undeployAllVerticlesOfClass(localNode.getVertx(), ConsumerVerticle.class)
                .onComplete(testContext.succeeding(v -> {
                    testContext.verify(() -> assertThat(localNode.isLocalConsumerAvailable(verticleAddress)).isFalse());
                    testContext.completeNow();
                }));
    }

    private Future<Map<String, Long>> fireRequests(DataRequest request) {
        return CompositeFuture.all(IntStream.rangeClosed(1, REPETITION.intValue())
                .mapToObj(i -> DataVerticle.requestData(localNode.getVertx(), request, new DataContextImpl()))
                .collect(Collectors.toList())).map(cpf -> mapResponses(cpf.list()));
    }

    private Map<String, Long> mapResponses(List<String> results) {
        return results.stream().collect(Collectors.groupingBy(nodeResp -> nodeResp, Collectors.counting()));
    }

    private static class ConsumerVerticle extends DataVerticle<String> {
        public static final String NAME = "Consumer";

        private final String node;

        ConsumerVerticle(String node) {
            super();
            this.node = node;
        }

        @Override
        public String getName() {
            return NAME;
        }

        @Override
        public Future<String> retrieveData(DataQuery query, DataMap require, DataContext context) {
            return succeededFuture(node);
        }
    }
}
