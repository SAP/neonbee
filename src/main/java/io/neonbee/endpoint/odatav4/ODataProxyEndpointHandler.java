package io.neonbee.endpoint.odatav4;

import static io.neonbee.endpoint.HttpMethodToDataActionMapper.mapMethodToAction;
import static io.neonbee.endpoint.odatav4.internal.olingo.OlingoEndpointHandler.getStatusCode;
import static io.neonbee.endpoint.odatav4.internal.olingo.OlingoEndpointHandler.mapToODataRequest;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.olingo.commons.api.edm.FullQualifiedName;
import org.apache.olingo.server.api.ODataRequest;
import org.apache.olingo.server.api.ServiceMetadata;

import io.neonbee.data.DataAction;
import io.neonbee.data.DataContext;
import io.neonbee.data.DataQuery;
import io.neonbee.data.DataRequest;
import io.neonbee.data.internal.DataContextImpl;
import io.neonbee.entity.AbstractEntityVerticle;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RequestBody;
import io.vertx.ext.web.RoutingContext;

public final class ODataProxyEndpointHandler implements Handler<RoutingContext> {

    // Regex to find last path segment
    private static final Pattern ENTITY_NAME_PATTERN =
            Pattern.compile("/(\\w+)(?:\\([^)]*\\))?(?:/(\\w+))?$");

    private final ServiceMetadata serviceMetadata;

    /**
     * Returns the ODataProxyEndpointHandler.
     *
     * @param serviceMetadata The metadata of the service
     */
    public ODataProxyEndpointHandler(ServiceMetadata serviceMetadata) {
        this.serviceMetadata = serviceMetadata;
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
