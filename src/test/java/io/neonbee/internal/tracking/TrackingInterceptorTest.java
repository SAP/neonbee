package io.neonbee.internal.tracking;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.neonbee.data.DataContext;
import io.neonbee.data.DataQuery;
import io.neonbee.data.internal.DataContextImpl;
import io.vertx.core.MultiMap;
import io.vertx.core.eventbus.DeliveryContext;
import io.vertx.core.json.JsonObject;

public class TrackingInterceptorTest {
    private TestMessage<Object> message;

    @BeforeEach
    void setUp() {
        DataContextImpl context = new DataContextImpl("correlationId", "bearerToken",
                new JsonObject().put("username", "Duke"), null, null);
        context.pushVerticleToPath("Data1Verticle");
        context.amendTopVerticleCoordinate("deplymentId1");
        context.pushVerticleToPath("Data2Verticle");
        context.amendTopVerticleCoordinate("deplymentId2");
        String dataContextJson = DataContextImpl.encodeContextToString(context);

        message = new TestMessage<>("address", "replyAddress",
                MultiMap.caseInsensitiveMultiMap().add("Context", dataContextJson), new DataQuery());
    }

    @Test
    @DisplayName("test outbound message")
    void testHandleOutBoundMessages() {
        TrackingDataHandlingStrategy strategy = mock(TrackingDataHandlingStrategy.class);
        TrackingInterceptor interceptor = new TrackingInterceptor(MessageDirection.OUTBOUND, strategy);
        doNothing().when(strategy).handleOutBoundRequest(any(DataContext.class));
        doNothing().when(strategy).handleOutBoundReply(any(DataContext.class));
        DeliveryContext<Object> deliveryContext = new TestDeliveryContext<>(message);
        interceptor.handle(deliveryContext);
        verify(strategy, times(1)).handleOutBoundRequest(any(DataContext.class));
        verify(strategy, times(0)).handleOutBoundReply(any(DataContext.class));
        reset(strategy);

        message.setReplyAddress(null);
        interceptor.handle(deliveryContext);
        verify(strategy, times(0)).handleOutBoundRequest(any(DataContext.class));
        verify(strategy, times(1)).handleOutBoundReply(any(DataContext.class));
        reset(strategy);
    }

    @Test
    @DisplayName("test inbound message")
    void testHandleInBoundMessages() {
        TrackingDataHandlingStrategy strategy = mock(TrackingDataHandlingStrategy.class);
        TrackingInterceptor interceptor = new TrackingInterceptor(MessageDirection.INBOUND, strategy);
        doNothing().when(strategy).handleInBoundRequest(any(DataContext.class));
        doNothing().when(strategy).handleInBoundReply(any(DataContext.class));
        DeliveryContext<Object> deliveryContext = new TestDeliveryContext<>(message);
        interceptor.handle(deliveryContext);
        verify(strategy, times(1)).handleInBoundRequest(any(DataContext.class));
        verify(strategy, times(0)).handleInBoundReply(any(DataContext.class));
        reset(strategy);

        message.setReplyAddress(null);
        interceptor.handle(deliveryContext);
        verify(strategy, times(0)).handleInBoundRequest(any(DataContext.class));
        verify(strategy, times(1)).handleInBoundReply(any(DataContext.class));
        reset(strategy);
    }
}
