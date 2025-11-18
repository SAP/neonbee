package io.neonbee.endpoint.odatav4;

import static io.neonbee.endpoint.HttpMethodToDataActionMapper.mapMethodToAction;
import static io.neonbee.endpoint.odatav4.internal.olingo.OlingoEndpointHandler.getStatusCode;
import static io.neonbee.endpoint.odatav4.internal.olingo.OlingoEndpointHandler.mapToODataRequest;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.olingo.commons.api.edm.FullQualifiedName;
import org.apache.olingo.commons.api.ex.ODataRuntimeException;
import org.apache.olingo.server.api.OData;
import org.apache.olingo.server.api.ODataRequest;
import org.apache.olingo.server.api.ODataResponse;
import org.apache.olingo.server.api.ServiceMetadata;

import io.neonbee.data.DataAction;
import io.neonbee.data.DataContext;
import io.neonbee.data.DataQuery;
import io.neonbee.data.DataRequest;
import io.neonbee.data.internal.DataContextImpl;
import io.neonbee.endpoint.odatav4.internal.olingo.OlingoEndpointHandler;
import io.neonbee.entity.AbstractEntityVerticle;
import io.neonbee.logging.LoggingFacade;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RequestBody;
import io.vertx.ext.web.RoutingContext;

public final class ODataProxyEndpointHandler implements Handler<RoutingContext> {

    private static final LoggingFacade LOGGER = LoggingFacade.create();

    // Regex to find last path segment
    private static final Pattern ENTITY_NAME_PATTERN =
            Pattern.compile("/(\\w+)(?:\\([^)]*\\))?(?:/(\\w+))?$");

    private static final String BASE_PATH_SEGMENT = "odataproxy";

    private final ServiceMetadata serviceMetadata;

//    /**
//     * All instances (optional, V4.01)
//     */
//    private final String allPath;

    /**
     * Overview of available resources
     */
    private final String availableResourcesPath;

    /**
     * Multiple operations in one request
     */
    private final String batchRequestPath;

//    /**
//     * Join entity sets
//     */
//    private final String crossjoinentityPath;
//
//    /**
//     * Retrieve single entity raw
//     */
//    private final String entityPath;

    /**
     * EDM model
     */
    private final String metadataRequestPath;

    /**
     * Returns the ODataProxyEndpointHandler.
     *
     * @param serviceMetadata The metadata of the service
     * @param uriConversion   The URI conversion strategy
     */
    public ODataProxyEndpointHandler(ServiceMetadata serviceMetadata, ODataV4Endpoint.UriConversion uriConversion) {
        this.serviceMetadata = serviceMetadata;
        String requestNamespace = uriConversion.apply(serviceMetadata.getEdm().getEntityContainer().getNamespace());
//        this.allPath = requestPath(requestNamespace, "$all");
        this.availableResourcesPath = requestPath(requestNamespace, "");
        this.batchRequestPath = requestPath(requestNamespace, "$batch");
//        this.crossjoinentityPath = requestPath(requestNamespace, "$crossjoin"); // (A,B);
//        this.entityPath = requestPath(requestNamespace, "$entity");
        this.metadataRequestPath = requestPath(requestNamespace, "$metadata");
    }

    private static String requestPath(String requestNamespace, String function) {
        return String.format("/%s/%s/%s", BASE_PATH_SEGMENT, requestNamespace, function);
    }

    @SuppressWarnings("unused") // normale Java-Warnung
    @Override
    public void handle(RoutingContext routingContext) {

        String path = routingContext.request().path();
        if (batchRequestPath.equals(path)) {
            handleBatch(routingContext);
            return;
        }

        if (availableResourcesPath.equals(path)
                || metadataRequestPath.equals(path)) {
            handleMetadata(routingContext);
            return;
        }

        DataQuery dataQuery;
        FullQualifiedName fullQualifiedName;

        try {
            String namespace = serviceMetadata.getEdm().getEntityContainer().getNamespace();
            ODataRequest odataRequest = mapToODataRequest(routingContext, namespace);

            DataAction action = mapMethodToAction(routingContext.request().method());
            Buffer body = Optional.ofNullable(routingContext.body())
                    .map(RequestBody::buffer)
                    .orElse(Buffer.buffer());
            dataQuery = odataRequestToQuery(odataRequest, action, body);
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

    private void handleBatch(RoutingContext routingContext) {
        LOGGER.warn("Batch request path {}", routingContext.request().path());
    }

    /**
     * Handles the $metadata request.
     *
     * @param routingContext The routing context
     */
    public void handleMetadata(RoutingContext routingContext) {
        Vertx vertx = routingContext.vertx();
        vertx.executeBlocking(() -> {
            OData odata = OData.newInstance();
            String namespace = serviceMetadata.getEdm().getEntityContainer().getNamespace();
            return odata.createRawHandler(serviceMetadata)
                    .process(mapToODataRequest(routingContext, namespace));
        }).onComplete(asyncODataResponse -> {
            if (asyncODataResponse.failed()) {
                Throwable cause = asyncODataResponse.cause();
                routingContext.fail(getStatusCode(cause), cause);
                return;
            }

            ODataResponse odataResponse = asyncODataResponse.result();
            try {
                // map the odataResponse to the routingContext.response
                OlingoEndpointHandler.mapODataResponse(odataResponse, routingContext.response());
            } catch (IOException | ODataRuntimeException e) {
                routingContext.fail(-1, e);
            }
        });
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

            if (DataContext.STATUS_CODE_HINT.equals(name)) {
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
}
