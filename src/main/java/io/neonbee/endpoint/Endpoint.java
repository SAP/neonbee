package io.neonbee.endpoint;

import io.neonbee.config.EndpointConfig;
import io.neonbee.data.DataContext;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public interface Endpoint {

    /**
     * This constant is used as a key within the {@link DataContext#responseData()} map to specify the content type of
     * the HTTP response.
     * <p>
     * The value associated with this key is intended to be used as the value for the "Content-Type" header in the HTTP
     * response.
     */
    String CONTENT_TYPE_HINT = "Content-Type";

    /**
     * This constant is used as a key within the {@link DataContext#responseData()} map to specify a map containing
     * additional HTTP response header values.
     * <p>
     * All entries in the map specified by this key are added to the HTTP response headers, allowing for the flexible
     * inclusion of various headers. If `CONTENT_TYPE_HINT` is specified both as a standalone key and within this map,
     * the standalone `CONTENT_TYPE_HINT` takes precedence, and the entry within the map will be ignored.
     */
    String RESPONSE_HEADERS_HINT = "RESPONSE_HEADERS";

    /**
     * Get the default configuration for a given endpoint.
     * <p>
     * Note: The {@link Endpoint} class has no control over whether the returned EndpointConfig is tinkered with, thus
     * the returned {@link EndpointConfig} should either be immutable or a new instance to not run into issues when the
     * configuration is changed afterwards.
     *
     * @return the default {@link EndpointConfig} for this endpoint.
     */
    EndpointConfig getDefaultConfig();

    /**
     * Create a router for this endpoint.
     *
     * @param vertx    the Vert.x instance to use to create the router for
     * @param basePath the base path NeonBee will mount the router to, once it was created
     * @param config   any additional configuration provided in the {@link EndpointConfig}
     * @return a {@link Future} holding a fully configured {@link Router} for this endpoint
     */
    Future<Router> createEndpointRouter(Vertx vertx, String basePath, JsonObject config);

    /**
     * Convenience method creating a {@link Router} for a single {@link Handler}.
     *
     * @param vertx   the Vert.x instance to create the router for
     * @param handler the handler to use in the router to handle all requests
     * @return a {@link Router} with exactly one handler registered
     */
    static Router createRouter(Vertx vertx, Handler<RoutingContext> handler) {
        Router router = Router.router(vertx);
        router.route().handler(handler);
        return router;
    }
}
