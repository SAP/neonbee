package io.neonbee.endpoint.raw;

import static io.neonbee.data.DataAction.CREATE;
import static io.neonbee.data.DataAction.DELETE;
import static io.neonbee.data.DataAction.READ;
import static io.neonbee.data.DataAction.UPDATE;
import static io.neonbee.data.DataException.FAILURE_CODE_NO_HANDLERS;
import static io.neonbee.data.DataException.FAILURE_CODE_TIMEOUT;
import static io.neonbee.data.DataVerticle.requestData;
import static io.neonbee.endpoint.Endpoint.createRouter;
import static io.neonbee.internal.helper.CollectionHelper.multiMapToMap;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.CREATED;
import static io.netty.handler.codec.http.HttpResponseStatus.GATEWAY_TIMEOUT;
import static io.netty.handler.codec.http.HttpResponseStatus.METHOD_NOT_ALLOWED;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpResponseStatus.NO_CONTENT;
import static io.vertx.core.Future.succeededFuture;
import static io.vertx.core.http.HttpMethod.GET;
import static io.vertx.core.http.HttpMethod.HEAD;
import static io.vertx.core.http.HttpMethod.PATCH;
import static io.vertx.core.http.HttpMethod.POST;
import static io.vertx.core.http.HttpMethod.PUT;
import static io.vertx.ext.web.impl.Utils.pathOffset;
import static java.lang.Character.isUpperCase;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;

import io.neonbee.config.EndpointConfig;
import io.neonbee.data.DataAction;
import io.neonbee.data.DataException;
import io.neonbee.data.DataQuery;
import io.neonbee.data.DataRequest;
import io.neonbee.data.internal.DataContextImpl;
import io.neonbee.endpoint.Endpoint;
import io.neonbee.internal.RegexBlockList;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class RawEndpoint implements Endpoint {
    /**
     * The key to configure if hidden verticles should be exposed or not.
     */
    public static final String CONFIG_EXPOSE_HIDDEN_VERTICLES = "exposeHiddenVerticles";

    /**
     * The default path the raw endpoint is exposed by NeonBee.
     */
    public static final String DEFAULT_BASE_PATH = "/raw/";

    /**
     * Hidden verticles are those verticles not being exposed via the raw endpoint. To denounce a verticle as "hidden"
     * the name of the verticle has to start with an underscore _ after the namespace part. In the default configuration
     * of the raw endpoint the "exposeHiddenVerticles" parameter is set to {@code false}.
     */
    private static final Pattern HIDDEN_VERTICLES_PATTERN = Pattern.compile("(?:^|/)(?!.*/)_");

    @Override
    public EndpointConfig getDefaultConfig() {
        // as the EndpointConfig stays mutable, do not extract this to a static variable, but return a new object
        return new EndpointConfig().setType(RawEndpoint.class.getName()).setBasePath(DEFAULT_BASE_PATH)
                .setAdditionalConfig(new JsonObject().put(CONFIG_EXPOSE_HIDDEN_VERTICLES, false));
    }

    @Override
    public Future<Router> createEndpointRouter(Vertx vertx, String basePath, JsonObject config) {
        return succeededFuture(createRouter(vertx, new RawHandler(config)));
    }

    @VisibleForTesting
    static class RawHandler implements Handler<RoutingContext> {
        private final boolean exposeHiddenVerticles;

        private final RegexBlockList exposedVerticles;

        /**
         * Creates a new RawDataEndpointHandler based on the given configuration.
         *
         * @param config the configuration
         */
        RawHandler(JsonObject config) {
            // exposeHiddenVertices is a configuration which is explicitly additional to the "exposedVerticles" block
            // list. this is due to the nature of the block list, blocking all verticles by default when only a allow
            // list is specified. if we would add the HIDDEN_VERTICLES_PATTERN to the block list here, this might have
            // side effects for the user of the block list, as if the user would add something only to the allow list
            // the list would behave differently than expected. thus this parameter is checked in addition.
            exposeHiddenVerticles = config.getBoolean(CONFIG_EXPOSE_HIDDEN_VERTICLES, false);

            // a block / allow list of all verticles that should be exposed via this endpoint (works in conjunction with
            // the exposeHiddenVerticles flag, as described in the previous comment)
            exposedVerticles = RegexBlockList.fromJson(config.getValue("exposedVerticles"));
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
            } else if ((!exposeHiddenVerticles && HIDDEN_VERTICLES_PATTERN.matcher(qualifiedName).find())
                    || !exposedVerticles.isAllowed(qualifiedName)) {
                // do not even start looking for a verticle, as the vert as _ verticle are not exposed publicly!
                routingContext.fail(NOT_FOUND.code());
                return;
            }

            Map<String, List<String>> queryParameterMap;
            try {
                queryParameterMap = DataQuery.parseEncodedQueryString(request.query());
            } catch (IllegalArgumentException e) {
                routingContext.fail(BAD_REQUEST.code(), new IllegalArgumentException("Invalid request query"));
                return;
            }

            DataQuery query = new DataQuery(action,
                    pathOffset(routingContext.normalizedPath(), routingContext).substring(qualifiedName.length() + 1),
                    queryParameterMap, multiMapToMap(request.headers()), routingContext.body().buffer())
                            .addHeader("X-HTTP-Method", request.method().name());

            DataContextImpl context = new DataContextImpl(routingContext);
            requestData(routingContext.vertx(), new DataRequest(qualifiedName, query),
                    new DataContextImpl(routingContext)).onComplete(asyncResult -> {
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

                            // propagate the error to the ErrorHandler which sets the status code depending on the
                            // passed exception.
                            routingContext.fail(-1, cause);
                            return;
                        }

                        Object result = asyncResult.result();
                        if (result == null) {
                            routingContext.response().setStatusCode((action == CREATE ? CREATED : NO_CONTENT).code())
                                    .end();
                            return;
                        }

                        HttpServerResponse response = routingContext.response()//
                                .putHeader("Content-Type",
                                        Optional.ofNullable(context.responseData().get("Content-Type"))
                                                .map(String.class::cast).orElse("application/json"));
                        if (result instanceof JsonObject) {
                            result = ((JsonObject) result).toBuffer();
                        } else if (result instanceof JsonArray) {
                            result = ((JsonArray) result).toBuffer();
                        } else if (!(result instanceof Buffer)) {
                            // TODO add logic here, what kind of data is returned by the data verticle and what kind of
                            // data is ACCEPTed by the client. For now just support JSON and always return
                            // application/json.
                            result = Json.encodeToBuffer(asyncResult.result());
                        } else {
                            // fallback to text/plain, so that the browser tries to display it, instead of downloading
                            // it
                            response.putHeader("Content-Type", "text/plain");
                        }

                        response.end((Buffer) result);
                    });
        }

        /**
         * Determine the qualified name of the verticle. The verticle name will be the first path element which starts
         * with an upper case latin letter or a underscore _. Every path element till the verticle name is treated as
         * part of the namespace, which itself is separated by forward slashes. The namespace is treated lower-case.
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
}
