package io.neonbee.endpoint.odatav4;

import static io.neonbee.data.DataAction.CREATE;
import static io.neonbee.data.DataAction.DELETE;
import static io.neonbee.data.DataAction.READ;
import static io.neonbee.data.DataAction.UPDATE;
import static io.neonbee.endpoint.odatav4.ODataV4Endpoint.normalizeUri;
import static io.vertx.core.http.HttpMethod.GET;
import static io.vertx.core.http.HttpMethod.HEAD;
import static io.vertx.core.http.HttpMethod.PATCH;
import static io.vertx.core.http.HttpMethod.POST;
import static io.vertx.core.http.HttpMethod.PUT;
import static org.apache.olingo.server.core.ODataHandlerException.MessageKeys.AMBIGUOUS_XHTTP_METHOD;
import static org.apache.olingo.server.core.ODataHandlerException.MessageKeys.HTTP_METHOD_NOT_ALLOWED;
import static org.apache.olingo.server.core.ODataHandlerException.MessageKeys.INVALID_HTTP_METHOD;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.olingo.commons.api.edm.FullQualifiedName;
import org.apache.olingo.commons.api.http.HttpHeader;
import org.apache.olingo.commons.api.http.HttpMethod;
import org.apache.olingo.server.api.ODataApplicationException;
import org.apache.olingo.server.api.ODataLibraryException;
import org.apache.olingo.server.api.ODataRequest;
import org.apache.olingo.server.api.ServiceMetadata;
import org.apache.olingo.server.core.ODataHandlerException;

import com.google.common.annotations.VisibleForTesting;

import io.neonbee.data.DataAction;
import io.neonbee.data.DataContext;
import io.neonbee.data.DataQuery;
import io.neonbee.data.DataRequest;
import io.neonbee.data.internal.DataContextImpl;
import io.neonbee.entity.AbstractEntityVerticle;
import io.neonbee.internal.helper.BufferHelper;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;

public final class ODataProxyEndpointHandler implements Handler<RoutingContext> {

    // Regex to find last path segment
    private static final Pattern ENTITY_NAME_PATTERN =
            Pattern.compile("/(\\w+)(?:\\(.*\\))?(?:/(\\w+))?$");

    private final ServiceMetadata serviceMetadata;

    /**
     * Returns the ODataProxyEndpointHandler.
     *
     * @param serviceMetadata The metadata of the service
     */
    public ODataProxyEndpointHandler(ServiceMetadata serviceMetadata) {
        this.serviceMetadata = serviceMetadata;
    }

    private DataAction mapMethodToAction(io.vertx.core.http.HttpMethod method) {
        if (POST.equals(method)) {
            return CREATE;
        } else if (HEAD.equals(method) || GET.equals(method)) {
            return READ;
        } else if (PUT.equals(method) || PATCH.equals(method)) {
            return UPDATE;
        } else if (io.vertx.core.http.HttpMethod.DELETE.equals(method)) {
            return DELETE;
        } else {
            return null;
        }
    }

    @SuppressWarnings("unused") // normale Java-Warnung
    @Override
    public void handle(RoutingContext routingContext) {

        DataQuery dataQuery;
        FullQualifiedName fullQualifiedName;

        try {
            ODataRequest odataRequest = mapToODataRequest(
                    routingContext,
                    serviceMetadata.getEdm().getEntityContainer().getNamespace());

            DataAction action = mapMethodToAction(routingContext.request().method());
            dataQuery = odataRequestToQuery(odataRequest, action, routingContext.body().buffer());
            fullQualifiedName = getFullQualifiedName(odataRequest);
        } catch (Exception cause) {
            routingContext.fail(getStatusCode(cause), cause);
            return;
        }

        DataRequest dataRequest = new DataRequest(fullQualifiedName, dataQuery);
        DataContextImpl context = new DataContextImpl(routingContext);
        Future<Buffer> bufferFuture = AbstractEntityVerticle.requestEntity(
                Buffer.class,
                routingContext.vertx(),
                dataRequest,
                context);

        bufferFuture.onSuccess(buffer -> {
            HttpServerResponse response = routingContext.response();
            copyResponseDataToHttpResponse(context, response);
            response.end(buffer);
        }).onFailure(cause -> routingContext.fail(getStatusCode(cause), cause));
    }

    /**
     * Copy all entries from {@code context.responseData()} into the provided {@code HttpServerResponse} headers.
     *
     * @param context  the data context containing the response data map; may be null
     * @param response the HTTP response where headers will be written; may be null
     */
    private static void copyResponseDataToHttpResponse(DataContext context, HttpServerResponse response) {
        Map<String, Object> respData = context.responseData();
        for (Map.Entry<String, Object> e : respData.entrySet()) {
            String name = e.getKey();
            Object value = e.getValue();

            if(DataContext.STATUS_CODE_HINT.equals(name)) {
                response.setStatusCode((Integer) value);
                continue;
            }

            if (value instanceof Iterable<?>) {
                for (Object v : (Iterable<?>) value) {
                    if (v != null) {
                        response.putHeader(name, v.toString());
                    }
                }
            } else {
                response.putHeader(name, value.toString());
            }
        }
    }

    /**
     * Retrieves the EdmEntityType from the ODataRequest.
     *
     * @param odataRequest The ODataRequest
     * @return The FullQualifiedName
     */
    private FullQualifiedName getFullQualifiedName(ODataRequest odataRequest) {
        Matcher matcher = ENTITY_NAME_PATTERN.matcher(odataRequest.getRawODataPath());
        if (matcher.matches()) {
            return new FullQualifiedName(odataRequest.getRawServiceResolutionUri(), matcher.group(1));
        } else {
            throw new IllegalArgumentException("Cannot determine entity name from OData path: "
                    + odataRequest.getRawODataPath());
        }
    }

    private static DataQuery odataRequestToQuery(ODataRequest request, DataAction action, Buffer body) {
        // the uriPath without /odata root path and without query path
        String uriPath = "/" + request.getRawServiceResolutionUri() + request.getRawODataPath();
        // the raw query path
        Map<String, List<String>> stringListMap = DataQuery.parseEncodedQueryString(request.getRawQueryPath());
        return new DataQuery(action, uriPath, stringListMap, request.getAllHeaders(), body).addHeader("X-HTTP-Method",
                request.getMethod().name());
    }

    private static int getStatusCode(Throwable throwable) {
        return throwable instanceof ODataApplicationException odae ? odae.getStatusCode() : -1;
    }

    /**
     * Maps a Vert.x RoutingContext into a new ODataRequest.
     *
     * @param routingContext  the context for the handling of the HTTP request
     * @param schemaNamespace the name of the service namespace
     * @return A new ODataRequest
     * @throws ODataLibraryException ODataLibraryException
     */
    @VisibleForTesting
    static ODataRequest mapToODataRequest(RoutingContext routingContext, String schemaNamespace)
            throws ODataLibraryException {
        HttpServerRequest request = routingContext.request();

        ODataRequest odataRequest = new ODataRequest();
        odataRequest.setProtocol(request.scheme());
        odataRequest.setMethod(mapODataRequestMethod(request));
        odataRequest.setBody(new BufferHelper.BufferInputStream(routingContext.body().buffer()));
        for (String header : request.headers().names()) {
            odataRequest.addHeader(header, request.headers().getAll(header));
        }

        /* @formatter:off *//*
         * The OData request is awaiting the following fields:
         *
         * rawRequestUri = http://localhost/odata/sys1/Employees?$format=json,$top=10
         * rawBaseUri = http://localhost/odata/
         * rawServiceResolutionUri = sys1
         * rawODataPath = /Employees
         * rawQueryPath = $format=json,$top=10
         *
         * We can map these from the normalizedUri
         *//* @formatter:on */

        ODataV4Endpoint.NormalizedUri normalizedUri = normalizeUri(routingContext, schemaNamespace);
        odataRequest.setRawRequestUri(normalizedUri.requestUri);
        odataRequest.setRawBaseUri(normalizedUri.baseUri);
        odataRequest.setRawServiceResolutionUri(normalizedUri.schemaNamespace);
        odataRequest.setRawODataPath(normalizedUri.resourcePath);
        odataRequest.setRawQueryPath(normalizedUri.requestQuery);

        return odataRequest;
    }

    /**
     * Maps the Vert.x HttpServerRequest method to a valid OData HttpMethod, considering the request method, the
     * X-HTTP-Method and X-HTTP-Method-Override headers.
     *
     * @param request The incoming HttpRequest to map
     * @return A valid OData HttpMethod
     * @throws ODataLibraryException ODataLibraryException
     */
    @VisibleForTesting
    @SuppressWarnings("checkstyle:LocalVariableName")
    static org.apache.olingo.commons.api.http.HttpMethod mapODataRequestMethod(HttpServerRequest request)
            throws ODataLibraryException {
        org.apache.olingo.commons.api.http.HttpMethod odataRequestMethod;
        String rawMethod = request.method().name();
        try {
            odataRequestMethod = org.apache.olingo.commons.api.http.HttpMethod.valueOf(rawMethod);
        } catch (IllegalArgumentException e) {
            throw new ODataHandlerException("HTTP method not allowed" + rawMethod, e, HTTP_METHOD_NOT_ALLOWED,
                    rawMethod);
        }

        try { // in case it is a POST request, also consider the X-Http-Method and X-Http-Method-Override headers
            if (odataRequestMethod == org.apache.olingo.commons.api.http.HttpMethod.POST) {
                String xHttpMethod = request.getHeader(HttpHeader.X_HTTP_METHOD);
                String xHttpMethodOverride = request.getHeader(HttpHeader.X_HTTP_METHOD_OVERRIDE);

                if ((xHttpMethod == null) && (xHttpMethodOverride == null)) {
                    return odataRequestMethod;
                } else if (xHttpMethod == null) {
                    return org.apache.olingo.commons.api.http.HttpMethod.valueOf(xHttpMethodOverride);
                } else if (xHttpMethodOverride == null) {
                    return org.apache.olingo.commons.api.http.HttpMethod.valueOf(xHttpMethod);
                } else {
                    if (!xHttpMethod.equalsIgnoreCase(xHttpMethodOverride)) {
                        throw new ODataHandlerException("Ambiguous X-HTTP-Methods", AMBIGUOUS_XHTTP_METHOD, xHttpMethod,
                                xHttpMethodOverride);
                    }
                    return HttpMethod.valueOf(xHttpMethod);
                }
            } else {
                return odataRequestMethod;
            }
        } catch (IllegalArgumentException e) {
            throw new ODataHandlerException("Invalid HTTP method" + rawMethod, e, INVALID_HTTP_METHOD, rawMethod);
        }
    }
}
