package io.neonbee.data;

import io.vertx.core.Future;

public interface DataSink<T> {
    /**
     * This method is used in order to create (add / insert), update (modify) or delete data in an asynchronous manner
     * and returns a future to the data created / updated, or a null future in case the operation succeeded.
     *
     * @param query   The query describing the data which should be manipulated. For update and delete the uriPath /
     *                query addresses data to be altered.
     * @param context A context object passed through the whole data retrieving life cycle
     * @return A future to either the data created / updated or a null future in case the operation generally succeeded
     *         or failed.
     */
    Future<T> manipulateData(DataQuery query, DataContext context);
}
