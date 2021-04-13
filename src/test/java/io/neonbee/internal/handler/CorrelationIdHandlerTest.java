package io.neonbee.internal.handler;

import static io.neonbee.config.ServerConfig.CorrelationStrategy.GENERATE_UUID;
import static io.neonbee.config.ServerConfig.CorrelationStrategy.REQUEST_HEADER;
import static io.neonbee.internal.handler.CorrelationIdHandler.CORRELATION_ID;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.RETURNS_MOCKS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;

class CorrelationIdHandlerTest {
    @Test
    @DisplayName("test GENERATE_UUID correlation strategy")
    void generateUuidStrategy() {
        RoutingContext routingContextMock = mock(RoutingContext.class);
        CorrelationIdHandler.create(GENERATE_UUID).handle(routingContextMock);
        verifyUuidCorrelationId(routingContextMock);
    }

    @Test
    @DisplayName("test REQUEST_HEADER correlation strategy fallback to UUID_STRATEGY")
    void requestHeaderStrategyFallback() {
        RoutingContext routingContextMock = mock(RoutingContext.class);
        when(routingContextMock.request()).then(RETURNS_MOCKS);
        CorrelationIdHandler.create(REQUEST_HEADER).handle(routingContextMock);
        verifyUuidCorrelationId(routingContextMock);
    }

    @Test
    @DisplayName("test REQUEST_HEADER correlation strategy x-vcap-request-id header")
    void requestHeaderStrategyHeaders() {
        String expectedCorrelationId = "expectedCorrelationId";

        for (String header : new String[] { "X-CorrelationID", "x-vcap-request-id" }) {
            RoutingContext routingContextMock = mock(RoutingContext.class);
            HttpServerRequest requestMock = mock(HttpServerRequest.class);
            when(requestMock.getHeader(header)).thenReturn(expectedCorrelationId);
            when(routingContextMock.request()).thenReturn(requestMock);

            CorrelationIdHandler.create(REQUEST_HEADER).handle(routingContextMock);
            verifyGivenCorrelationId(routingContextMock, expectedCorrelationId);
        }
    }

    private static void verifyUuidCorrelationId(RoutingContext routingContextMock) {
        verifyGivenCorrelationId(routingContextMock,
                "\\b[0-9a-f]{8}\\b-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-\\b[0-9a-f]{12}\\b");
    }

    private static void verifyGivenCorrelationId(RoutingContext routingContextMock, String expectedCorrelationId) {
        verify(routingContextMock).put(eq(CORRELATION_ID), matches(expectedCorrelationId));
    }
}
