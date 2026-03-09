package io.neonbee.endpoint.odatav4;

import static io.neonbee.data.DataVerticle.requestData;
import static io.neonbee.endpoint.HttpMethodToDataActionMapper.mapMethodToAction;
import static io.neonbee.endpoint.odatav4.internal.olingo.OlingoEndpointHandler.mapToODataRequest;
import static io.neonbee.internal.helper.BufferHelper.inputStreamToBuffer;
import static io.neonbee.internal.helper.CollectionHelper.multiMapToMap;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.AbstractMap;
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
import org.apache.olingo.server.api.ODataLibraryException;
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
import io.neonbee.endpoint.odatav4.rawbatch.RawBatchDecision;
import io.neonbee.endpoint.odatav4.rawbatch.RawBatchResult;
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

@SuppressWarnings("PMD.GodClass")
public final class ODataProxyEndpointHandler implements Handler<RoutingContext> {
    /**
     * Context key for the stored request body when a raw batch request is intercepted (for re-entry via
     * handleDefaultODataProxyProcessing).
     */
    public static final String CONTEXT_KEY_RAW_BATCH_BODY =
            ODataProxyEndpointHandler.class.getName() + ".rawBatchBody";

    private static final LoggingFacade LOGGER = LoggingFacade.create();

    // Regex to find last path segment
    private static final Pattern ENTITY_NAME_PATTERN =
            Pattern.compile("/(\\w+)(?:\\([^)]*\\))?(?:/(\\w+))?$");

    private static final String BASE_PATH_SEGMENT = "odataproxy";

    private static final String ENTITY_CONTAINER_SUFFIX = ".Service";

    private final ServiceMetadata serviceMetadata;

    /**
     * Overview of available resources.
     */
    private final String availableResourcesPath;

    /**
     * EDM model.
     */
    private final String metadataRequestPath;

    /**
     * Optional map: schema namespace -> DataVerticle qualified name. Key is full EDM entity container namespace (e.g.
     * customerengagement.Service) or short form without ".Service" (fallback). Empty map means no raw batch
     * interception.
     */
    private final Map<String, String> rawBatchProcessing;

    /**
     * Returns the ODataProxyEndpointHandler.
     *
     * @param serviceMetadata The metadata of the service
     * @param uriConversion   The URI conversion strategy
     */
    public ODataProxyEndpointHandler(ServiceMetadata serviceMetadata, ODataV4Endpoint.UriConversion uriConversion) {
        this(serviceMetadata, uriConversion, Map.of());
    }

    /**
     * Returns the ODataProxyEndpointHandler with optional raw batch processing config.
     *
     * @param serviceMetadata    The metadata of the service
     * @param uriConversion      The URI conversion strategy
     * @param rawBatchProcessing Map of schema namespace to DataVerticle qualified name for raw batch interception (may
     *                           be null or empty)
     */
    public ODataProxyEndpointHandler(ServiceMetadata serviceMetadata, ODataV4Endpoint.UriConversion uriConversion,
            Map<String, String> rawBatchProcessing) {
        this.serviceMetadata = serviceMetadata;
        this.rawBatchProcessing = rawBatchProcessing != null ? rawBatchProcessing : Map.of();
        String requestNamespace = uriConversion.apply(serviceMetadata.getEdm().getEntityContainer().getNamespace());
        this.availableResourcesPath = requestPath(requestNamespace, "");
        this.metadataRequestPath = requestPath(requestNamespace, "$metadata");
    }

    @SuppressWarnings("unused") // normale Java-Warnung
    @Override
    public void handle(RoutingContext routingContext) {
        DataContext context = new DataContextImpl(routingContext);
        HttpServerRequest request = routingContext.request();

        Future<Buffer> result;
        if (isMetadataRequest(request.path())) {
            result = handleMetadataRequest(routingContext, context);
        } else if (isBatchRequest(request)) {
            result = handleBatchRouting(routingContext, context);
        } else {
            result = handleEntityRequest(routingContext, context);
        }

        completeResponse(routingContext, context, result);
    }

    private boolean isMetadataRequest(String path) {
        return availableResourcesPath.equals(path) || metadataRequestPath.equals(path);
    }

    private boolean isBatchRequest(HttpServerRequest request) {
        return HttpMethod.POST.equals(request.method()) && request.path().contains("$batch");
    }

    private boolean isRawBatchEnabled() {
        return !rawBatchProcessing.isEmpty();
    }

    private Future<Buffer> handleBatchRouting(RoutingContext routingContext, DataContext context) {
        if (!isRawBatchEnabled()) {
            return executeDefaultBatch(routingContext, context);
        }

        DataRequest dataRequest = mapRawBatchRequest(routingContext);
        if (dataRequest == null) {
            return executeDefaultBatch(routingContext, context);
        }

        routingContext.put(CONTEXT_KEY_RAW_BATCH_BODY, getBody(routingContext));
        return requestData(routingContext.vertx(), dataRequest, context)
                .compose(result -> {
                    if (result == null) {
                        return Future.failedFuture("Raw batch verticle returned null");
                    }
                    if (!(result instanceof RawBatchResult rawResult)) {
                        return Future.failedFuture("Unsupported raw batch response type: " + result);
                    }
                    return processRawBatchDecision(routingContext, context, rawResult);
                });
    }

    /**
     * Maps the current batch request to a DataRequest for the configured raw batch verticle, or null if none.
     */
    private DataRequest mapRawBatchRequest(RoutingContext routingContext) {
        String verticleName = resolveRawBatchVerticle();
        if (verticleName == null) {
            return null;
        }
        Map<String, List<String>> queryParams;
        HttpServerRequest request = routingContext.request();
        try {
            queryParams = DataQuery.parseEncodedQueryString(request.query());
        } catch (IllegalArgumentException e) {
            return null;
        }
        String path = request.path();
        DataAction action = mapMethodToAction(request.method());
        Map<String, List<String>> headers = multiMapToMap(request.headers());
        Buffer body = getBody(routingContext);
        DataQuery dataQuery = new DataQuery(action, path, queryParams, headers, body)
                .addHeader("X-HTTP-Method", request.method().name());
        return new DataRequest(verticleName, dataQuery);
    }

    private Future<Buffer> processRawBatchDecision(RoutingContext routingContext, DataContext context,
            RawBatchResult result) {
        if (result.hasBuffer()) {
            return Future.succeededFuture(result.buffer());
        }

        RawBatchDecision decision = result.decision();

        if (decision == RawBatchDecision.HANDLED_RAW) {
            return Future.succeededFuture(Buffer.buffer());
        }

        if (decision == RawBatchDecision.DELEGATE_TO_DEFAULT) {
            return executeDefaultBatch(routingContext, context);
        }

        return Future.failedFuture("Unsupported raw batch result: " + decision);
    }

    private Future<Buffer> executeDefaultBatch(RoutingContext routingContext, DataContext context) {
        String namespace = serviceMetadata.getEdm().getEntityContainer().getNamespace();
        Buffer bodyOverride = routingContext.get(CONTEXT_KEY_RAW_BATCH_BODY);
        ODataRequest odataRequest;
        try {
            odataRequest = bodyOverride != null
                    ? mapToODataRequest(routingContext, namespace, bodyOverride)
                    : mapToODataRequest(routingContext, namespace);
        } catch (ODataLibraryException e) {
            return Future.failedFuture(e);
        }
        return handleBatchRequest(routingContext, context, odataRequest, null);
    }

    /**
     * Resolves the DataVerticle name for raw batch processing by mapping the current service's EDM entity container
     * namespace to the configured verticle name. Lookup order: full namespace (e.g.
     * {@code customerengagement.Service}), then fallback without trailing {@value #ENTITY_CONTAINER_SUFFIX} (e.g.
     * {@code customerengagement}).
     *
     * @return The DataVerticle qualified name, or null if not configured for raw batch
     */
    private String resolveRawBatchVerticle() {
        if (rawBatchProcessing.isEmpty()) {
            return null;
        }

        String schemaNamespace = serviceMetadata.getEdm().getEntityContainer().getNamespace();
        String verticleName = rawBatchProcessing.get(schemaNamespace);
        if (verticleName != null) {
            return verticleName;
        }
        if (schemaNamespace.endsWith(ENTITY_CONTAINER_SUFFIX)) {
            String fallbackKey =
                    schemaNamespace.substring(0, schemaNamespace.length() - ENTITY_CONTAINER_SUFFIX.length());
            return rawBatchProcessing.get(fallbackKey);
        }
        return null;
    }

    private Buffer getBody(RoutingContext ctx) {
        return Optional.ofNullable(ctx.body())
                .map(RequestBody::buffer)
                .orElse(Buffer.buffer());
    }

    private void completeResponse(RoutingContext ctx, DataContext context, Future<Buffer> future) {
        future.onSuccess(buffer -> {
            HttpServerResponse response = ctx.response();
            if (response.ended()) {
                return; // e.g. verticle called handleDefaultODataProxyProcessing and already sent the response
            }
            setHeaderValues(context.responseData(), response::putHeader);
            response.putHeader("OData-Version", "4.0");
            response.setStatusCode(getStatusCode(context.responseData(), HttpStatusCode.OK.getStatusCode()));
            response.end(buffer);
        }).onFailure(cause -> {
            LOGGER.correlateWith(ctx).error("Error processing OData request", cause);
            ctx.fail(getStatusCode(cause), cause);
        });
    }

    /**
     * Maps the routing context to an OData request and data query for entity handling.
     *
     * @param routingContext The routing context
     * @return The OData request and data query
     * @throws ODataLibraryException if the request cannot be mapped
     */
    private EntityRequestParams mapEntityRequest(RoutingContext routingContext) throws ODataLibraryException {
        HttpServerRequest request = routingContext.request();
        DataAction action = mapMethodToAction(request.method());
        Buffer body = getBody(routingContext);
        String namespace = serviceMetadata.getEdm().getEntityContainer().getNamespace();
        ODataRequest odataRequest = mapToODataRequest(routingContext, namespace);
        DataQuery dataQuery = odataRequestToQuery(odataRequest, action, body);
        return new EntityRequestParams(odataRequest, dataQuery);
    }

    /**
     * Handles an entity request by mapping the context and delegating to the static handler.
     */
    private Future<Buffer> handleEntityRequest(RoutingContext routingContext, DataContext context) {
        try {
            EntityRequestParams params = mapEntityRequest(routingContext);
            return handleEntityRequest(routingContext, context, params.odataRequest(), params.dataQuery());
        } catch (Exception e) {
            return Future.failedFuture(e);
        }
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
     * Handles entity requests. Change sets are processed in order but not atomically; if one request fails, prior
     * requests are not rolled back as required by the OData specification.
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

            responseParts = requestParts.stream()
                    .map(part -> createBatchResponsePart(routingContext, context, part))
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

    static Future<ODataResponsePart> createBatchResponsePart(
            RoutingContext routingContext,
            DataContext context,
            BatchRequestPart requestPart) {
        if (requestPart.isChangeSet()) {
            List<Future<ODataResponse>> responseFutures = requestPart.getRequests().stream()
                    .map(odataRequest -> getODataResponse(routingContext, context, odataRequest))
                    .toList();
            return Future.all(responseFutures).map(ignored -> {
                List<ODataResponse> responses = responseFutures.stream()
                        .map(Future::result)
                        .toList();
                Optional<ODataResponse> errorResponse = responses.stream()
                        .filter(response -> response.getStatusCode() >= HttpStatusCode.BAD_REQUEST.getStatusCode())
                        .findFirst();
                if (errorResponse.isPresent()) {
                    return new ODataResponsePart(errorResponse.get(), false);
                }
                return new ODataResponsePart(responses, true);
            });
        }

        List<ODataRequest> requests = requestPart.getRequests();
        if (requests.isEmpty()) {
            return Future.failedFuture(new IllegalArgumentException("Batch request part contains no requests"));
        }
        return getODataResponsePart(routingContext, context, requests.get(0));
    }

    static Future<ODataResponsePart> getODataResponsePart(
            RoutingContext routingContext,
            DataContext context,
            ODataRequest odataRequest) {
        return getODataResponse(routingContext, context, odataRequest)
                .map(odataResponse -> new ODataResponsePart(odataResponse, false));
    }

    static Future<ODataResponse> getODataResponse(
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
        String contentId = odataRequest.getHeader(HttpHeader.CONTENT_ID);
        return handleEntityRequest(routingContext, contextCopy, odataRequest, partDataQuery)
                .map(buffer -> createODataResponse(contextCopy, buffer))
                .map(response -> applyContentId(response, contentId));
    }

    static ODataResponse applyContentId(ODataResponse response, String contentId) {
        if (contentId == null || response.getHeader(HttpHeader.CONTENT_ID) != null) {
            return response;
        }
        response.addHeader(HttpHeader.CONTENT_ID, contentId);
        return response;
    }

    /**
     * Handles the $metadata request.
     *
     * @param routingContext The routing context
     * @param context        The data context to receive status and headers
     * @return A future with the response buffer for completeResponse to write
     */
    Future<Buffer> handleMetadataRequest(RoutingContext routingContext, DataContext context) {
        Vertx vertx = routingContext.vertx();
        String namespace = serviceMetadata.getEdm().getEntityContainer().getNamespace();
        return vertx.executeBlocking(() -> {
            OData odata = OData.newInstance();
            return odata.createRawHandler(serviceMetadata)
                    .process(mapToODataRequest(routingContext, namespace));
        }).compose(odataResponse -> {
            context.responseData().put(DataContext.STATUS_CODE_HINT, odataResponse.getStatusCode());
            for (Map.Entry<String, List<String>> entry : odataResponse.getAllHeaders().entrySet()) {
                context.responseData().put(entry.getKey(), entry.getValue());
            }
            Buffer buffer;
            try {
                if (odataResponse.getContent() != null) {
                    buffer = inputStreamToBuffer(odataResponse.getContent());
                } else if (odataResponse.getODataContent() != null) {
                    java.io.ByteArrayOutputStream byteArrayOutput = new java.io.ByteArrayOutputStream();
                    odataResponse.getODataContent().write(byteArrayOutput);
                    buffer = Buffer.buffer(byteArrayOutput.toByteArray());
                } else {
                    buffer = Buffer.buffer();
                }
            } catch (IOException | ODataRuntimeException e) {
                return Future.failedFuture(e);
            }
            return Future.succeededFuture(buffer);
        });
    }

    private static String requestPath(String requestNamespace, String function) {
        return String.format("/%s/%s/%s", BASE_PATH_SEGMENT, requestNamespace, function);
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
    static Integer getStatusCode(Map<String, Object> respData, Integer defaultCode) {
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

    private record EntityRequestParams(ODataRequest odataRequest, DataQuery dataQuery) {
    }
}
