package io.neonbee.internal.handler;

import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

public class NotFoundHandler implements Handler<RoutingContext> {

    @Override
    public void handle(RoutingContext routingContext) {
        routingContext.fail(NOT_FOUND.code());
    }
}
