package io.neonbee.data;

import io.vertx.core.Future;

public interface DataSource<T> {
    /**
     * Retrieve the requested data in an asynchronous manner and returns a future to the data expected.
     *
     * @param query   The query describing the data requested
     * @param context A context object passed through the whole data retrieving life cycle
     * @return A future to the data requested
     */
    Future<T> retrieveData(DataQuery query, DataContext context);
}
