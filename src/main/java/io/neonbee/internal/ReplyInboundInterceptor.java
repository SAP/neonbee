package io.neonbee.internal;

import static io.vertx.core.eventbus.ReplyFailure.ERROR;

import java.lang.reflect.Field;

import com.google.common.annotations.VisibleForTesting;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.DeliveryContext;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.eventbus.impl.MessageImpl;

/**
 * At the time of message reply inbound processing in Vert.x it is a "one-way road". If message parsing (e.g. due to an
 * exception in the codec) fails, the message will simply time-out on the event bus. To overcome this this guarding
 * inbound interceptor will modify the inbound message to set a {@link ReplyException} instead. This class unfortunately
 * needs to rely on reflections to do so.
 */
public class ReplyInboundInterceptor implements Handler<DeliveryContext<Object>> {
    @VisibleForTesting
    static final String REPLY_ADDRESS_PREFIX = "__vertx.reply."; // taken from EventBusImpl

    @Override
    @SuppressFBWarnings("DE_MIGHT_IGNORE")
    public void handle(DeliveryContext<Object> deliveryContext) {
        Message<?> message = deliveryContext.message();
        try {
            // we only need to check on message replies, because this is where Vert.x does not provide proper error
            // handling. in case of messages sent to other verticles, DataVerticle takes care of catching exceptions
            if (message.address().startsWith(REPLY_ADDRESS_PREFIX)) {
                message.body();
            }
        } catch (Exception e) {
            try {
                Field fieldToModify = MessageImpl.class.getDeclaredField("receivedBody");
                fieldToModify.setAccessible(true);
                fieldToModify.set(message, new ReplyException(ERROR,
                        "Decoding of message body failed. " + e.getMessage()));
            } catch (NoSuchFieldException | IllegalArgumentException | IllegalAccessException e2) { // NOPMD
                // nothing we can do here, the message will result in an uncaught exception
            }
        } finally {
            deliveryContext.next();
        }
    }
}
