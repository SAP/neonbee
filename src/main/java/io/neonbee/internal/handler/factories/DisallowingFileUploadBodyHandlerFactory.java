package io.neonbee.internal.handler.factories;

import static io.vertx.core.Future.succeededFuture;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

public class DisallowingFileUploadBodyHandlerFactory implements RoutingHandlerFactory {
    /**
     * Create a {@link BodyHandler} that disallows file uploads.
     */
    public DisallowingFileUploadBodyHandlerFactory() {
        // no initialization needed, however:
        // checkstyle suggests to create an empty constructor to explain the use of the class and
        // sonarcube is complaining if the constructor is empty and suggests to add (this) comment ;)
    }

    @Override
    public Future<Handler<RoutingContext>> createHandler() {
        return succeededFuture(BodyHandler.create(false /* do not handle file uploads */));
    }
}
