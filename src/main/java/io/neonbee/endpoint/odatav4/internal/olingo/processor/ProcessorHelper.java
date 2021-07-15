package io.neonbee.endpoint.odatav4.internal.olingo.processor;

import static io.neonbee.entity.EntityVerticle.requestEntity;

import java.util.Optional;

import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.edm.EdmEntityType;
import org.apache.olingo.server.api.ODataRequest;
import org.apache.olingo.server.api.uri.UriInfo;
import org.apache.olingo.server.api.uri.UriResourceEntitySet;

import io.neonbee.data.DataAction;
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
    private ProcessorHelper() {}

    private static DataQuery odataRequestToQuery(ODataRequest request, DataAction action, Buffer body) {
        // the uriPath without /odata root path and without query path
        String uriPath = "/" + request.getRawServiceResolutionUri() + request.getRawODataPath();
        // the raw query path
        String rawQueryPath = request.getRawQueryPath();
        return new DataQuery(action, uriPath, rawQueryPath, request.getAllHeaders(), body).addHeader("X-HTTP-Method",
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

        return requestEntity(vertx, new DataRequest(entityType.getFullQualifiedName(), query),
                new DataContextImpl(routingContext)).onFailure(processPromise::fail);
    }
}
