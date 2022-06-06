package io.neonbee.internal.handler;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.internal.handler.ChainAuthHandler.NOOP_AUTHENTICATION_HANDLER;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.neonbee.config.AuthHandlerConfig;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.HttpException;
import io.vertx.ext.web.handler.impl.AuthenticationHandlerInternal;
import io.vertx.junit5.VertxExtension;

@ExtendWith(VertxExtension.class)
class ChainAuthHandlerTest {
    @Test
    @DisplayName("create empty ChainAuthHandler")
    void emptyChainAuthHandlerTest() {
        assertThat(ChainAuthHandler.create(null, null)).isSameInstanceAs(NOOP_AUTHENTICATION_HANDLER);
        assertThat(ChainAuthHandler.create(null, List.of())).isSameInstanceAs(NOOP_AUTHENTICATION_HANDLER);
    }

    @Test
    @DisplayName("create a non-empty ChainAuthHandler")
    void createChainAuthHandlerTest(Vertx vertx) throws IOException {
        AuthHandlerConfig config1 = mock(AuthHandlerConfig.class);
        when(config1.createAuthHandler(any())).thenReturn(mock(AuthenticationHandlerInternal.class));

        AuthHandlerConfig config2 = mock(AuthHandlerConfig.class);
        when(config2.createAuthHandler(any())).thenReturn(mock(AuthenticationHandlerInternal.class));

        ChainAuthHandler handler = ChainAuthHandler.create(vertx, List.of(config1, config2));
        assertThat(handler).isNotSameInstanceAs(NOOP_AUTHENTICATION_HANDLER);
        verify(config1).createAuthHandler(vertx);
        verify(config2).createAuthHandler(vertx);
    }

    @Test
    @DisplayName("test ChainAuthHandler with multiple checks")
    @SuppressWarnings("unchecked")
    void testChainAuthHandler(Vertx vertx) throws IOException {
        AtomicBoolean firstHandlerCalled = new AtomicBoolean();
        AuthenticationHandlerInternal handler1 = mock(AuthenticationHandlerInternal.class);
        AuthHandlerConfig config1 = mock(AuthHandlerConfig.class);
        when(config1.createAuthHandler(any())).thenReturn(handler1);
        doAnswer(invocation -> {
            firstHandlerCalled.set(true);
            ((Handler<Future<Void>>) invocation.getArgument(1)).handle(Future.failedFuture(new HttpException(401)));
            return null;
        }).when(handler1).authenticate(any(), any());

        AuthenticationHandlerInternal handler2 = mock(AuthenticationHandlerInternal.class);
        AuthHandlerConfig config2 = mock(AuthHandlerConfig.class);
        when(config2.createAuthHandler(any())).thenReturn(handler2);

        RoutingContext routingContextMock = mock(RoutingContext.class);
        HttpServerRequest requestMock = mock(HttpServerRequest.class);
        when(requestMock.isEnded()).thenReturn(true);
        when(routingContextMock.request()).thenReturn(requestMock);

        ChainAuthHandler handler = ChainAuthHandler.create(vertx, List.of(config1, config2));
        handler.handle(routingContextMock);
        assertThat(firstHandlerCalled.get()).isTrue();
        verify(handler2).authenticate(eq(routingContextMock), any());
    }
}
