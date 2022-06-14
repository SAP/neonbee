package io.neonbee.internal.handler.factories;

import static io.vertx.core.Future.succeededFuture;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

/**
 * Create A {@link BodyHandler} that disallows file uploads.
 */
public class DisallowingFileUploadBodyHandlerFactory implements RoutingHandlerFactory {

    @Override
    public Future<Handler<RoutingContext>> createHandler() {
        return succeededFuture(BodyHandler.create(false /* do not handle file uploads */));
    }
}
