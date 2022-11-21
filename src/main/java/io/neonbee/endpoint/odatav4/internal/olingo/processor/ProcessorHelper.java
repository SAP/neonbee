package io.neonbee.endpoint.odatav4.internal.olingo.processor;

import static io.neonbee.entity.EntityVerticle.requestEntity;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.edm.EdmEntityType;
import org.apache.olingo.server.api.ODataRequest;
import org.apache.olingo.server.api.uri.UriInfo;
import org.apache.olingo.server.api.uri.UriResourceEntitySet;

import com.google.common.annotations.VisibleForTesting;

import io.neonbee.data.DataAction;
import io.neonbee.data.DataContext;
import io.neonbee.data.DataQuery;
import io.neonbee.data.DataRequest;
import io.neonbee.data.internal.DataContextImpl;
import io.neonbee.entity.EntityWrapper;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.RoutingContext;

public final class ProcessorHelper {

    /** Response prefix in routing context. */
    public static final String RESPONSE_HEADER_PREFIX = "response.";

    /** OData filter key. */
    public static final String ODATA_FILTER_KEY = "OData.filter";

    /** OData orderBy key. */
    public static final String ODATA_ORDER_BY_KEY = "OData.orderby";

    /** OData skip key. */
    public static final String ODATA_SKIP_KEY = "OData.skip";

    /** OData top key. */
    public static final String ODATA_TOP_KEY = "OData.top";

    /** OData expand key. */
    public static final String ODATA_EXPAND_KEY = "OData.expand";

    /** OData key predicate key. */
    public static final String ODATA_KEY_PREDICATE_KEY = "OData.key";

    private ProcessorHelper() {}

    private static DataQuery odataRequestToQuery(ODataRequest request, DataAction action, Buffer body) {
        // the uriPath without /odata root path and without query path
        String uriPath = "/" + request.getRawServiceResolutionUri() + request.getRawODataPath();
        // the raw query path
        Map<String, List<String>> stringListMap = DataQuery.parseEncodedQueryString(request.getRawQueryPath());
        return new DataQuery(action, uriPath, stringListMap, request.getAllHeaders(), body).addHeader("X-HTTP-Method",
                request.getMethod().name());
    }

    /**
     * Maps an ODataRequest into an entity request and sends it to the related entity verticles.
     *
     * @param request        The ODataRequest
     * @param action         The DataAction of the request
     * @param uriInfo        The UriInfo of the ODataRequest
     * @param vertx          The Vert.x instance
     * @param routingContext The routingContext of the request
     * @param processPromise the processPromise of the current request
     * @return a Future of EntityWrapper holding the result of the entity request.
     */
    public static Future<EntityWrapper> forwardRequest(ODataRequest request, DataAction action, UriInfo uriInfo,
            Vertx vertx, RoutingContext routingContext, Promise<Void> processPromise) {
        return forwardRequest(request, action, null, uriInfo, vertx, routingContext, processPromise);
    }

    /**
     * Maps an ODataRequest into an entity request and sends it to the related entity verticles. If the ODataRequest
     * contains an Entity in the request body, this entity will also be forwarded.
     *
     * @param request        The ODataRequest
     * @param action         The DataAction of the request
     * @param entity         The Entity of the request
     * @param uriInfo        The UriInfo of the ODataRequest
     * @param vertx          The Vert.x instance
     * @param routingContext The routingContext of the request
     * @param processPromise the processPromise of the current request
     * @return a Future of EntityWrapper holding the result of the entity request.
     */
    public static Future<EntityWrapper> forwardRequest(ODataRequest request, DataAction action, Entity entity,
            UriInfo uriInfo, Vertx vertx, RoutingContext routingContext, Promise<Void> processPromise) {
        UriResourceEntitySet uriResourceEntitySet = (UriResourceEntitySet) uriInfo.getUriResourceParts().get(0);
        EdmEntityType entityType = uriResourceEntitySet.getEntitySet().getEntityType();
        Buffer body = Optional.ofNullable(entity)
                .map(e -> new EntityWrapper(entityType.getFullQualifiedName(), e).toBuffer(vertx)).orElse(null);
        DataQuery query = odataRequestToQuery(request, action, body);
        DataContext dataContext = new DataContextImpl(routingContext);
        return requestEntity(vertx, new DataRequest(entityType.getFullQualifiedName(), query), dataContext)
                .map(result -> {
                    transferResponseHint(dataContext, routingContext);
                    return result;
                }).onFailure(processPromise::fail);
    }

    /**
     * Transfer response hints from data context into routing context.
     *
     * @param dataContext    data context
     * @param routingContext routing context
     */
    @VisibleForTesting
    static void transferResponseHint(DataContext dataContext, RoutingContext routingContext) {
        dataContext.responseData().entrySet()
                .forEach(entry -> routingContext.put(RESPONSE_HEADER_PREFIX + entry.getKey(), entry.getValue()));
    }
}
