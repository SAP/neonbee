package io.neonbee.data;

import static io.vertx.core.Future.failedFuture;

import io.neonbee.logging.LoggingFacade;
import io.vertx.core.Future;

public interface DataAdapter<T> extends DataSource<T>, DataSink<T> {
    /**
     * Adapter implementation for retrieve data. Should be overridden in implementations, which accept to retrieve data.
     */
    @Override
    default Future<T> retrieveData(DataQuery query, DataContext context) {
        LoggingFacade.create(getClass()).correlateWith(context).warn("{} does not implement retrieveData",
                getClass().getSimpleName());
        return failedFuture(new UnsupportedOperationException("retrieveData not implemented"));
    }

    /**
     * Adapter implementation for manipulate data. Should be overridden in implementations, which accept to create /
     * update / delete data.
     */
    @Override
    default Future<T> manipulateData(DataQuery query, DataContext context) {
        switch (query.getAction()) {
        case CREATE:
            return createData(query, context);
        case UPDATE:
            return updateData(query, context);
        case DELETE:
            return deleteData(query, context);
        default:
            return failedFuture(new IllegalArgumentException("manipulateData is unable to handle this action"));
        }
    }

    /**
     * Convenience method for creating data.
     *
     * @param query   The query describing the data which should be manipulated
     * @param context A context object passed through the whole data retrieving life cycle
     * @return A future to either the data created / updated or a null future in case the operation generally succeeded
     *         or failed.
     */
    default Future<T> createData(DataQuery query, DataContext context) {
        LoggingFacade.create(getClass()).correlateWith(context)
                .warn("{} does neither implement manipulateData, nor createData", getClass().getSimpleName());
        return failedFuture(new UnsupportedOperationException("manipulateData / createData not implemented"));
    }

    /**
     * Convenience method for updating data.
     *
     * @param query   The query describing the data which should be manipulated
     * @param context A context object passed through the whole data retrieving life cycle
     * @return A future to either the data created / updated or a null future in case the operation generally succeeded
     *         or failed.
     */
    default Future<T> updateData(DataQuery query, DataContext context) {
        LoggingFacade.create(getClass()).correlateWith(context)
                .warn("{} does neither implement manipulateData, nor alterData", getClass().getSimpleName());
        return failedFuture(new UnsupportedOperationException("manipulateData / alterData not implemented"));
    }

    /**
     * Convenience method for deleting data.
     *
     * @param query   The query describing the data which should be manipulated
     * @param context A context object passed through the whole data retrieving life cycle
     * @return A future to either the data created / updated or a null future in case the operation generally succeeded
     *         or failed.
     */
    default Future<T> deleteData(DataQuery query, DataContext context) {
        LoggingFacade.create(getClass()).correlateWith(context)
                .warn("{} does neither implement manipulateData, nor deleteData", getClass().getSimpleName());
        return failedFuture(new UnsupportedOperationException("manipulateData / deleteData not implemented"));
    }
}
