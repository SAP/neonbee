package io.neonbee.internal.handler.factories;

import io.neonbee.internal.verticle.ServerVerticle;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

/**
 * Factory interface for creating routing handlers.
 *
 * This interface is evaluated when {@link ServerVerticle} is started. The returned handlers will be added to the root
 * route.
 */
public interface RoutingHandlerFactory {

    /**
     * Returns an instance of the {@link Handler} object to add to the main route.
     *
     * @return the {@link Handler} object
     */
    Future<Handler<RoutingContext>> createHandler();
}
