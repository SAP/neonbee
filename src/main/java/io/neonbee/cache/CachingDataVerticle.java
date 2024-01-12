package io.neonbee.cache;

import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.LOWER_UNDERSCORE;
import static io.vertx.core.Future.succeededFuture;
import static java.util.Collections.emptyList;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import io.neonbee.data.DataContext;
import io.neonbee.data.DataMap;
import io.neonbee.data.DataQuery;
import io.neonbee.data.DataRequest;
import io.neonbee.data.DataVerticle;
import io.neonbee.internal.SharedDataAccessor;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.Lock;

/**
 * An abstract class that you can extend to write a {@link DataVerticle} with a in-memory caching functionality.
 *
 * The usage and method signatures are the same as in a {@link DataVerticle} but instead of overriding
 * {@link DataVerticle#requireData(DataQuery, DataContext)} and
 * {@link DataVerticle#retrieveData(DataQuery, DataMap, DataContext)} you should implement
 * {@link #requireDataForCaching(DataQuery, DataContext)} and
 * {@link #retrieveDataToCache(DataQuery, DataMap, DataContext)}.
 *
 * @param <T> the type of data this {@link CachingDataVerticle} caches &amp; handles
 */
public abstract class CachingDataVerticle<T> extends DataVerticle<T> {
    /**
     * If we have multiple instances of a single verticle, they should all share the same cache / coalesce on the same
     * requests / queries. Thus use a cache map, correlating all caches by the implementation class of the verticle.
     */
    @VisibleForTesting
    static final Map<Class<?>, Cache<Object, ?>> CACHES = new ConcurrentHashMap<>();

    private static final long DEFAULT_COALESCING_TIMEOUT = 10L * 1000;

    @SuppressWarnings("InlineFormatString")
    private static final String COALESCING_LOCK_NAME = "#coalescing_%h"; // %h hashCode of the cacheKey

    /**
     * We create a list of user identifying attributes (like userId, user_id, username, USERNAME, etc.) upfront, on the
     * one hand side for security reason, so we exactly know the attributes identifying a user (so this is not relying
     * on some kind of algorithm at runtime), but also to simply safe the processing time during runtime as the
     * {@link #getUserIdentifier(JsonObject)} method will be called quite often so creating the different variations of
     * the attribute name always at runtime, would eat up much very much processing time. So better to calculate the
     * list once and then use the same list when {@link #getUserIdentifier(JsonObject)} is called.
     */
    private static final String[] USER_IDENTIFYING_ATTRIBUTES;

    static {
        @SuppressWarnings("JdkObsolete")
        List<String> attributes = new LinkedList<>();

        for (String suffix : new String[] { "name", "id", "identifier" }) {
            for (String prefix : new String[] { "user", "user_", "" }) {
                attributes.add(prefix + suffix);
            }
        }

        // add at last, so all other identifying attributes take precedence
        attributes.add("user");

        ListIterator<String> iterator = attributes.listIterator();
        while (iterator.hasNext()) {
            String attribute = iterator.next();
            if (attribute.contains("_")) {
                iterator.add(LOWER_UNDERSCORE.to(LOWER_CAMEL, attribute));
            }
            iterator.add(attribute.toUpperCase(Locale.ROOT));
        }

        USER_IDENTIFYING_ATTRIBUTES = attributes.toArray(new String[0]);
    }

    private final Cache<Object, T> cache;

    private final Map<Object, T> cacheRegister = new HashMap<>();

    private final long coalescingTimeout;

    private SharedDataAccessor sharedDataAccessor;

    /**
     * Initializes a {@link CachingDataVerticle} with a cache lifetime of fife minutes and request coalescing.
     */
    @SuppressWarnings("checkstyle:MagicNumber")
    protected CachingDataVerticle() {
        this(5, TimeUnit.MINUTES);
    }

    /**
     * Initializes a {@link CachingDataVerticle} with a custom cache lifetime and request coalescing.
     *
     * Note, in order to only use request coalescing without caching the data in the verticle, you can set the cache
     * lifetime to a very small value, e.g. 1 second. This will result in the verticle coalescing requests to the same
     * verticle only if they are made in parallel, however each subsequent request, will again trigger a new request.
     *
     * @param cacheLifetime cache lifetime
     * @param timeUnit      defines the time unit like seconds, minutes or hours
     */
    protected CachingDataVerticle(long cacheLifetime, TimeUnit timeUnit) {
        this(cacheLifetime, timeUnit, DEFAULT_COALESCING_TIMEOUT);
    }

    /**
     * Initializes a {@link CachingDataVerticle} with a custom cache lifetime and optional request coalescing.
     *
     * @param cacheLifetime     cache lifetime
     * @param timeUnit          defines the time unit like seconds, minutes or hours
     * @param coalescingTimeout the timeout in milliseconds to wait for parallel requests, before attempting to receive
     *                          data on our own. if set to 0 or lower, requests will not be coalesced
     */
    @SuppressWarnings("unchecked")
    protected CachingDataVerticle(long cacheLifetime, TimeUnit timeUnit, long coalescingTimeout) {
        super();
        // we want to have every instance of the same verticle share the same cache / coalesce the same requests
        cache = (Cache<Object, T>) CACHES.computeIfAbsent(getClass(), verticleClass -> {
            return CacheBuilder.newBuilder().expireAfterWrite(cacheLifetime, timeUnit).build();
        });
        this.coalescingTimeout = coalescingTimeout;
    }

    @Override
    public void init(Vertx vertx, Context context) {
        super.init(vertx, context);

        // we will only need to retrieve locks if we coalesce requests
        if (coalescingTimeout > 0) {
            sharedDataAccessor = new SharedDataAccessor(vertx, getClass());
        }
    }

    /**
     * Return a unique cache key. The cache key could be any object.
     *
     * The default implementation returns a {@link CacheTuple} identifying the user and the query, meaning every query
     * of the same user will be cached. In order to determine the logged-in user the {@link DataContext#userPrincipal()}
     * will be used. As the structure of the user principal is determined by the authentication stack the
     * {@link CachingDataVerticle} will use the following user value for the tuple:
     * <ul>
     * <li>{@code null} in case no user is signed in / {@link DataContext#userPrincipal()} returned {@code null}</li>
     * <li>in case the {@link DataContext#userPrincipal()} contains a "name", "id", "identifier", or "user" attribute,
     * this attribute will be used in the cache tuple case-sensitive (so username kristian does not equal Kristian). The
     * attribute name allows for the following variations: prefixed "user" (e.g. userid or username), camel-case "userX"
     * (e.g. userId, userName), prefixed "user_" (e.g. user_id or user_name), no prefix (e.g. id, or name), or any of
     * the previous in all upper case (e.g. USERID, USER_NAME, or ID)</li>
     * <li>the whole {@link DataContext#userPrincipal()} as a {@link JsonObject} in any other case</li>
     * </ul>
     *
     * @param query   the query of the request
     * @param context the context
     * @return any object identifying the cached object
     */
    @VisibleForTesting
    protected Future<Object> getCacheKey(DataQuery query, DataContext context) {
        return succeededFuture(new CacheTuple(getUserIdentifier(context.userPrincipal()), query));
    }

    @VisibleForTesting
    static Object getUserIdentifier(JsonObject userPrincipal) {
        if (userPrincipal == null) {
            return null;
        }

        for (String attribute : USER_IDENTIFYING_ATTRIBUTES) {
            if (userPrincipal.containsKey(attribute)) {
                return userPrincipal.getValue(attribute);
            }
        }

        return userPrincipal; // use the full user principal as a fallback
    }

    /**
     * Same as {@link DataVerticle#requireData(DataQuery query, DataContext context)} but if there is a cache entry for
     * the request no data will actually be required and the cache result is returned from {@link #requireData}.
     *
     * @param query   The query describing the data requested
     * @param context A context object passed through the whole data retrieving life cycle
     * @return Future
     */
    @SuppressWarnings("PMD.UnusedFormalParameter")
    public Future<Collection<DataRequest>> requireDataForCaching(DataQuery query, DataContext context) {
        return succeededFuture(emptyList());
    }

    @Override
    public final Future<Collection<DataRequest>> requireData(DataQuery query, DataContext context) {
        return getCacheKey(query, context).compose(cacheKey -> {
            // no cache key signals us, that this request should not get cached
            if (cacheKey == null) {
                return requireDataForCaching(query, context);
            }

            return retrieveDataFromCache(cacheKey, context).compose(cachedData -> {
                // avoid unnecessary query processing in case of an available entry in the cache
                if (cachedData != null) {
                    // temporarily put the cached object into the volatile cacheRegister, so we can be sure it'll be
                    // available in the retrieveData method, the object might be purged from the cache otherwise
                    cacheRegister.put(cacheKey, cachedData);
                    return succeededFuture(emptyList());
                }

                // otherwise require the data that the verticle needs for caching
                return requireDataForCaching(query, context);
            });
        });
    }

    /**
     * Returns the cached data for a given cache key.
     *
     * Note that this method might get overridden, in order to implement a multi-stage caching / buffering approach. Use
     * {@link #getDataFromCache(Object)}, if you want to be sure to retrieve data from the in-memory cache instead.
     *
     * @param cacheKey the cache key to retrieve the cached data for
     * @param context  the context the cacheKey was used in
     * @return a future to the data, or an empty future, in case no data is cached
     */
    protected Future<T> retrieveDataFromCache(Object cacheKey, DataContext context) {
        return succeededFuture(getDataFromCache(cacheKey));
    }

    /**
     * Get data from the in-memory cache.
     *
     * @param cacheKey the cache key to get the data for if present
     * @return the cached data or {@code null}, in case no data was cached
     */
    protected final T getDataFromCache(Object cacheKey) {
        return cache.getIfPresent(cacheKey);
    }

    /**
     * Put data into the in-memory cache.
     *
     * @param cacheKey the cache key to put into the cache
     * @param data     the data to put into the cache
     */
    protected final void putDataToCache(Object cacheKey, T data) {
        cache.put(cacheKey, data);
    }

    /**
     * Purges the whole in-memory cache.
     */
    protected final void purgeDataFromCache() {
        cache.invalidateAll();
    }

    /**
     * Same as {@link DataVerticle#retrieveData(DataQuery query, DataContext context)} but if there is a cache entry for
     * the request no data will actually be required/calculated and instead the cache result is returned. When there is
     * no cache entry, a new one will be created.
     *
     * @see #retrieveData(DataQuery, DataMap, DataContext)
     * @param query   The query describing the data requested
     * @param require A map of the results required via {@link #requireData(DataQuery, DataContext)}
     * @param context A context object passed through the whole data retrieving life cycle
     * @return A future to the data requested
     */
    public abstract Future<T> retrieveDataToCache(DataQuery query, DataMap require, DataContext context);

    @Override
    public final Future<T> retrieveData(DataQuery query, DataMap require, DataContext context) {
        return getCacheKey(query, context).map(Optional::ofNullable).compose(optionalCacheKey -> {
            // when we do not get a cache key, skip the cache and do not coalesce any requests
            Future<Lock> lockFuture = succeededFuture();
            if (optionalCacheKey.isPresent()) {
                Object cacheKey = optionalCacheKey.get();

                // remove the cache data from the volatile cache register, if there is any we not need to retrieve it
                T cachedData = cacheRegister.remove(cacheKey);
                if (cachedData != null) {
                    return succeededFuture(cachedData);
                }

                // in order to coalesce requests, wait until we receive a lock and check the cache again
                if (coalescingTimeout > 0) {
                    // note that the hash that is part of the lock name is unsafe, meaning that if there is a chance for
                    // a hash collision. in this case two requests are coalesced / may wait for each other, even if they
                    // do not share the same cache key. however the likelihood is small and the cache is still safe
                    // because the data read from the cache will not be affected by the collision, so this is neglected
                    lockFuture = sharedDataAccessor.getLocalLockWithTimeout(
                            String.format(COALESCING_LOCK_NAME, cacheKey), coalescingTimeout);
                }
            }

            // if we got the lock and the cache contains data, some other request already retrieved it already and we
            // can coalesce the two requests by returning the data immediately. if the cache does not contain data yet,
            // we are "responsible" for receiving it. if we did not get the lock (so lock == null), it either means we
            // should not coalesce parallel requests, or we don't know who got the lock because the other process is
            // taking too long to get the data, so in any case we will fall back requesting it on our own. to avoid a
            // conflict with the original owner of the lock, we not call the retrievedDataToCache method afterwards
            return lockFuture.otherwiseEmpty().compose(lock -> optionalCacheKey.map(cache::getIfPresent)
                    .map(Future::succeededFuture).orElseGet(() -> {
                        return retrieveDataToCache(query, require, context).compose(data -> {
                            if (data == null) {
                                return succeededFuture();
                            }

                            // if data was returned, cache it right now (always only into the in-memory cache, for a
                            // multi-stage caching / buffering, the retrievedDataToCache can be utilized)
                            optionalCacheKey.ifPresent(cacheKey -> cache.put(cacheKey, data));

                            // if we have a lock, we can already release it right here, as other waiting verticles check
                            // the cache right after. this is a small potential time safe, as the other requests will
                            // not have to wait for the retrievedDataToCache future to complete
                            if (lock != null) {
                                lock.release();
                            }

                            // in case we do not coalesce requests, or we are the one who got the lock and thus
                            // "rightfully" retrieved the data, notify the retrievedDataToCache that we got new data,
                            // however, neglect the outcome and always return the data
                            if (optionalCacheKey.isPresent() && (coalescingTimeout <= 0 || lock != null)) {
                                return retrievedDataToCache(optionalCacheKey.get(), data, context)
                                        .map(data).otherwise(data);
                            }

                            // if we did not retrieve the lock in the first place, we cannot know when the future that
                            // got the lock completes, so better *not* execute the retrievedDataToCache method, in order
                            // to prevent any conflict / race condition with the owner of the lock
                            return succeededFuture(data);
                        });
                    }).onComplete(result -> {
                        if (lock != null) {
                            lock.release(); // safety-safe, or if data was cached in the meantime!
                        }
                    })).compose(data -> filterDataFromCache(query, data, context));
        });
    }

    /**
     * This method is called whenever new data was stored in the cache. Note that the result of the returned future will
     * have no impact, on the result of the call that cached the data, other than that the call that made the request
     * will wait for the future to complete. In order to optimize performance, this call should return immediately with
     * a succeeded future, in order to implement a "fire &amp; forget" strategy.
     *
     * By default this method does nothing, it may be used in order to provide multi-level caching, e.g. by storing it
     * on the database, in addition to storing it in the in-memory cache of the {@link CachingDataVerticle}.
     *
     * This method will never be called for queries that {@link #getCacheKey(DataQuery, DataContext)} returned a
     * {@code null} value.
     *
     * @param <U>      any type
     * @param cacheKey the key the data should be cached for
     * @param data     the data that should be cached
     * @param context  the context the data was retrieved in
     * @return any future, signaling completion of the operation
     */
    protected <U> Future<U> retrievedDataToCache(Object cacheKey, T data, DataContext context) {
        return succeededFuture();
    }

    /**
     * A method that can be used to based on the query / context filter the data before it is being returned from cache
     * or from the {@link #retrieveDataToCache(DataQuery, DataMap, DataContext)} method. The filter does not influence
     * what data is being cached, so when the data is read from cache based on the {@code cacheKey} it might still
     * contain data that is not returned by the verticle, after being processed by this method.
     *
     * @param query   the query to filter the data by
     * @param data    the data to filter
     * @param context the context in which to filter the data
     * @return the data to return from the verticle
     */
    protected Future<T> filterDataFromCache(DataQuery query, T data, DataContext context) {
        return succeededFuture(data);
    }

    /**
     * The {@link CacheTuple} is the default {@code cacheKey} used by the {@link CachingDataVerticle} and can be used to
     * combine multiple objects of any type.
     */
    public static class CacheTuple {
        private final List<Object> values;

        /**
         * Creates a {@link CacheTuple} with any number of values.
         *
         * @param values the values of the tuple
         */
        public CacheTuple(Object... values) {
            this.values = Collections.unmodifiableList(Arrays.asList(values));
        }

        /**
         * Return a single tuple value.
         *
         * @param <T>   the type of the tuple value
         * @param index the index of the tuple value to return
         * @return the value of the tuple at that index
         */
        @SuppressWarnings("unchecked")
        public <T> T get(int index) {
            return (T) values.get(index);
        }

        /**
         * Return the size of the tuple.
         *
         * @return the size of the tuple
         */
        public int size() {
            return values.size();
        }

        @Override
        public boolean equals(Object object) {
            if (object == this) {
                return true;
            } else if (!(object instanceof CacheTuple)) {
                return false;
            }

            return values.equals(((CacheTuple) object).values);
        }

        @Override
        public int hashCode() {
            return values.hashCode();
        }
    }
}
