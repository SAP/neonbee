package io.neonbee.entity;

import static io.neonbee.internal.verticle.ConsolidationVerticle.ENTITY_TYPE_NAME_HEADER;
import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;

import org.apache.olingo.commons.api.edm.FullQualifiedName;

import io.neonbee.data.DataContext;
import io.neonbee.data.DataQuery;
import io.neonbee.data.DataRequest;
import io.neonbee.internal.verticle.ConsolidationVerticle;
import io.vertx.core.Future;
import io.vertx.core.Vertx;

public abstract class EntityVerticle extends AbstractEntityVerticle<EntityWrapper> {

    /**
     * Requesting data from other EntityVerticles.
     *
     * @param vertx   The Vert.x instance
     * @param request The DataRequest specifying the data to request from another EntityVerticle
     * @param context The {@link DataContext data context} which keeps track of the request-level data during a request
     * @return a future to the data requested
     */
    public static Future<EntityWrapper> requestEntity(Vertx vertx, DataRequest request, DataContext context) {
        FullQualifiedName entityTypeName = request.getEntityTypeName();
        if (entityTypeName == null) {
            throw new IllegalArgumentException(
                    "A entity request must specify an entity type name to request data from");
        }

        /*
         * TODO, as soon as having multiple verticle for an entity is not longer a corner case, I would recommend that
         * we send the "qualifiedNames" in a header to the ConsolidationVerticle. Then it wouldn't be necessary to do
         * the getVerticlesForEntityType call twice.
         */
        return getVerticlesForEntityType(vertx, entityTypeName).compose(qualifiedNames -> {
            if (qualifiedNames.isEmpty()) {
                return failedFuture("No verticle registered listening to entity type name "
                        + entityTypeName.getFullQualifiedNameAsString());
            } else if (qualifiedNames.size() == 1) {
                return requestData(vertx, new DataRequest(qualifiedNames.get(0), request.getQuery()), context);
            } else {
                DataQuery query = request.getQuery().copy().setHeader(ENTITY_TYPE_NAME_HEADER,
                        entityTypeName.getFullQualifiedNameAsString());
                return requestData(vertx,
                        new DataRequest(ConsolidationVerticle.QUALIFIED_NAME, query).setLocalOnly(true), context);
            }
        }).compose(entity -> entity instanceof EntityWrapper ? succeededFuture((EntityWrapper) entity)
                : failedFuture("The result of entity verticle must be an EntityWrapper"));
    }

    /**
     * Convenience method for calling the {@link #requestEntity(DataRequest, DataContext)} method.
     *
     * @param request The DataRequest specifying the data to request from another EntityVerticle
     * @param context The {@link DataContext data context} which keeps track of all the request-level information during
     *                the lifecycle of requesting data
     * @return a future to the data requested
     * @see #requestEntity(DataRequest, DataContext)
     */
    public Future<EntityWrapper> requestEntity(DataRequest request, DataContext context) {
        return requestEntity(getVertx(), request, context);
    }

}
