package io.neonbee.endpoint.odatav4;

import static io.neonbee.data.DataAction.CREATE;
import static io.neonbee.data.DataAction.DELETE;
import static io.neonbee.data.DataAction.READ;
import static io.neonbee.data.DataAction.UPDATE;
import static io.neonbee.endpoint.odatav4.ODataV4Endpoint.normalizeUri;
import static io.neonbee.internal.helper.BufferHelper.inputStreamToBuffer;
import static io.vertx.core.http.HttpMethod.GET;
import static io.vertx.core.http.HttpMethod.HEAD;
import static io.vertx.core.http.HttpMethod.PATCH;
import static io.vertx.core.http.HttpMethod.POST;
import static io.vertx.core.http.HttpMethod.PUT;
import static org.apache.olingo.server.core.ODataHandlerException.MessageKeys.AMBIGUOUS_XHTTP_METHOD;
import static org.apache.olingo.server.core.ODataHandlerException.MessageKeys.HTTP_METHOD_NOT_ALLOWED;
import static org.apache.olingo.server.core.ODataHandlerException.MessageKeys.INVALID_HTTP_METHOD;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.apache.olingo.commons.api.edm.EdmEntityType;
import org.apache.olingo.commons.api.http.HttpHeader;
import org.apache.olingo.commons.api.http.HttpMethod;
import org.apache.olingo.server.api.OData;
import org.apache.olingo.server.api.ODataApplicationException;
import org.apache.olingo.server.api.ODataLibraryException;
import org.apache.olingo.server.api.ODataRequest;
import org.apache.olingo.server.api.ODataResponse;
import org.apache.olingo.server.api.ServiceMetadata;
import org.apache.olingo.server.api.uri.UriInfo;
import org.apache.olingo.server.api.uri.UriResourceEntitySet;
import org.apache.olingo.server.core.ODataHandlerException;
import org.apache.olingo.server.core.uri.parser.Parser;
import org.apache.olingo.server.core.uri.parser.UriParserException;
import org.apache.olingo.server.core.uri.validator.UriValidationException;

import com.google.common.annotations.VisibleForTesting;

import io.neonbee.data.DataAction;
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

        ODataRequest odataRequest;
        DataQuery dataQuery;
        EdmEntityType entityType;

        try {
            odataRequest =
                    mapToODataRequest(routingContext, serviceMetadata.getEdm().getEntityContainer().getNamespace());
            DataAction action = mapMethodToAction(routingContext.request().method());
            dataQuery = odataRequestToQuery(odataRequest, action, routingContext.body().buffer());
            entityType = getEdmEntityType(odataRequest);
        } catch (Exception cause) {
            routingContext.fail(getStatusCode(cause), cause);
            return;
        }

        DataRequest dataRequest = new DataRequest(entityType.getFullQualifiedName(), dataQuery);
        Future<Buffer> bufferFuture = AbstractEntityVerticle.<Buffer>requestEntity(Buffer.class, routingContext.vertx(),
                dataRequest, new DataContextImpl(routingContext));
        bufferFuture.onSuccess(buffer -> {
            HttpServerResponse response = routingContext.response();
            response.end(buffer);
        }).onFailure(cause -> routingContext.fail(getStatusCode(cause), cause));

//        ODataResponse odataResponse;

//        // Determine the event address/full qualified name of the recipient verticle
//        String qualifiedName = determineQualifiedName(routingContext);
//        if (qualifiedName == null || qualifiedName.isEmpty()) {
//            routingContext.fail(BAD_REQUEST.code(),
//                    new IllegalArgumentException("Missing the full qualified verticle name"));
//            return;
//        }
//
//        HttpServerRequest request = routingContext.request();
//
//        // Create DataContext and add the origUrl into it
//        DataContextImpl context = new DataContextImpl(routingContext);
//        context.put(ORIG_URL_KEY, request.absoluteURI());
//        context.put(METHOD_KEY, request.method().name());
//
//        DataQuery query = buildDataQuery(routingContext, qualifiedName, request);
//
//        DeliveryOptions options = new DeliveryOptions().addHeader(CONTEXT_HEADER, encodeContextToString(context));
//        if (sendTimeout > 0) {
//            options.setSendTimeout(sendTimeout);
//        }
//
//        String address = EntityVerticle.getProxyAddress(qualifiedName);
//
//        routingContext.vertx().eventBus().<Object>request(address, query, options).onComplete(asyncResult -> {
//            if (asyncResult.failed()) {
//                handleFailure(routingContext, asyncResult.cause());
//                return;
//            }
//
//            Message<Object> reply = asyncResult.result();
//            mergeResponseContext(context, reply);
//
//            Object result = reply.body();
//            if (result instanceof DataException dataException) {
//                handleDataException(routingContext, dataException);
//                return;
//            }
//
//            HttpServerResponse response = routingContext.response();
//            applyResponseHeaders(response, context);
//
//            Buffer responseBuffer = toBuffer(result);
//            String contentType = Optional.ofNullable(context.responseData().get(CONTENT_TYPE_HINT))
//                    .map(Object::toString).filter(value -> !value.isBlank()).orElse(null);
//            if (contentType != null) {
//                response.putHeader(CONTENT_TYPE_HINT, contentType);
//            } else if (!response.headers().contains(CONTENT_TYPE_HINT)) {
//                response.putHeader(CONTENT_TYPE_HINT, "application/octet-stream");
//            }
//
//            int statusCode = Optional.ofNullable(context.responseData().get(STATUS_CODE_HINT))
//                    .filter(Number.class::isInstance).map(Number.class::cast).map(Number::intValue)
//                    .filter(code -> code > 0)
//                    .orElse(response.getStatusCode() > 0 ? response.getStatusCode() : OK.code());
//            response.setStatusCode(statusCode);
//
//            if (responseBuffer == null || responseBuffer.length() == 0) {
//                response.putHeader(HttpHeaders.CONTENT_LENGTH, "0");
//                response.end(Buffer.buffer());
//            } else {
//                response.end(responseBuffer);
//            }
//        });
    }

    private EdmEntityType getEdmEntityType(ODataRequest odataRequest)
            throws UriParserException, UriValidationException {
        OData odata = OData.newInstance();

        UriInfo uriInfo = new Parser(serviceMetadata.getEdm(), odata)
                .parseUri(
                        odataRequest.getRawODataPath(),
                        odataRequest.getRawQueryPath(),
                        null,
                        odataRequest.getRawBaseUri());

        UriResourceEntitySet uriResourceEntitySet = (UriResourceEntitySet) uriInfo.getUriResourceParts().get(0);
        return uriResourceEntitySet.getEntitySet().getEntityType();
    }

//    @Override
//    public void handle(RoutingContext routingContext) {
//        // In case the OData request is asynchronously processed, the processor will complete the processPromise when
//        // done, in case Olingo handles the request synchronously, the processPromise will be completed here
//        Vertx vertx = routingContext.vertx();
//        Promise<Void> processPromise = Promise.promise();
//        vertx.executeBlocking(() -> {
//            OData odata = OData.newInstance();
//            ODataHandler odataHandler = odata.createRawHandler(serviceMetadata);
//
//            // add further built-in processors for NeonBee here (every processor must handle the processPromise)
//            odataHandler.register(new CountEntityCollectionProcessor(vertx, routingContext, processPromise));
//            odataHandler.register(new EntityProcessor(vertx, routingContext, processPromise));
//            odataHandler.register(new BatchProcessor(vertx, routingContext, processPromise));
//            odataHandler.register(new PrimitiveProcessor(vertx, routingContext, processPromise));
//
//            ODataResponse odataResponse = odataHandler.process(mapToODataRequest(routingContext,
//                    serviceMetadata.getEdm().getEntityContainer().getNamespace()));
//            // check for synchronous processing, complete the processPromise in case a response body is set
//            if ((odataResponse.getStatusCode() != INTERNAL_SERVER_ERROR.code())
//                    || (odataResponse.getContent() != null) || (odataResponse.getODataContent() != null)) {
//                processPromise.tryComplete();
//            }
//            return odataResponse;
//        }).onComplete(asyncODataResponse -> {
//            // failed to map / process OData request, so fail the web request
//            if (asyncODataResponse.failed()) {
//                Throwable cause = asyncODataResponse.cause();
//                routingContext.fail(getStatusCode(cause), cause);
//                return;
//            }
//
//            // the contents of the odataResponse could still be null, in case the processing was done asynchronous, so
//            // wait for the processPromise to finish before continuing processing
//            ODataResponse odataResponse = asyncODataResponse.result();
//            processPromise.future().onComplete(asyncResult -> {
//                // (asynchronously) retrieving the odata response failed, so fail the web request
//                if (asyncResult.failed()) {
//                    Throwable cause = asyncResult.cause();
//                    routingContext.fail(getStatusCode(cause), cause);
//                    return;
//                }
//
//                try {
//                    // map the odataResponse to the routingContext.response
//                    mapODataResponse(odataResponse, routingContext.response());
//                } catch (IOException | ODataRuntimeException e) {
//                    routingContext.fail(-1, e);
//                }
//            });
//        });
//    }

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

    /**
     * Maps a ODataResponse to a existing Vert.x HttpServerResponse.
     *
     * @param odataResponse The ODataResponse to map
     * @param response      The HttpServerResponse to map to
     * @throws IOException IOException
     */
    @VisibleForTesting
    static void mapODataResponse(ODataResponse odataResponse, HttpServerResponse response) throws IOException {
        // status code and headers
        response.setStatusCode(odataResponse.getStatusCode());
        for (Map.Entry<String, List<String>> entry : odataResponse.getAllHeaders().entrySet()) {
            for (String headerValue : entry.getValue()) {
                response.putHeader(entry.getKey(), headerValue);
            }
        }
        // OData response content
        if (odataResponse.getContent() != null) {
            response.end(inputStreamToBuffer(odataResponse.getContent()));
        } else if (odataResponse.getODataContent() != null) {
            ByteArrayOutputStream byteArrayOutput = new ByteArrayOutputStream();
            odataResponse.getODataContent().write(byteArrayOutput);
            response.end(Buffer.buffer(byteArrayOutput.toByteArray()));
        } else {
            response.end(); // no content (e.g. for update / delete requests)
        }
    }
}
