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

    /**
     * Create a failure handler to call in case the hook execution fails, failing the routing context accordingly.
     *
     * @param hookType       the HookType for which the failure handler is created
     * @param routingContext the RoutingContext to fail in case of an error
     * @return a Handler that handles failures during hook execution
     */
    public static Handler<? super Throwable> hookFailureHandler(HookType hookType, RoutingContext routingContext) {
        return throwable -> {
            LOGGER.error("An error has occurred while executing the request hook of type {}", hookType,
                    throwable);
            if (throwable instanceof DataException dataException) {
                routingContext.fail(dataException.failureCode());
            } else {
                routingContext.fail(throwable);
            }
        };
    }

    @Override
    public void handle(RoutingContext routingContext) {
        NeonBee.get(routingContext.vertx()).getHookRegistry()
                .executeHooks(hookType, Map.of(ROUTING_CONTEXT, routingContext))
                .onFailure(hookFailureHandler(hookType, routingContext))
                .onSuccess(anything -> routingContext.next());
    }
}
