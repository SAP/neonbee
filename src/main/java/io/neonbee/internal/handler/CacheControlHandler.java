package io.neonbee.internal.handler;

import static io.netty.handler.codec.http.HttpHeaderNames.PRAGMA;
import static io.vertx.core.http.HttpHeaders.CACHE_CONTROL;
import static io.vertx.core.http.HttpHeaders.EXPIRES;

import io.vertx.core.MultiMap;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.PlatformHandler;

public class CacheControlHandler implements PlatformHandler {
    /**
     * Creates a new {@link CacheControlHandler}.
     */
    public CacheControlHandler() {
        super();
    }

    @Override
    public void handle(RoutingContext routingContext) {
        routingContext.addHeadersEndHandler(nothing -> {
            MultiMap headers = routingContext.response().headers();
            if (headers.contains(CACHE_CONTROL) || headers.contains(PRAGMA) || headers.contains(EXPIRES)) {
                // somebody took care about cache control already, do nothing here!
                return;
            }

            // if no caching headers are set, set no-caching headers by default!
            headers.set(CACHE_CONTROL, "no-cache, no-store, must-revalidate");
            headers.set(PRAGMA, "no-cache");
            headers.set(EXPIRES, "0");
        });
        routingContext.next();
    }
}
