package io.neonbee.entity;

import io.neonbee.data.DataContext;
import io.neonbee.data.DataRequest;
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
        return AbstractEntityVerticle.requestEntity(EntityWrapper.class, vertx, request, context);
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
