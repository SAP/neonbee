package io.neonbee.endpoint.odatav4;

import static io.neonbee.data.DataVerticle.CONTEXT_HEADER;
import static io.neonbee.data.internal.DataContextImpl.decodeContextFromString;
import static io.neonbee.data.internal.DataContextImpl.encodeContextToString;
import static io.neonbee.endpoint.Endpoint.CONTENT_TYPE_HINT;
import static io.neonbee.endpoint.Endpoint.RESPONSE_HEADERS_HINT;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.GATEWAY_TIMEOUT;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.vertx.ext.web.impl.Utils.pathOffset;
import static java.lang.Character.isUpperCase;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.google.common.annotations.VisibleForTesting;

import io.neonbee.data.DataAction;
import io.neonbee.data.DataContext;
import io.neonbee.data.DataException;
import io.neonbee.data.DataQuery;
import io.neonbee.data.internal.DataContextImpl;
import io.neonbee.entity.EntityVerticle;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.eventbus.ReplyFailure;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RequestBody;
import io.vertx.ext.web.RoutingContext;

public class ODataProxyEndpointHandler implements Handler<RoutingContext> {

    private static final String ORIG_URL_KEY = "origUrl";

    private static final String METHOD_KEY = "httpMethod";

    private static final String STATUS_CODE_HINT = "STATUS_CODE";

    private static final String CONFIG_SEND_TIMEOUT = "sendTimeout";

    private static final long DEFAULT_SEND_TIMEOUT = 0L;

    private final long sendTimeout;

    /**
     * Creates a new ODataProxyEndpointHandler based on the given configuration.
     *
     * @param config the configuration
     */
    public ODataProxyEndpointHandler(JsonObject config) {
        this.sendTimeout =
                Optional.ofNullable(config).map(json -> json.getLong(CONFIG_SEND_TIMEOUT, DEFAULT_SEND_TIMEOUT))
                        .orElse(DEFAULT_SEND_TIMEOUT);
    }

    @Override
    public void handle(RoutingContext routingContext) {
        // Determine the event address/full qualified name of the recipient verticle
        String qualifiedName = determineQualifiedName(routingContext);
        if (qualifiedName == null || qualifiedName.isEmpty()) {
            routingContext.fail(BAD_REQUEST.code(),
                    new IllegalArgumentException("Missing the full qualified verticle name"));
            return;
        }

        HttpServerRequest request = routingContext.request();

        // Create DataContext and add the origUrl into it
        DataContextImpl context = new DataContextImpl(routingContext);
        context.put(ORIG_URL_KEY, request.absoluteURI());
        context.put(METHOD_KEY, request.method().name());

        DataQuery query = buildDataQuery(routingContext, qualifiedName, request);

        DeliveryOptions options = new DeliveryOptions().addHeader(CONTEXT_HEADER, encodeContextToString(context));
        if (sendTimeout > 0) {
            options.setSendTimeout(sendTimeout);
        }

        String address = EntityVerticle.getProxyAddress(qualifiedName);

        routingContext.vertx().eventBus().<Object>request(address, query, options).onComplete(asyncResult -> {
            if (asyncResult.failed()) {
                handleFailure(routingContext, asyncResult.cause());
                return;
            }

            Message<Object> reply = asyncResult.result();
            mergeResponseContext(context, reply);

            Object result = reply.body();
            if (result instanceof DataException dataException) {
                handleDataException(routingContext, dataException);
                return;
            }

            HttpServerResponse response = routingContext.response();
            applyResponseHeaders(response, context);

            Buffer responseBuffer = toBuffer(result);
            String contentType = Optional.ofNullable(context.responseData().get(CONTENT_TYPE_HINT))
                    .map(Object::toString).filter(value -> !value.isBlank()).orElse(null);
            if (contentType != null) {
                response.putHeader(CONTENT_TYPE_HINT, contentType);
            } else if (!response.headers().contains(CONTENT_TYPE_HINT)) {
                response.putHeader(CONTENT_TYPE_HINT, "application/octet-stream");
            }

            int statusCode = Optional.ofNullable(context.responseData().get(STATUS_CODE_HINT))
                    .filter(Number.class::isInstance).map(Number.class::cast).map(Number::intValue)
                    .filter(code -> code > 0)
                    .orElse(response.getStatusCode() > 0 ? response.getStatusCode() : OK.code());
            response.setStatusCode(statusCode);

            if (responseBuffer == null || responseBuffer.length() == 0) {
                response.putHeader(HttpHeaders.CONTENT_LENGTH, "0");
                response.end(Buffer.buffer());
            } else {
                response.end(responseBuffer);
            }
        });
    }

    /**
     * Determine the qualified name of the verticle from the routing path. The verticle name will be the first path
     * element which starts with an upper case latin letter or a underscore _. Every path element till the verticle name
     * is treated as part of the namespace, which itself is separated by forward slashes. The namespace is treated
     * lower-case.
     *
     * @param routingContext the routing context
     * @return the qualified name of the verticle, or null if not found
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

    private static DataQuery buildDataQuery(RoutingContext routingContext, String qualifiedName,
            HttpServerRequest request) {
        Buffer body = Optional.ofNullable(routingContext.body()).map(RequestBody::buffer).orElse(null);

        DataQuery query = new DataQuery().setAction(mapAction(request.method()))
                .setUriPath(extractResourcePath(routingContext, qualifiedName))
                .setHeaders(toMap(request.headers()))
                .setBody(body);

        query.setRawQuery(request.query());
        return query;
    }

    private static String extractResourcePath(RoutingContext routingContext, String qualifiedName) {
        String routingPath = pathOffset(routingContext.normalizedPath(), routingContext);
        String baseSegment = '/' + qualifiedName;
        if (!routingPath.startsWith(baseSegment)) {
            return routingPath.isEmpty() ? "/" : routingPath;
        }
        String resourcePath = routingPath.substring(baseSegment.length());
        return resourcePath.isEmpty() ? "/" : resourcePath;
    }

    private static DataAction mapAction(HttpMethod method) {
        if (method == null) {
            return DataAction.READ;
        }

        if (HttpMethod.POST.equals(method)) {
            return DataAction.CREATE;
        } else if (HttpMethod.PUT.equals(method) || HttpMethod.PATCH.equals(method)) {
            return DataAction.UPDATE;
        } else if (HttpMethod.DELETE.equals(method)) {
            return DataAction.DELETE;
        }

        return DataAction.READ;
    }

    private static Map<String, List<String>> toMap(MultiMap multiMap) {
        if (multiMap == null || multiMap.isEmpty()) {
            return Map.of();
        }

        return multiMap.entries().stream().collect(Collectors.groupingBy(Map.Entry::getKey, LinkedHashMap::new,
                Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
    }

    private static void mergeResponseContext(DataContext context, Message<Object> reply) {
        DataContext responseContext = decodeContextFromString(reply.headers().get(CONTEXT_HEADER));
        if (responseContext != null) {
            context.setData(responseContext.data());
            context.mergeResponseData(responseContext.responseData());
        }
    }

    private static void applyResponseHeaders(HttpServerResponse response, DataContext context) {
        Object headersCandidate = context.responseData().get(RESPONSE_HEADERS_HINT);
        if (!(headersCandidate instanceof Map)) {
            return;
        }

        @SuppressWarnings("unchecked")
        Map<String, ?> headers = (Map<String, ?>) headersCandidate;
        headers.forEach((key, value) -> {
            if (key == null || value == null) {
                return;
            }
            if (CONTENT_TYPE_HINT.equalsIgnoreCase(key)) {
                return; // handled separately
            }

            if (value instanceof Iterable<?>) {
                for (Object item : (Iterable<?>) value) {
                    if (item != null) {
                        response.headers().add(key, item.toString());
                    }
                }
            } else if (value.getClass().isArray()) {
                int length = java.lang.reflect.Array.getLength(value);
                for (int index = 0; index < length; index++) {
                    Object item = java.lang.reflect.Array.get(value, index);
                    if (item != null) {
                        response.headers().add(key, item.toString());
                    }
                }
            } else {
                response.headers().set(key, value.toString());
            }
        });
    }

    private static Buffer toBuffer(Object result) {
        if (result == null) {
            return Buffer.buffer();
        } else if (result instanceof Buffer) {
            return (Buffer) result;
        } else if (result instanceof byte[]) {
            return Buffer.buffer((byte[]) result);
        } else if (result instanceof JsonObject) {
            return Buffer.buffer(((JsonObject) result).encode());
        } else if (result instanceof JsonArray) {
            return Buffer.buffer(((JsonArray) result).encode());
        } else if (result instanceof CharSequence) {
            return Buffer.buffer(result.toString());
        }

        return Buffer.buffer(result.toString());
    }

    private void handleDataException(RoutingContext routingContext, DataException dataException) {
        switch (dataException.failureCode()) {
        case DataException.FAILURE_CODE_NO_HANDLERS:
            routingContext.fail(NOT_FOUND.code());
            break;
        case DataException.FAILURE_CODE_TIMEOUT:
            routingContext.fail(GATEWAY_TIMEOUT.code());
            break;
        default:
            routingContext.fail(-1, dataException);
            break;
        }
    }

    private void handleFailure(RoutingContext routingContext, Throwable cause) {
        if (cause instanceof ReplyException replyException) {
            ReplyFailure failureType = replyException.failureType();
            if (failureType == ReplyFailure.NO_HANDLERS) {
                routingContext.fail(NOT_FOUND.code());
                return;
            } else if (failureType == ReplyFailure.TIMEOUT) {
                routingContext.fail(GATEWAY_TIMEOUT.code());
                return;
            }
        }

        routingContext.fail(-1, cause);
    }
}
