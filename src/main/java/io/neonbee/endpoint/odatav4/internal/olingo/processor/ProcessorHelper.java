package io.neonbee.endpoint.odatav4.internal.olingo.processor;

import static io.neonbee.entity.EntityVerticle.requestEntity;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.vertx.core.http.HttpMethod;
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
import io.neonbee.internal.helper.BufferHelper;
import io.neonbee.logging.LoggingFacade;
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

    /** OData count size key. */
    public static final String ODATA_COUNT_SIZE_KEY = "OData.count.size";

    private static final LoggingFacade LOGGER = LoggingFacade.create();

    private static final Set<HttpMethod> METHODS_WITH_BODY = Set.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH);

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
        enhanceDataContextWithRawBody(routingContext, dataContext);
        return requestEntity(vertx, new DataRequest(entityType.getFullQualifiedName(), query), dataContext)
                .map(result -> transferResponseHint(dataContext, routingContext, result))
                .onFailure(processPromise::fail);
    }

    /**
     * Add a new entry into data context with the raw body of the OData request under the key rawBody.
     *
     * @param routingContext     routing context request
     * @param dataContext data context
     */
    static void enhanceDataContextWithRawBody(RoutingContext routingContext, DataContext dataContext) {
        if (METHODS_WITH_BODY.contains(routingContext.request().method()) && routingContext.body().length() > 0) {
            dataContext.put(DataContext.RAW_BODY_KEY, routingContext.body().buffer());
        }
    }

    /**
     * Transfer response hints from data context into routing context.
     *
     * @param dataContext    data context
     * @param routingContext routing context
     * @param result         entity wrapper result
     * @return the entity wrapper result
     */
    @VisibleForTesting
    static EntityWrapper transferResponseHint(DataContext dataContext, RoutingContext routingContext,
            EntityWrapper result) {
        dataContext.responseData().forEach((key, value) -> routingContext.put(RESPONSE_HEADER_PREFIX + key, value));
        return result;
    }
}
