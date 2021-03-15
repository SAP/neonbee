package io.neonbee.cluster;

import static io.neonbee.test.helper.DeploymentHelper.deployVerticle;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.neonbee.NeonBee;
import io.neonbee.NeonBeeExtension;
import io.neonbee.NeonBeeInstanceConfiguration;
import io.neonbee.data.DataContext;
import io.neonbee.data.DataMap;
import io.neonbee.data.DataQuery;
import io.neonbee.data.DataRequest;
import io.neonbee.data.DataVerticle;
import io.neonbee.data.internal.DataContextImpl;
import io.neonbee.internal.tracking.MessageDirection;
import io.neonbee.internal.tracking.TrackingDataHandlingStrategy;
import io.neonbee.internal.tracking.TrackingInterceptor;
import io.vertx.core.Future;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxTestContext;

@ExtendWith({ NeonBeeExtension.class, MockitoExtension.class })
class TrackingInterceptorClusterTest {

    @Mock
    private TrackingDataHandlingStrategy strategy;

    @BeforeEach
    void setUp() {
        lenient().doNothing().when(strategy).handleOutBoundRequest(any(DataContext.class));
        lenient().doNothing().when(strategy).handleInBoundRequest(any(DataContext.class));
        lenient().doNothing().when(strategy).handleOutBoundReply(any(DataContext.class));
        lenient().doNothing().when(strategy).handleInBoundReply(any(DataContext.class));
    }

    @Test
    @Timeout(value = 20, timeUnit = TimeUnit.SECONDS)
    @DisplayName("Body of messages over distributed eventbus should be non-empty.")
    void testNeonBeeWithClusters(@NeonBeeInstanceConfiguration(clustered = true) NeonBee core,
            @NeonBeeInstanceConfiguration(clustered = true) NeonBee stable, VertxTestContext testContext) {

        stable.getVertx().eventBus().addInboundInterceptor(new TrackingInterceptor(MessageDirection.INBOUND, strategy))
                .addOutboundInterceptor(new TrackingInterceptor(MessageDirection.OUTBOUND, strategy));
        core.getVertx().eventBus().addInboundInterceptor(new TrackingInterceptor(MessageDirection.INBOUND, strategy))
                .addOutboundInterceptor(new TrackingInterceptor(MessageDirection.OUTBOUND, strategy));

        DataVerticle<String> coreVerticle = new DataVerticle<>() {
            @Override
            public String getName() {
                return "Core";
            }

            @Override
            public Future<String> retrieveData(DataQuery query, DataMap require, DataContext context) {
                return Future.succeededFuture("Core result.");
            }
        };

        DataVerticle<String> stableVerticle = new DataVerticle<>() {
            @Override
            public String getName() {
                return "Stable";
            }

            @Override
            public Future<Collection<DataRequest>> requireData(DataQuery query, DataContext context) {
                DataRequest request = new DataRequest("Core", query);
                return Future.succeededFuture(List.of(request));
            }
        };

        DataRequest request = new DataRequest("Core");

        // Send a request to Core verticle via the Stable vertx, which causes the traffic to flow over distributed event
        // bus
        deployVerticle(core.getVertx(), coreVerticle).compose(s -> deployVerticle(stable.getVertx(), stableVerticle))
                .compose(s -> DataVerticle.<String>requestData(stable.getVertx(), request, new DataContextImpl()))
                .onComplete(testContext.succeeding(result -> {
                    testContext.verify(() -> {
                        verify(strategy, times(1)).handleOutBoundRequest(any(DataContext.class));
                        verify(strategy, times(1)).handleInBoundRequest(any(DataContext.class));
                        verify(strategy, times(1)).handleOutBoundReply(any(DataContext.class));
                        verify(strategy, times(1)).handleInBoundReply(any(DataContext.class));
                    });
                    testContext.completeNow();
                }));
    }
}
