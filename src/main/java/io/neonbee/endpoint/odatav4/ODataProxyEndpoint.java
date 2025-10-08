package io.neonbee.endpoint.odatav4;

import static io.vertx.core.Future.succeededFuture;

import io.neonbee.config.EndpointConfig;
import io.neonbee.endpoint.Endpoint;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;

public class ODataProxyEndpoint implements Endpoint {

    /**
     * The default path the OData proxy endpoint is exposed by NeonBee.
     */
    public static final String DEFAULT_BASE_PATH = "/odataproxy/";

    @Override
    public EndpointConfig getDefaultConfig() {
        return new EndpointConfig()
                .setType(ODataProxyEndpoint.class.getName())
                .setBasePath(DEFAULT_BASE_PATH);
    }

    @Override
    public Future<Router> createEndpointRouter(Vertx vertx, String basePath, JsonObject config) {
        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());
        router.route().handler(new ODataProxyEndpointHandler(config));
        return succeededFuture(router);
    }
}
