package io.neonbee.endpoint;

import io.neonbee.config.EndpointConfig;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public interface Endpoint {
    EndpointConfig getDefaultConfig();

    Router createEndpointRouter(Vertx vertx, String basePath, JsonObject config);

    static Router createRouter(Vertx vertx, Handler<RoutingContext> handler) {
        Router router = Router.router(vertx);
        router.route().handler(handler);
        return router;
    }
}
