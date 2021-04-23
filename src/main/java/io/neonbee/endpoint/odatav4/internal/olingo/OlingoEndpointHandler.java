package io.neonbee.endpoint.odatav4.internal.olingo;

import static io.neonbee.endpoint.odatav4.ODataV4Endpoint.normalizeUri;
import static io.neonbee.internal.helper.BufferHelper.inputStreamToBuffer;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static org.apache.olingo.server.core.ODataHandlerException.MessageKeys.AMBIGUOUS_XHTTP_METHOD;
import static org.apache.olingo.server.core.ODataHandlerException.MessageKeys.HTTP_METHOD_NOT_ALLOWED;
import static org.apache.olingo.server.core.ODataHandlerException.MessageKeys.INVALID_HTTP_METHOD;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.apache.olingo.commons.api.ex.ODataRuntimeException;
import org.apache.olingo.commons.api.http.HttpHeader;
import org.apache.olingo.commons.api.http.HttpMethod;
import org.apache.olingo.server.api.OData;
import org.apache.olingo.server.api.ODataApplicationException;
import org.apache.olingo.server.api.ODataHandler;
import org.apache.olingo.server.api.ODataLibraryException;
import org.apache.olingo.server.api.ODataRequest;
import org.apache.olingo.server.api.ODataResponse;
import org.apache.olingo.server.api.ServiceMetadata;
import org.apache.olingo.server.core.ODataHandlerException;

import com.google.common.annotations.VisibleForTesting;

import io.neonbee.endpoint.odatav4.ODataV4Endpoint.NormalizedUri;
import io.neonbee.endpoint.odatav4.internal.olingo.processor.BatchProcessor;
import io.neonbee.endpoint.odatav4.internal.olingo.processor.CountEntityCollectionProcessor;
import io.neonbee.endpoint.odatav4.internal.olingo.processor.EntityProcessor;
import io.neonbee.endpoint.odatav4.internal.olingo.processor.PrimitiveProcessor;
import io.neonbee.internal.helper.BufferHelper.BufferInputStream;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;

public final class OlingoEndpointHandler implements Handler<RoutingContext> {
    private final ServiceMetadata serviceMetadata;

    /**
     * Convenience method as similar other Vert.x handler implementations (e.g. ErrorHandler)
     *
     * @param serviceMetadata The metadata of the service
     * @return A OlingoEndpointHandler instance
     */
    public static OlingoEndpointHandler create(ServiceMetadata serviceMetadata) {
        return new OlingoEndpointHandler(serviceMetadata);
    }

    private OlingoEndpointHandler(ServiceMetadata serviceMetadata) {
        this.serviceMetadata = serviceMetadata;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        // In case the OData request is asynchronously processed, the processor will complete the processPromise when
        // done, in case Olingo handles the request synchronously, the processPromise will be completed here
        Vertx vertx = routingContext.vertx();
        Promise<Void> processPromise = Promise.promise();
        vertx.<ODataResponse>executeBlocking(blockingPromise -> {
            OData odata = OData.newInstance();
            ODataHandler odataHandler = odata.createRawHandler(serviceMetadata);

            // add further built-in processors for NeonBee here (every processor must handle the processPromise)
            odataHandler.register(new CountEntityCollectionProcessor(vertx, routingContext, processPromise));
            odataHandler.register(new EntityProcessor(vertx, routingContext, processPromise));
            odataHandler.register(new BatchProcessor(vertx, routingContext, processPromise));
            odataHandler.register(new PrimitiveProcessor(vertx, routingContext, processPromise));

            try {
                ODataResponse odataResponse = odataHandler.process(mapToODataRequest(routingContext,
                        serviceMetadata.getEdm().getEntityContainer().getNamespace()));
                // check for synchronous processing, complete the processPromise in case a response body is set
                if ((odataResponse.getStatusCode() != INTERNAL_SERVER_ERROR.code())
                        || (odataResponse.getContent() != null) || (odataResponse.getODataContent() != null)) {
                    processPromise.tryComplete();
                }
                blockingPromise.complete(odataResponse);
            } catch (ODataLibraryException e) {
                blockingPromise.fail(e);
            }
        }, asyncODataResponse -> {
            // failed to map / process OData request, so fail the web request
            if (asyncODataResponse.failed()) {
                Throwable cause = asyncODataResponse.cause();
                routingContext.fail(getStatusCode(cause), cause);
                return;
            }

            // the contents of the odataResponse could still be null, in case the processing was done asynchronous, so
            // wait for the processPromise to finish before continuing processing
            ODataResponse odataResponse = asyncODataResponse.result();
            processPromise.future().onComplete(asyncResult -> {
                // (asynchronously) retrieving the odata response failed, so fail the web request
                if (asyncResult.failed()) {
                    Throwable cause = asyncResult.cause();
                    routingContext.fail(getStatusCode(cause), cause);
                    return;
                }

                try {
                    // map the odataResponse to the routingContext.response
                    mapODataResponse(odataResponse, routingContext.response());
                } catch (IOException | ODataRuntimeException e) {
                    routingContext.fail(-1, e);
                }
            });
        });
    }

    private static int getStatusCode(Throwable throwable) {
        return throwable instanceof ODataApplicationException ? ((ODataApplicationException) throwable).getStatusCode()
                : -1;
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
        odataRequest.setBody(new BufferInputStream(routingContext.getBody()));
        for (String header : request.headers().names()) {
            odataRequest.addHeader(header, request.headers().getAll(header));
        }

        /* @formatter:off *//*
         * The OData request is awaiting the following fields:
         *
         * rawRequestUri = http://localhost/my%20service/sys1/Employees?$format=json,$top=10
         * rawBaseUri = http://localhost/my%20service
         * rawServiceResolutionUri = sys1
         * rawODataPath = /Employees
         * rawQueryPath = $format=json,$top=10
         *
         * We can map these from the normalizedUri
         *//* @formatter:on */

        NormalizedUri normalizedUri = normalizeUri(routingContext, schemaNamespace);
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
    static HttpMethod mapODataRequestMethod(HttpServerRequest request) throws ODataLibraryException {
        HttpMethod odataRequestMethod;
        String rawMethod = request.method().name();
        try {
            odataRequestMethod = HttpMethod.valueOf(rawMethod);
        } catch (IllegalArgumentException e) {
            throw new ODataHandlerException("HTTP method not allowed" + rawMethod, e, HTTP_METHOD_NOT_ALLOWED,
                    rawMethod);
        }

        try { // in case it is a POST request, also consider the X-Http-Method and X-Http-Method-Override headers
            if (odataRequestMethod == HttpMethod.POST) {
                String xHttpMethod = request.getHeader(HttpHeader.X_HTTP_METHOD);
                String xHttpMethodOverride = request.getHeader(HttpHeader.X_HTTP_METHOD_OVERRIDE);

                if ((xHttpMethod == null) && (xHttpMethodOverride == null)) {
                    return odataRequestMethod;
                } else if (xHttpMethod == null) {
                    return HttpMethod.valueOf(xHttpMethodOverride);
                } else if (xHttpMethodOverride == null) {
                    return HttpMethod.valueOf(xHttpMethod);
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
