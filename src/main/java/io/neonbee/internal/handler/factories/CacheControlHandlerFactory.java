package io.neonbee.internal.handler.factories;

import static io.vertx.core.Future.succeededFuture;

import io.neonbee.internal.handler.CacheControlHandler;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

/**
 * Create the {@link CacheControlHandler}.
 */
public class CacheControlHandlerFactory implements RoutingHandlerFactory {

    @Override
    public Future<Handler<RoutingContext>> createHandler() {
        return succeededFuture(new CacheControlHandler());
    }
}
