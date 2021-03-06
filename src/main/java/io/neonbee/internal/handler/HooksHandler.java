package io.neonbee.internal.handler;

import static io.neonbee.hook.HookType.ONCE_PER_REQUEST_ROUTING_CONTEXT;

import java.util.Map;

import io.neonbee.NeonBee;
import io.neonbee.data.DataException;
import io.neonbee.hook.HookType;
import io.neonbee.logging.LoggingFacade;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

/**
 * This handler will trigger the execution of the ONCE_PER_REQUEST hook, preventing execution of the next handlers if
 * any is any error occurs.
 */
public final class HooksHandler implements Handler<RoutingContext> {
    private static final LoggingFacade LOGGER = LoggingFacade.create();

    /**
     * Creates a default HooksHandler.
     *
     * @return the HooksHandler
     */
    public static Handler<RoutingContext> create() {
        return new HooksHandler();
    }

    @Override
    public void handle(RoutingContext routingContext) {
        NeonBee neonBee = NeonBee.instance(routingContext.vertx());
        neonBee.getHookRegistry()
                .executeHooks(HookType.ONCE_PER_REQUEST, Map.of(ONCE_PER_REQUEST_ROUTING_CONTEXT, routingContext))
                .onComplete(asyncResult -> {
                    if (asyncResult.failed()) {
                        Throwable cause = asyncResult.cause();
                        LOGGER.error("An error has occurred while executing the request hook", cause);
                        if (cause instanceof DataException) {
                            routingContext.fail(((DataException) cause).failureCode());
                        } else {
                            routingContext.fail(cause);
                        }
                    } else {
                        routingContext.next();
                    }
                });
    }

    private HooksHandler() {}
}
