package io.neonbee.internal.handler;

import java.net.HttpURLConnection;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

public class NotFoundHandler implements Handler<RoutingContext> {
    /**
     * Convenience method as similar other Vertx handler implementations (e.g. ErrorHandler)
     *
     * @return The NotFoundHandler
     */
    public static NotFoundHandler create() {
        return new NotFoundHandler();
    }

    @Override
    public void handle(RoutingContext routingContext) {
        routingContext.fail(HttpURLConnection.HTTP_NOT_FOUND);
    }
}
