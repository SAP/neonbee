package io.neonbee.internal;

import static io.vertx.core.eventbus.ReplyFailure.ERROR;

import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.common.annotations.VisibleForTesting;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.neonbee.logging.LoggingFacade;
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

    private static final LoggingFacade LOGGER = LoggingFacade.create();

    private static final AtomicBoolean EVENTBUS_ERROR_HANDLING_MESSAGE = new AtomicBoolean();

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
            // unfortunately as of 2024 / Vert.x 4.5.1, Vert.x did not implement a proper exception handling mechanism
            // for the event bus yet (see [1]), thus raising the exception here will cause an uncaught exception in
            // Vert.x's generic vertx.exceptionHandler(...), but not in a failed reply of the message. the only way to
            // compensate this, is to modifying the underlying MessageImpl, which will fail in Java 17+, in case no
            // appropriate --add-opens JVM argument has been set. we will log a warning in this case
            // [1] https://github.com/eclipse-vertx/vert.x/issues/3070
            try {
                Field fieldToModify = MessageImpl.class.getDeclaredField("receivedBody");
                fieldToModify.setAccessible(true);// NOPMD
                fieldToModify.set(message, new ReplyException(ERROR,
                        "Decoding of message body failed. " + e.getMessage()));
            } catch (InaccessibleObjectException e2) {
                // only show the warning message once if warn logging is enabled
                if (LOGGER.isWarnEnabled() && EVENTBUS_ERROR_HANDLING_MESSAGE.compareAndSet(false, true)) {
                    LOGGER.warn(
                            "Vert.x does not implement proper error handling for event bus replies, see [1]. NeonBee attempts to compensate for this overriding a private field in the event bus message, but is unable to do so in recent JVM releases, if no \"--add-opens java.base/{}=ALL-UNNAMED\" JVM argument was set. Consider adapting your JVM runtime arguments when launching NeonBee.\n\n[1] https://github.com/eclipse-vertx/vert.x/issues/3070",
                            MessageImpl.class.getPackageName());
                }
            } catch (NoSuchFieldException | IllegalArgumentException | IllegalAccessException e2) { // NOPMD
                // nothing we can do here, the message will result in an uncaught exception
            }
        } finally {
            deliveryContext.next();
        }
    }
}
