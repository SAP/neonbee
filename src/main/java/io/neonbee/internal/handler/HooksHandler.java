package io.neonbee.internal.handler;

import static io.neonbee.hook.HookType.ROUTING_CONTEXT;

import java.util.Map;

import io.neonbee.NeonBee;
import io.neonbee.data.DataException;
import io.neonbee.hook.HookType;
import io.neonbee.logging.LoggingFacade;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

/**
 * This handler will trigger the execution of the passed {@link HookType} and prevents execution of the next handlers if
 * any error occurs during the hook execution.
 */
public class HooksHandler implements Handler<RoutingContext> {
    private static final LoggingFacade LOGGER = LoggingFacade.create();

    private final HookType hookType;

    /**
     * Creates a new HooksHandler with the passed HookType.
     *
     * @param hookType the HookType to execute inside this handler
     */
    public HooksHandler(HookType hookType) {
        this.hookType = hookType;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        NeonBee.get(routingContext.vertx()).getHookRegistry()
                .executeHooks(hookType, Map.of(ROUTING_CONTEXT, routingContext))
                .onComplete(asyncResult -> {
                    if (asyncResult.failed()) {
                        Throwable cause = asyncResult.cause();
                        LOGGER.error("An error has occurred while executing the request hook of type {}", hookType,
                                cause);
                        if (cause instanceof DataException dataException) {
                            routingContext.fail(dataException.failureCode());
                        } else {
                            routingContext.fail(cause);
                        }
                    } else {
                        routingContext.next();
                    }
                });
    }
}
