package io.neonbee.internal.tracking;

import static io.neonbee.data.DataVerticle.CONTEXT_HEADER;
import static io.neonbee.data.internal.DataContextImpl.decodeContextFromString;

import com.google.common.annotations.VisibleForTesting;

import io.neonbee.data.DataContext;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.DeliveryContext;
import io.vertx.core.eventbus.Message;

/**
 * A tracking interceptor, which delegates the tracking data handling to a configurable handler.
 */
public class TrackingInterceptor implements Handler<DeliveryContext<Object>> {

    private final MessageDirection direction;

    private final TrackingDataHandlingStrategy handler;

    @VisibleForTesting
    public TrackingInterceptor(MessageDirection direction, TrackingDataHandlingStrategy handler) {
        this.direction = direction;
        this.handler = handler;
    }

    @Override
    public void handle(DeliveryContext<Object> event) {
        Message<Object> message = event.message();
        String contextHeader = message.headers().get(CONTEXT_HEADER);
        if (contextHeader != null) {
            DataContext context = decodeContextFromString(contextHeader);
            if (message.replyAddress() != null) {
                switch (direction) {
                case OUTBOUND:
                    handler.handleOutBoundRequest(context);
                    break;
                case INBOUND:
                    handler.handleInBoundRequest(context);
                    break;
                default:
                    break;
                }
            } else {
                switch (direction) {
                case OUTBOUND:
                    handler.handleOutBoundReply(context);
                    break;
                case INBOUND:
                    handler.handleInBoundReply(context);
                    break;
                default:
                    break;
                }
            }
        }
        event.next();
    }

    /**
     * Returns the message direction of this interceptor.
     *
     * @return the message direction
     */
    public MessageDirection getDirection() {
        return direction;
    }

    /**
     * Get the handler to delegate the message to.
     *
     * @return the delegate handler
     */
    public TrackingDataHandlingStrategy getHandler() {
        return handler;
    }
}
