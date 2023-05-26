package io.neonbee.cache;

import static io.vertx.core.Future.succeededFuture;

import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;

import io.neonbee.data.DataContext;
import io.neonbee.logging.LoggingFacade;
import io.vertx.core.Future;

/**
 * This class extends the in-memory caching possibilities of the {@link CachingDataVerticle} by an arbitrary second
 * stage buffering concept. E.g. storing data in a database or likewise. Caching being the most volatile form of storage
 * it is first checked if the data is cached already, before attempting to read it from the buffer.
 */
public abstract class BufferingDataVerticle<T> extends CachingDataVerticle<T> {
    private static final LoggingFacade LOGGER = LoggingFacade.create();

    /**
     * Initializes a {@link BufferingDataVerticle} with a refresh cache interval of 5 minutes by default and request
     * coalescing turned on.
     */
    protected BufferingDataVerticle() {
        super();
    }

    /**
     * Initializes a {@link BufferingDataVerticle} with a custom refresh cache interval and request coalescing turned
     * on.
     *
     * @param cacheLifetime refresh interval
     * @param timeUnit      defines the time unit like seconds, minutes or hours
     */
    protected BufferingDataVerticle(long cacheLifetime, TimeUnit timeUnit) {
        super(cacheLifetime, timeUnit);
    }

    /**
     * Initializes a {@link BufferingDataVerticle} with a custom refresh cache interval and turning request coalescing
     * on or off.
     *
     * @param cacheLifetime   cache lifetime
     * @param timeUnit        defines the time unit like seconds, minutes or hours
     * @param coalesceTimeout the timeout in milliseconds to wait for parallel requests, before attempting to receive
     *                        data on our own. if set to 0 or lower, requests will not be coalesced
     */
    protected BufferingDataVerticle(long cacheLifetime, TimeUnit timeUnit, long coalesceTimeout) {
        super(cacheLifetime, timeUnit, coalesceTimeout);
    }

    /**
     * Read data from the buffer (after checking the in-memory cache was empty).
     *
     * @param cacheKey the cached key to read the data for
     * @param context  the context the data should be read for
     * @return a future to the buffered data or an empty future in case there is no data in the buffer
     */
    public abstract Future<T> readDataFromBuffer(Object cacheKey, DataContext context);

    /**
     * Write data to the buffer.
     *
     * @param <U>      any type
     * @param cacheKey the cache key to write the data for
     * @param data     the data to write
     * @param context  the context in which the data was retrieved in
     * @return any kind of future, note that the callee will only wait for this future to complete, but the result of
     *         the future is neglected. this also allows for a "fire &amp; forget" strategy, if this method is returned
     *         immediately with a succeeded future
     */
    public abstract <U> Future<U> writeDataToBuffer(Object cacheKey, T data, DataContext context);

    @Override
    protected Future<T> retrieveDataFromCache(Object cacheKey, DataContext context) {
        return super.retrieveDataFromCache(cacheKey, context).compose(cachedData -> {
            if (cachedData != null) {
                return succeededFuture(cachedData);
            }

            // if data could not be read, treat it as if no data is in the buffer / falling back to retrieving the data
            return readDataFromBuffer(cacheKey, context).onSuccess(data -> {
                // in case data was buffered, also repopulate the in-memory cache
                if (data != null) {
                    putDataToCache(cacheKey, data);
                }
            }).onFailure(throwable -> {
                // the only expected exception is a NoSuchElement exception, treat it as if no buffer entry exists
                if (!(throwable instanceof NoSuchElementException)) {
                    LOGGER.correlateWith(context).warn("Failed to read from buffer", throwable);
                }
            }).otherwiseEmpty();
        });
    }

    @Override
    protected <U> Future<U> retrievedDataToCache(Object cacheKey, T data, DataContext context) {
        return writeDataToBuffer(cacheKey, data, context).onFailure(throwable -> {
            LOGGER.correlateWith(context).warn("Failed to write to buffer", throwable);
        }).mapEmpty(); // the result of this method anyways never influences the result
    }
}
