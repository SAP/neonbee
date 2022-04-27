package io.neonbee.endpoint.openapi;

import org.slf4j.Logger;

import io.neonbee.endpoint.Endpoint;
import io.neonbee.logging.LoggingFacade;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.openapi.RouterBuilder;

public abstract class AbstractOpenAPIEndpoint implements Endpoint {
    private static final Logger LOGGER = LoggingFacade.create();

    @Override
    public Future<Router> createEndpointRouter(Vertx vertx, String basePath, JsonObject config) {
        return getOpenAPIContractURL(vertx, config).compose(contractPath -> RouterBuilder.create(vertx, contractPath))
                .onFailure(err -> LOGGER.error("Error while parsing the OpenAPI Contract", err))
                .compose(rb -> createRouter(vertx, rb));
    }

    /**
     * Returns the URL to the OpenAPI contract. The URL is the location of your spec. It can be an absolute path, a
     * local path or remote url (with HTTP/HTTPS protocol).
     *
     * @param vertx  the related Vert.x instance
     * @param config the endpoint config
     * @return the URL to the OpenAPI contract
     */
    protected abstract Future<String> getOpenAPIContractURL(Vertx vertx, JsonObject config);

    /**
     * Returns the {@link Future} holding the created {@link Router}.
     *
     * @param vertx         the related Vert.x instance
     * @param routerBuilder the builder to create the OpenAPI router
     * @return a {@link Future} holding the created {@link Router}
     */
    protected abstract Future<Router> createRouter(Vertx vertx, RouterBuilder routerBuilder);
}
