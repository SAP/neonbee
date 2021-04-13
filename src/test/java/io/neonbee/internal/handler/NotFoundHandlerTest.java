package io.neonbee.internal.handler;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.vertx.ext.web.RoutingContext;

class NotFoundHandlerTest {
    @Test
    @DisplayName("test not found handler")
    void testNotFound() {
        RoutingContext routingContextMock = mock(RoutingContext.class);
        new NotFoundHandler().handle(routingContextMock);
        verify(routingContextMock).fail(404);
    }
}
