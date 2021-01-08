package io.neonbee.internal.handler;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.vertx.ext.web.RoutingContext;

public class NotFoundHandlerTest {
    @Test
    @DisplayName("test not found handler")
    public void testNotFound() {
        RoutingContext routingContextMock = mock(RoutingContext.class);
        NotFoundHandler.create().handle(routingContextMock);
        verify(routingContextMock).fail(404);
    }
}
