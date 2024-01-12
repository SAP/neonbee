package io.neonbee.internal.handler.factories;

import static io.vertx.core.Future.succeededFuture;

import io.neonbee.internal.handler.CacheControlHandler;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

public class CacheControlHandlerFactory implements RoutingHandlerFactory {
    /**
     * Creates a new {@link CacheControlHandlerFactory}.
     */
    public CacheControlHandlerFactory() {
        super();
    }

    @Override
    public Future<Handler<RoutingContext>> createHandler() {
        return succeededFuture(new CacheControlHandler());
    }
}
