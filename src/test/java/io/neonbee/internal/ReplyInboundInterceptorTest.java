package io.neonbee.internal;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.internal.ReplyInboundInterceptor.REPLY_ADDRESS_PREFIX;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.vertx.core.eventbus.DeliveryContext;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.eventbus.impl.MessageImpl;

class ReplyInboundInterceptorTest {
    @Test
    @DisplayName("should not alter message in case it has no issues parsing it")
    @SuppressWarnings("unchecked")
    void shouldParseMessage() throws Exception {
        ReplyInboundInterceptor interceptor = new ReplyInboundInterceptor();
        DeliveryContext<Object> context = mock(DeliveryContext.class);
        Message<Object> message = mock(MessageImpl.class);
        when(message.address()).thenReturn(REPLY_ADDRESS_PREFIX + "ok");
        when(context.message()).thenReturn(message);
        interceptor.handle(context);
        verify(message).body();
        verify(context).next();
        assertThat(getMessageBody(message)).isNull();
    }

    @Test
    @DisplayName("should set ReplyException in case there are issues parsing the message")
    @SuppressWarnings("unchecked")
    void shouldResultInError() throws Exception {
        ReplyInboundInterceptor interceptor = new ReplyInboundInterceptor();
        DeliveryContext<Object> context = mock(DeliveryContext.class);
        Message<Object> message = mock(MessageImpl.class);
        when(message.address()).thenReturn(REPLY_ADDRESS_PREFIX + "ok");
        when(context.message()).thenReturn(message);
        when(message.body()).thenThrow(new RuntimeException("BAD RUNTIME EXCEPTION! WOW!"));
        interceptor.handle(context);
        verify(message).body();
        verify(context).next();
        assertThat(getMessageBody(message)).isInstanceOf(ReplyException.class);
        assertThat((ReplyException) getMessageBody(message)).hasMessageThat().contains("BAD RUNTIME EXCEPTION! WOW!");
    }

    @Test
    @DisplayName("should not try to parse body in case it is not a reply message")
    @SuppressWarnings("unchecked")
    void shouldSkipNonReplies() throws Exception {
        ReplyInboundInterceptor interceptor = new ReplyInboundInterceptor();
        DeliveryContext<Object> context = mock(DeliveryContext.class);
        Message<Object> message = mock(MessageImpl.class);
        when(message.address()).thenReturn("anything");
        when(context.message()).thenReturn(message);
        interceptor.handle(context);
        verify(message, never()).body();
        verify(context).next();
    }

    private static Object getMessageBody(Message<?> message)
            throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
        Field field = MessageImpl.class.getDeclaredField("receivedBody");
        field.setAccessible(true);
        return field.get(message);
    }
}
