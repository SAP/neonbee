package io.neonbee.endpoint.openapi;

import static io.vertx.ext.web.openapi.router.RequestExtractor.withBodyHandler;
import static io.vertx.ext.web.openapi.router.RouterBuilder.KEY_META_DATA_OPERATION;
import static io.vertx.ext.web.openapi.router.RouterBuilder.KEY_META_DATA_VALIDATED_REQUEST;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import io.neonbee.endpoint.Endpoint;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.openapi.router.RouterBuilder;
import io.vertx.openapi.contract.OpenAPIContract;
import io.vertx.openapi.validation.ResponseValidator;
import io.vertx.openapi.validation.ValidatableResponse;
import io.vertx.openapi.validation.ValidatedRequest;
import io.vertx.openapi.validation.ValidatorException;

public abstract class AbstractOpenAPIEndpoint implements Endpoint {
    /**
     * This variable <b>MUST</b> only be used in the scope of {@link #createRouter(Vertx, RouterBuilder)} to ensure it
     * is not null.
     */
    protected OpenAPIContract contract;

    /**
     * This variable <b>MUST</b> only be used in the scope of {@link #createRouter(Vertx, RouterBuilder)} to ensure it
     * is not null.
     */
    protected ResponseValidator responseValidator;

    /**
     * A new default {@link AbstractOpenAPIEndpoint}.
     */
    protected AbstractOpenAPIEndpoint() {
        super();
    }

    @Override
    public Future<Router> createEndpointRouter(Vertx vertx, String basePath, JsonObject config) {
        return getOpenAPIContract(vertx, config).compose(resolvedContract -> {
            this.contract = resolvedContract;
            this.responseValidator = ResponseValidator.create(vertx, contract);
            return createRouter(vertx, RouterBuilder.create(vertx, contract, withBodyHandler()));
        });
    }

    /**
     * Returns the OpenAPI contract.
     *
     * @param vertx  the related Vert.x instance
     * @param config the endpoint config
     * @return the OpenAPI contract
     */
    protected abstract Future<OpenAPIContract> getOpenAPIContract(Vertx vertx, JsonObject config);

    /**
     * Returns the {@link Future} holding the created {@link Router}.
     *
     * @param vertx         the related Vert.x instance
     * @param routerBuilder the builder to create the OpenAPI router
     * @return a {@link Future} holding the created {@link Router}
     */
    protected abstract Future<Router> createRouter(Vertx vertx, RouterBuilder routerBuilder);

    /**
     * Like {@link #createResponseValidationHandler(BiFunction, BiConsumer)}, but exceptions thrown during the response
     * validation are directly propagated to the related routing context.
     *
     * @param requestProcessor The related request processor
     * @return the created handler
     */
    protected Handler<RoutingContext> createResponseValidationHandler(
            BiFunction<ValidatedRequest, RoutingContext, Future<ValidatableResponse>> requestProcessor) {
        return createResponseValidationHandler(requestProcessor, (e, rtx) -> rtx.fail(e));
    }

    /**
     * Creates a handler that processes an incoming request with the passed request processor. The resulting response
     * will be validated and automatically send back the requester.
     *
     * @param requestProcessor The related request processor
     * @param exceptionHandler To handle {@link ValidatorException} thrown during the response validation
     * @return the created handler
     */
    protected Handler<RoutingContext> createResponseValidationHandler(
            BiFunction<ValidatedRequest, RoutingContext, Future<ValidatableResponse>> requestProcessor,
            BiConsumer<ValidatorException, RoutingContext> exceptionHandler) {
        return routingContext -> {
            ValidatedRequest validatedRequest = routingContext.get(KEY_META_DATA_VALIDATED_REQUEST);
            String operationId = routingContext.currentRoute().getMetadata(KEY_META_DATA_OPERATION);

            requestProcessor.apply(validatedRequest, routingContext)
                    .compose(validatableResponse -> responseValidator.validate(validatableResponse, operationId))
                    .compose(validatedResponse -> validatedResponse.send(routingContext.response()))
                    .onFailure(e -> {
                        if (e instanceof ValidatorException) {
                            exceptionHandler.accept((ValidatorException) e, routingContext);
                        } else {
                            routingContext.fail(e);
                        }
                    });
        };
    }
}
