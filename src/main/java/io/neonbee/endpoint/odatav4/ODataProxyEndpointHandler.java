package io.neonbee.endpoint.odatav4;

import static io.neonbee.endpoint.HttpMethodToDataActionMapper.mapMethodToAction;
import static io.neonbee.endpoint.odatav4.internal.olingo.OlingoEndpointHandler.mapToODataRequest;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.apache.olingo.commons.api.edm.FullQualifiedName;
import org.apache.olingo.commons.api.ex.ODataRuntimeException;
import org.apache.olingo.commons.api.format.ContentType;
import org.apache.olingo.commons.api.http.HttpHeader;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.server.api.OData;
import org.apache.olingo.server.api.ODataRequest;
import org.apache.olingo.server.api.ODataResponse;
import org.apache.olingo.server.api.ServiceMetadata;
import org.apache.olingo.server.api.deserializer.batch.BatchOptions;
import org.apache.olingo.server.api.deserializer.batch.BatchRequestPart;
import org.apache.olingo.server.api.deserializer.batch.ODataResponsePart;
import org.apache.olingo.server.api.serializer.FixedFormatSerializer;
import org.apache.olingo.server.core.deserializer.FixedFormatDeserializerImpl;
import org.apache.olingo.server.core.deserializer.batch.BatchParserCommon;
import org.apache.olingo.server.core.serializer.FixedFormatSerializerImpl;

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
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
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

    /**
     * Overview of available resources.
     */
    private final String availableResourcesPath;

    /**
     * Multiple operations in one request.
     */
    private final String batchRequestPath;

    /**
     * EDM model.
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
        this.availableResourcesPath = requestPath(requestNamespace, "");
        this.batchRequestPath = requestPath(requestNamespace, "$batch");
        this.metadataRequestPath = requestPath(requestNamespace, "$metadata");
    }

    private static String requestPath(String requestNamespace, String function) {
        return String.format("/%s/%s/%s", BASE_PATH_SEGMENT, requestNamespace, function);
    }

    @SuppressWarnings("unused") // normale Java-Warnung
    @Override
    public void handle(RoutingContext routingContext) {

        HttpServerRequest request = routingContext.request();
        String path = request.path();

        if (availableResourcesPath.equals(path)
                || metadataRequestPath.equals(path)) {
            handleMetadataRequest(routingContext);
            return;
        }

        DataQuery dataQuery;
        ODataRequest odataRequest;

        try {
            DataAction action = mapMethodToAction(request.method());
            Buffer body = Optional.ofNullable(routingContext.body())
                    .map(RequestBody::buffer)
                    .orElse(Buffer.buffer());
            String namespace = serviceMetadata.getEdm().getEntityContainer().getNamespace();
            odataRequest = mapToODataRequest(routingContext, namespace);
            dataQuery = odataRequestToQuery(odataRequest, action, body);
        } catch (Exception cause) {
            routingContext.fail(getStatusCode(cause), cause);
            return;
        }

        DataContext context = new DataContextImpl(routingContext);
        Future<Buffer> bufferFuture;
        if (HttpMethod.POST.equals(request.method()) && batchRequestPath.equals(path)) {
            bufferFuture = handleBatchRequest(routingContext, context, odataRequest, dataQuery);
        } else {
            bufferFuture = handleEntityRequest(routingContext, context, odataRequest, dataQuery);
        }

        bufferFuture.onSuccess(buffer -> {
            HttpServerResponse response = routingContext.response();
            setHeaderValues(context.responseData(), response::putHeader);
            response.setStatusCode(getStatusCode(context.responseData()));
            response.end(buffer);
        }).onFailure(cause -> {
            LOGGER.correlateWith(routingContext).error("Error processing OData request", cause);
            routingContext.fail(getStatusCode(cause), cause);
        });
    }

    static Future<Buffer> handleEntityRequest(
            RoutingContext routingContext,
            DataContext context,
            ODataRequest odataRequest,
            DataQuery dataQuery) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.correlateWith(routingContext).debug("Entity request path {}", routingContext.request().path());
        }

        FullQualifiedName fullQualifiedName = getFullQualifiedName(odataRequest);
        DataRequest dataRequest = new DataRequest(fullQualifiedName, dataQuery);
        return AbstractEntityVerticle.requestEntity(Buffer.class, routingContext.vertx(), dataRequest, context);
    }

    /**
     * Handles entity requests. We do NOT support change sets os far!
     *
     * @param routingContext The routing context
     * @param context        The data context
     * @param odataRequest   The OData request
     * @param dataQuery      The data query
     * @return A future with the result buffer
     */
    static Future<Buffer> handleBatchRequest(
            RoutingContext routingContext,
            DataContext context,
            ODataRequest odataRequest,
            DataQuery dataQuery) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.correlateWith(routingContext).debug("Batch request path {}", routingContext.request().path());
        }

        String requestContentType = odataRequest.getHeader(HttpHeader.CONTENT_TYPE);
        List<Future<ODataResponsePart>> responseParts;
        try {
            String boundary = BatchParserCommon.getBoundary(requestContentType, 0);
            BatchOptions options = BatchOptions.with().rawBaseUri(odataRequest.getRawBaseUri())
                    .rawServiceResolutionUri(odataRequest.getRawServiceResolutionUri()).build();

            List<BatchRequestPart> requestParts = new FixedFormatDeserializerImpl()
                    .parseBatchRequest(odataRequest.getBody(), boundary, options);

            responseParts = requestParts.stream().map(BatchRequestPart::getRequests)
                    .flatMap(Collection::stream)
                    .map(req -> getODataResponsePart(routingContext, context, req))
                    .toList();
        } catch (Exception e) {
            return Future.failedFuture(e);
        }

        return Future.all(responseParts)
                .map(response -> {
                    // set headers and status code
                    String responseBoundary = "batch_" + UUID.randomUUID();

                    String responseContentType = ContentType.MULTIPART_MIXED + ";boundary=" + responseBoundary;
                    context.responseData().put(HttpHeader.CONTENT_TYPE, responseContentType);
                    context.responseData().put(DataContext.STATUS_CODE_HINT,
                            HttpStatusCode.ACCEPTED.getStatusCode());

                    List<ODataResponsePart> list = responseParts.stream().map(Future::result).toList();
                    byte[] bytes;
                    FixedFormatSerializer fixedFormatSerializer = new FixedFormatSerializerImpl();
                    try (InputStream responseContent = fixedFormatSerializer.batchResponse(list, responseBoundary)) {
                        bytes = responseContent.readAllBytes();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }

                    return Buffer.buffer(bytes);
                });
    }

    /**
     * Handles the $metadata request.
     *
     * @param routingContext The routing context
     * @return A future with the OData response
     */
    Future<ODataResponse> handleMetadataRequest(RoutingContext routingContext) {
        Vertx vertx = routingContext.vertx();
        return vertx.executeBlocking(() -> {
            OData odata = OData.newInstance();
            String namespace = serviceMetadata.getEdm().getEntityContainer().getNamespace();
            return odata.createRawHandler(serviceMetadata)
                    .process(mapToODataRequest(routingContext, namespace));
        }).onComplete(asyncResult -> {
            if (asyncResult.failed()) {
                Throwable cause = asyncResult.cause();
                routingContext.fail(getStatusCode(cause), cause);
                return;
            }

            ODataResponse odataResponse = asyncResult.result();
            try {
                // map the odataResponse to the routingContext.response
                OlingoEndpointHandler.mapODataResponse(odataResponse, routingContext.response());
            } catch (IOException | ODataRuntimeException e) {
                routingContext.fail(-1, e);
            }
        });
    }

    static Future<ODataResponsePart> getODataResponsePart(
            RoutingContext routingContext,
            DataContext context,
            ODataRequest odataRequest) {
        Buffer body;
        try {
            body = Buffer.buffer(odataRequest.getBody().readAllBytes());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        DataContext contextCopy = context.copy();
        DataAction action = mapMethodToAction(odataRequest.getMethod());
        DataQuery partDataQuery = odataRequestToQuery(odataRequest, action, body);
        return handleEntityRequest(routingContext, contextCopy, odataRequest, partDataQuery)
                .map(buffer -> createODataResponse(contextCopy, buffer))
                .map(odataResponse -> new ODataResponsePart(odataResponse, false));
    }

    /**
     * Creates an ODataResponse from the DataContext and Buffer.
     *
     * @param context The data context
     * @param buffer  The response buffer
     * @return The ODataResponse
     */
    static ODataResponse createODataResponse(DataContext context, Buffer buffer) {
        ODataResponse response = new ODataResponse();
        Map<String, Object> respData = context.responseData();
        response.setStatusCode(getStatusCode(respData, HttpStatusCode.ACCEPTED.getStatusCode()));

        // Because org.apache.olingo.server.api.ODataResponse.addHeader(java.lang.String, java.lang.String) overrides
        // the previous value, we need to use
        // org.apache.olingo.server.api.ODataResponse.addHeader(java.lang.String, java.util.List<java.lang.String>)
        // see: https://github.com/apache/olingo-odata4/pull/174
        setHeaderValues(respData, (k, v) -> response.addHeader(k, List.of(v)));
        response.setContent(new ByteArrayInputStream(buffer.getBytes()));
        return response;
    }

    /**
     * Retrieves the EdmEntityType from the ODataRequest.
     *
     * @param odataRequest The ODataRequest
     * @return The FullQualifiedName
     */
    static FullQualifiedName getFullQualifiedName(ODataRequest odataRequest) {
        Matcher matcher = ENTITY_NAME_PATTERN.matcher(odataRequest.getRawODataPath());
        if (matcher.matches()) {
            return new FullQualifiedName(odataRequest.getRawServiceResolutionUri(), matcher.group(1));
        } else {
            throw new IllegalArgumentException("Cannot determine entity name from OData path: "
                    + odataRequest.getRawODataPath());
        }
    }

    static DataQuery odataRequestToQuery(ODataRequest request, DataAction action, Buffer body) {
        // the uriPath without /odata root path and without query path
        String uriPath = "/" + request.getRawServiceResolutionUri() + request.getRawODataPath();
        // the raw query path
        Map<String, List<String>> stringListMap = DataQuery.parseEncodedQueryString(request.getRawQueryPath());
        return new DataQuery(action, uriPath, stringListMap, request.getAllHeaders(), body)
                .addHeader("X-HTTP-Method", request.getMethod().name());
    }

    /**
     * Sets the header values from the response data map using the provided operator.
     *
     * @param respData               The response data map
     * @param addHeaderValueOperator The operator to add header values. This operator can be called multiple times for
     *                               with same header key with different values.
     */
    static void setHeaderValues(Map<String, Object> respData, BiConsumer<String, String> addHeaderValueOperator) {
        respData.entrySet().stream()
                .filter(e -> !DataContext.STATUS_CODE_HINT.equals(e.getKey()))
                .flatMap(e -> {
                    String key = e.getKey();
                    Object value = e.getValue();
                    if (value instanceof Iterable<?> iterable) {
                        return StreamSupport.stream(iterable.spliterator(), false)
                                .map(v -> new AbstractMap.SimpleEntry<>(key, v));
                    } else {
                        return Stream.of(new AbstractMap.SimpleEntry<>(key, value));
                    }
                }).forEach(e -> {
                    String value = e.getValue() == null ? null : e.getValue().toString();
                    addHeaderValueOperator.accept(e.getKey(), value);
                });
    }

    /**
     * Retrieves the status code from the response data map.
     *
     * @param respData The response data map
     * @return The status code
     */
    static Integer getStatusCode(Map<String, Object> respData) {
        return getStatusCode(respData, -1);
    }

    /**
     * Retrieves the status code from the response data map.
     *
     * @param respData    The response data map
     * @param defaultCode The default status code
     * @return The status code
     */
    static Integer getStatusCode(Map<String, Object> respData, int defaultCode) {
        return (Integer) respData.getOrDefault(DataContext.STATUS_CODE_HINT, defaultCode);
    }

    /**
     * Retrieves the status code from the Throwable cause.
     *
     * @param cause The Throwable cause
     * @return The status code
     */
    static int getStatusCode(Throwable cause) {
        return OlingoEndpointHandler.getStatusCode(cause);
    }
}
