package io.neonbee.internal.handler;

import static io.neonbee.data.DataAction.CREATE;
import static io.neonbee.data.DataAction.DELETE;
import static io.neonbee.data.DataAction.READ;
import static io.neonbee.data.DataAction.UPDATE;
import static io.neonbee.data.DataException.FAILURE_CODE_NO_HANDLERS;
import static io.neonbee.data.DataException.FAILURE_CODE_TIMEOUT;
import static io.neonbee.data.DataVerticle.requestData;
import static io.neonbee.internal.Helper.multiMapToMap;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.CREATED;
import static io.netty.handler.codec.http.HttpResponseStatus.GATEWAY_TIMEOUT;
import static io.netty.handler.codec.http.HttpResponseStatus.METHOD_NOT_ALLOWED;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpResponseStatus.NO_CONTENT;
import static io.vertx.core.http.HttpMethod.GET;
import static io.vertx.core.http.HttpMethod.HEAD;
import static io.vertx.core.http.HttpMethod.PATCH;
import static io.vertx.core.http.HttpMethod.POST;
import static io.vertx.core.http.HttpMethod.PUT;
import static io.vertx.ext.web.impl.Utils.pathOffset;
import static java.lang.Character.isUpperCase;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;

import io.neonbee.data.DataAction;
import io.neonbee.data.DataException;
import io.neonbee.data.DataQuery;
import io.neonbee.data.DataRequest;
import io.neonbee.data.internal.DataContextImpl;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

public class RawDataEndpointHandler implements Handler<RoutingContext> {
    private final boolean exposeHiddenVerticles;

    /**
     * Convenience method as similar other Vertx handler implementations (e.g. ErrorHandler)
     *
     * @param config the configuration of the endpoint
     * @return A RawDataEndpointHandler instance
     */
    public static RawDataEndpointHandler create(JsonObject config) {
        return new RawDataEndpointHandler(config);
    }

    /**
     * Creates a new RawDataEndpointHandler based on the given configuration.
     *
     * @param config the configuration
     */
    public RawDataEndpointHandler(JsonObject config) {
        exposeHiddenVerticles = config.getBoolean("exposeHiddenVerticles", false);
    }

    @Override
    public void handle(RoutingContext routingContext) {
        HttpServerRequest request = routingContext.request();

        DataAction action = mapMethodToAction(request.method());
        if (action == null) {
            routingContext.fail(METHOD_NOT_ALLOWED.code(),
                    new UnsupportedOperationException("HTTP Method is not allowed"));
            return;
        }

        String qualifiedName = determineQualifiedName(routingContext);
        if (Strings.isNullOrEmpty(qualifiedName)) {
            routingContext.fail(BAD_REQUEST.code(),
                    new IllegalArgumentException("Missing the full qualified verticle name"));
            return;
        } else if (!exposeHiddenVerticles && qualifiedName.charAt(qualifiedName.lastIndexOf('/') + 1) == '_') {
            // do not even start looking for a verticle, as _ verticle are not exposed publicly!
            routingContext.fail(NOT_FOUND.code());
            return;
        }
        String decodedQueryPath = null;
        try {
            if (!Strings.isNullOrEmpty(request.query())) {
                decodedQueryPath = URLDecoder.decode(request.query(), StandardCharsets.UTF_8);
            }
        } catch (IllegalArgumentException e) {
            routingContext.fail(BAD_REQUEST.code(), new IllegalArgumentException("Invalid request query"));
            return;
        }

        DataQuery query = new DataQuery(action,
                pathOffset(routingContext.normalizedPath(), routingContext).substring(qualifiedName.length() + 1),
                decodedQueryPath, multiMapToMap(request.headers()), routingContext.getBody()).addHeader("X-HTTP-Method",
                        request.method().name());

        requestData(routingContext.vertx(), new DataRequest(qualifiedName, query), new DataContextImpl(routingContext))
                .onComplete(asyncResult -> {
                    if (asyncResult.failed()) {
                        Throwable cause = asyncResult.cause();
                        if (cause instanceof DataException) {
                            switch (((DataException) cause).failureCode()) {
                            case FAILURE_CODE_NO_HANDLERS:
                                routingContext.fail(NOT_FOUND.code());
                                return;
                            case FAILURE_CODE_TIMEOUT:
                                routingContext.fail(GATEWAY_TIMEOUT.code());
                                return;
                            default:
                                /* nothing to do here, propagate error to the ErrorHandler */
                            }
                        }

                        // Propagate error to the ErrorHandler which sets the status code depending on the passed
                        // exception.
                        routingContext.fail(-1, cause);
                        return;
                    }

                    Object result = asyncResult.result();
                    if (result == null) {
                        routingContext.response().setStatusCode((action == CREATE ? CREATED : NO_CONTENT).code()).end();
                        return;
                    }

                    HttpServerResponse response = routingContext.response()//
                            .putHeader("Content-Type", "application/json");
                    if (result instanceof JsonObject) {
                        result = ((JsonObject) result).toBuffer();
                    } else if (result instanceof JsonArray) {
                        result = ((JsonArray) result).toBuffer();
                    } else if (!(result instanceof Buffer)) {
                        // TODO add logic here, what kind of data is returned by the data verticle and what kind of data
                        // is ACCEPTed by the client. For now just support JSON and always return application/json.
                        result = Json.encodeToBuffer(asyncResult.result());
                    } else {
                        // fallback to text/plain, so that the browser tries to display it, instead of downloading it
                        response.putHeader("Content-Type", "text/plain");
                    }

                    response.end((Buffer) result);
                });
    }

    /**
     * Determine the qualified name of the verticle. The verticle name will be the first path element which starts with
     * an upper case latin letter or a underscore _. Every path element till the verticle name is treated as part of the
     * namespace, which itself is separated by forward slashes. The namespace is treated lower-case.
     *
     * @return the qualified name of a verticle, or null in case no qualified name could be determined
     */
    @VisibleForTesting
    static String determineQualifiedName(RoutingContext routingContext) {
        String routingPath = pathOffset(routingContext.normalizedPath(), routingContext);
        int routingPathLength = routingPath.length();
        int nextForwardSlash = 0;
        while ((nextForwardSlash = routingPath.indexOf('/', nextForwardSlash)) != -1
                && ++nextForwardSlash != routingPathLength) {
            char firstCharacter = routingPath.charAt(nextForwardSlash);
            if ('_' == firstCharacter || isUpperCase(firstCharacter)) {
                return routingPath.substring(1, nextForwardSlash).toLowerCase(Locale.ROOT)
                        + routingPath.substring(nextForwardSlash).split("/", 2)[0];
            }
        }

        return null;
    }

    private DataAction mapMethodToAction(HttpMethod method) {
        if (POST.equals(method)) {
            return CREATE;
        } else if (HEAD.equals(method) || GET.equals(method)) {
            return READ;
        } else if (PUT.equals(method) || PATCH.equals(method)) {
            return UPDATE;
        } else if (HttpMethod.DELETE.equals(method)) {
            return DELETE;
        } else {
            return null;
        }
    }
}
