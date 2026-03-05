package io.neonbee.internal;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;

import java.util.Optional;

import io.neonbee.NeonBee;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.core.shareddata.Counter;
import io.vertx.core.shareddata.LocalMap;
import io.vertx.core.shareddata.Lock;
import io.vertx.core.shareddata.SharedData;

/**
 * this class prefixes all names to access shared data with "NeonBee" and with the class name of the class requesting
 * the shared data object. This helps to uniquely address objects in the shared data. This class also provides
 * convenience functions for accessing shared data without providing a name (this is e.g. helpful, in case one class
 * only needs one lock, imagine it to work similar to a synchronized method, or calling synchronized(this) { ... })
 */
@SuppressWarnings({ "PMD.LinguisticNaming", "checkstyle:MissingJavadocMethod", "checkstyle:SummaryJavadoc" })
public class SharedDataAccessor implements SharedData {
    private static final String DEFAULT_NAME = "default";

    private final SharedData sharedData;

    private final Class<?> accessClass;

    public SharedDataAccessor(Vertx vertx, Class<?> accessClass) {
        this(vertx.sharedData(), accessClass);
    }

    public SharedDataAccessor(SharedData sharedData, Class<?> accessClass) {
        this.sharedData = sharedData;
        this.accessClass = accessClass;
    }

    @Override
    public <K, V> Future<AsyncMap<K, V>> getAsyncMap(String name) {
        return sharedData.getAsyncMap(sharedName(name));
    }

    /**
     * Returns an async. map to be used storing shared data for the associated class.
     *
     * @see #getAsyncMap(String, Handler)
     * @param resultHandler The handler which returns the map asynchronously
     * @param <K>           The type of the key of the async map entries
     * @param <V>           The type of the value of the async map entries
     */
    public <K, V> void getAsyncMap(Handler<AsyncResult<AsyncMap<K, V>>> resultHandler) {
        getAsyncMap(null, resultHandler);
    }

    /**
     * @deprecated Deprecated since Vert.x 4.x (callback model), use {@link SharedDataAccessor#getAsyncMap(String)}
     *             (future-based) instead. Removed in Vert.x 5.
     * @param name          name of the lock
     * @param resultHandler the handler to return the lock asynchronously
     * @param <K>           The type of the key of the async map entries
     * @param <V>           The type of the value of the async map entries
     */
    @Deprecated(since = "4.x", forRemoval = true)
    public <K, V> void getAsyncMap(String name, Handler<AsyncResult<AsyncMap<K, V>>> resultHandler) {
        sharedData.getAsyncMap(sharedName(name)).onComplete(asyncResult -> {
            if (asyncResult.succeeded()) {
                AsyncMap<K, V> map = (AsyncMap<K, V>) asyncResult.result();
                handleSuccess(map, resultHandler);
            } else {
                handleFailure(asyncResult.cause(), resultHandler);
            }
        });
    }

    /**
     * Returns the future of an async. local map to be used storing shared data for the associated class.
     *
     * @see #getLocalAsyncMap(String, Handler)
     * @param <K> The type of the key of the async map entries
     * @param <V> The type of the value of the async map entries
     * @return a {@link Future} of {@link AsyncMap} which is only available locally
     */
    public <K, V> Future<AsyncMap<K, V>> getLocalAsyncMap() {
        return getLocalAsyncMap((String) null);
    }

    @Override
    public <K, V> Future<AsyncMap<K, V>> getLocalAsyncMap(String name) {
        return sharedData.getLocalAsyncMap(sharedName(name));
    }

    /**
     * Returns an async. local map to be used storing shared data for the associated class.
     *
     * @see #getLocalAsyncMap(String, Handler)
     * @param resultHandler The handler which returns the map asynchronously
     * @param <K>           The type of the key of the async map entries
     * @param <V>           The type of the value of the async map entries
     */
    public <K, V> void getLocalAsyncMap(Handler<AsyncResult<AsyncMap<K, V>>> resultHandler) {
        getLocalAsyncMap(null, resultHandler);
    }

    /**
     * @deprecated Deprecated since Vert.x 4.x (callback model), use {@link SharedDataAccessor#getLocalAsyncMap(String)}
     *             (future-based) instead. Removed in Vert.x 5.
     * @param name          name of the lock
     * @param resultHandler the handler to return the lock asynchronously
     * @param <K>           The type of the key of the async map entries
     * @param <V>           The type of the value of the async map entries
     */
    @Deprecated(since = "4.x", forRemoval = true)
    public <K, V> void getLocalAsyncMap(String name, Handler<AsyncResult<AsyncMap<K, V>>> resultHandler) {
        sharedData.getLocalAsyncMap(sharedName(name)).onComplete(asyncResult -> {
            if (asyncResult.succeeded()) {
                AsyncMap<K, V> map = (AsyncMap<K, V>) asyncResult.result();
                handleSuccess(map, resultHandler);
            } else {
                handleFailure(asyncResult.cause(), resultHandler);
            }
        });
    }

    /**
     * Returns the future of a cluster wide shared map to be used storing shared data for the associated class.
     *
     * @see #getClusterWideMap(String, Handler)
     * @param <K> The type of the key of the async map entries
     * @param <V> The type of the value of the async map entries
     * @return a {@link Future} of {@link AsyncMap} which is shared in the cluster
     */
    public <K, V> Future<AsyncMap<K, V>> getClusterWideMap() {
        return getClusterWideMap((String) null);
    }

    @Override
    public <K, V> Future<AsyncMap<K, V>> getClusterWideMap(String name) {
        return sharedData.getClusterWideMap(sharedName(name));
    }

    /**
     * Returns a cluster wide shared map to be used storing shared data for the associated class.
     *
     * @see #getClusterWideMap(String, Handler)
     * @param resultHandler The handler which returns the map asynchronously
     * @param <K>           The type of the key of the async map entries
     * @param <V>           The type of the value of the async map entries
     */
    public <K, V> void getClusterWideMap(Handler<AsyncResult<AsyncMap<K, V>>> resultHandler) {
        getClusterWideMap(null, resultHandler);
    }

    /**
     * @deprecated Deprecated since Vert.x 4.x (callback model), use
     *             {@link SharedDataAccessor#getClusterWideMap(String)} (future-based) instead. Removed in Vert.x 5.
     * @param name          name of the lock
     * @param resultHandler the handler to return the lock asynchronously
     * @param <K>           The type of the key of the async map entries
     * @param <V>           The type of the value of the async map entries
     */
    @Deprecated(since = "4.x", forRemoval = true)
    public <K, V> void getClusterWideMap(String name, Handler<AsyncResult<AsyncMap<K, V>>> resultHandler) {
        sharedData.getClusterWideMap(sharedName(name)).onComplete(asyncResult -> {
            if (asyncResult.succeeded()) {
                AsyncMap<K, V> map = (AsyncMap<K, V>) asyncResult.result();
                handleSuccess(map, resultHandler);
            } else {
                handleFailure(asyncResult.cause(), resultHandler);
            }
        });
    }

    /**
     * Returns the future to a counter associated to the given class.
     *
     * @see #getCounter(String, Handler)
     * @return a {@link Future} of {@link Counter} which is shared in the cluster
     */
    public Future<Counter> getCounter() {
        return getCounter((String) null);
    }

    @Override
    public Future<Counter> getCounter(String name) {
        return sharedData.getCounter(sharedName(name));
    }

    /**
     * Returns a counter associated to the given class.
     *
     * @see #getCounter(String, Handler)
     * @param resultHandler The handler
     */
    public void getCounter(Handler<AsyncResult<Counter>> resultHandler) {
        getCounter(null, resultHandler);
    }

    /**
     * @deprecated Deprecated since Vert.x 4.x (callback model), use {@link SharedDataAccessor#getCounter(String)}
     *             (future-based) instead. Removed in Vert.x 5.
     * @param name          name of the lock
     * @param resultHandler the handler to return the lock asynchronously
     */
    @Deprecated(since = "4.x", forRemoval = true)
    public void getCounter(String name, Handler<AsyncResult<Counter>> resultHandler) {
        sharedData.getCounter(sharedName(name)).onComplete(asyncResult -> resultHandler.handle(asyncResult));
    }

    /**
     * Returns the future to a local counter associated to the given class.
     *
     * @see #getLocalCounter(String, Handler)
     * @return a {@link Future} of {@link Counter} which is only available locally
     */
    public Future<Counter> getLocalCounter() {
        return getLocalCounter((String) null);
    }

    @Override
    public Future<Counter> getLocalCounter(String name) {
        return sharedData.getLocalCounter(sharedName(name));
    }

    /**
     * Returns a local counter associated to the given class.
     *
     * @see #getLocalCounter(String, Handler)
     * @param resultHandler The handler
     */
    public void getLocalCounter(Handler<AsyncResult<Counter>> resultHandler) {
        getLocalCounter(null, resultHandler);
    }

    /**
     * @deprecated Deprecated since Vert.x 4.x (callback model), use {@link SharedDataAccessor#getLocalCounter(String)}
     *             (future-based) instead. Removed in Vert.x 5.
     * @param name          name of the lock
     * @param resultHandler the handler to return the lock asynchronously
     */
    @Deprecated(since = "4.x", forRemoval = true)
    public void getLocalCounter(String name, Handler<AsyncResult<Counter>> resultHandler) {
        sharedData.getLocalCounter(sharedName(name)).onComplete(asyncResult -> resultHandler.handle(asyncResult));
    }

    /**
     * Returns the future to a counter associated to the given class.
     *
     * @see #getLock(String, Handler)
     * @return a {@link Future} of {@link Lock} which is shared in the cluster
     */
    public Future<Lock> getLock() {
        return getLock((String) null);
    }

    @Override
    public Future<Lock> getLock(String name) {
        return sharedData.getLock(sharedName(name));
    }

    /**
     * Returns a lock associated to the given class.
     *
     * @see #getLock(String, Handler)
     * @param resultHandler The handler
     */
    public void getLock(Handler<AsyncResult<Lock>> resultHandler) {
        getLock(null, resultHandler);
    }

    /**
     * @deprecated Deprecated since Vert.x 4.x (callback model), use {@link SharedDataAccessor#getLock(String)}
     *             (future-based) instead. Removed in Vert.x 5.
     * @param name          name of the lock
     * @param resultHandler the handler to return the lock asynchronously
     */
    @Deprecated(since = "4.x", forRemoval = true)
    public void getLock(String name, Handler<AsyncResult<Lock>> resultHandler) {
        sharedData.getLock(sharedName(name)).onComplete(asyncResult -> resultHandler.handle(asyncResult));
    }

    /**
     * Returns the future of a local lock associated to the given class.
     *
     * @see #getLocalLock(String, Handler)
     * @return a {@link Future} of {@link Lock} which is only available locally
     */
    public Future<Lock> getLocalLock() {
        return getLocalLock((String) null);
    }

    @Override
    public Future<Lock> getLocalLock(String name) {
        return sharedData.getLocalLock(sharedName(name));
    }

    /**
     * Returns a local lock associated to the given class.
     *
     * @see #getLocalLock(String, Handler)
     * @param resultHandler The handler
     */
    public void getLocalLock(Handler<AsyncResult<Lock>> resultHandler) {
        getLocalLock(null, resultHandler);
    }

    /**
     * @deprecated Deprecated since Vert.x 4.x (callback model), use {@link SharedDataAccessor#getLocalLock(String)}
     *             (future-based) instead. Removed in Vert.x 5.
     * @param name          name of the lock
     * @param resultHandler the handler to return the lock asynchronously
     */
    @Deprecated(since = "4.x", forRemoval = true)
    public void getLocalLock(String name, Handler<AsyncResult<Lock>> resultHandler) {
        sharedData.getLocalLock(sharedName(name)).onComplete(asyncLock -> resultHandler.handle(asyncLock));
    }

    /**
     * Returns the future of a lock with a timeout associated to the given class.
     *
     * @see #getLockWithTimeout(String, long, Handler)
     * @param timeout The timeout in ms
     * @return a {@link Future} of {@link Lock} which is only available locally
     */
    public Future<Lock> getLockWithTimeout(long timeout) {
        return getLockWithTimeout(null, timeout);
    }

    @Override
    public Future<Lock> getLockWithTimeout(String name, long timeout) {
        return sharedData.getLockWithTimeout(sharedName(name), timeout);
    }

    /**
     * Returns a lock with a timeout associated to the given class.
     *
     * @see #getLockWithTimeout(String, long, Handler)
     * @param timeout       The timeout in ms
     * @param resultHandler The handler
     */
    public void getLockWithTimeout(long timeout, Handler<AsyncResult<Lock>> resultHandler) {
        getLockWithTimeout(null, timeout, resultHandler);
    }

    /**
     * @deprecated Deprecated since Vert.x 4.x (callback model), use {@link SharedDataAccessor#getLockWithTimeout(long)}
     *             (future-based) instead. Removed in Vert.x 5.
     * @param name          name of the lock
     * @param timeout       timeout in ms
     * @param resultHandler the handler to return the lock asynchronously
     */
    @Deprecated(since = "4.x", forRemoval = true)
    public void getLockWithTimeout(String name, long timeout, Handler<AsyncResult<Lock>> resultHandler) {
        sharedData.getLockWithTimeout(sharedName(name), timeout)
                .onComplete(asyncResult -> resultHandler.handle(asyncResult));
    }

    /**
     * Returns the future of a local lock with a timeout associated to the given class.
     *
     * @see #getLocalLockWithTimeout(String, long, Handler)
     * @param timeout The timeout in ms
     * @return a {@link Future} of {@link Lock} which is only available locally
     */
    public Future<Lock> getLocalLockWithTimeout(long timeout) {
        return getLocalLockWithTimeout(null, timeout);
    }

    @Override
    public Future<Lock> getLocalLockWithTimeout(String name, long timeout) {
        return sharedData.getLocalLockWithTimeout(sharedName(name), timeout);
    }

    /**
     * Returns a local lock with a timeout associated to the given class.
     *
     * @see #getLocalLockWithTimeout(String, long, Handler)
     * @param timeout       The timeout in ms
     * @param resultHandler The handler
     */
    public void getLocalLockWithTimeout(long timeout, Handler<AsyncResult<Lock>> resultHandler) {
        getLocalLockWithTimeout(null, timeout, resultHandler);
    }

    /**
     * @deprecated Deprecated since Vert.x 4.x (callback model), use
     *             {@link SharedDataAccessor#getLocalLockWithTimeout(long)} (future-based) instead. Removed in Vert.x 5.
     * @param name          name of the lock
     * @param timeout       timeout in ms
     * @param resultHandler the handler to return the lock asynchronously
     */
    @Deprecated(since = "4.x", forRemoval = true)
    public void getLocalLockWithTimeout(String name, long timeout, Handler<AsyncResult<Lock>> resultHandler) {
        sharedData.getLocalLockWithTimeout(sharedName(name), timeout)
                .onComplete(asyncResult -> resultHandler.handle(asyncResult));
    }

    /**
     * Returns a local map to be used storing shared data for the associated class.
     *
     * @see #getLocalMap(String)
     * @param <K> The type of the keys of the {@link LocalMap}
     * @param <V> The type of the values of the {@link LocalMap}
     * @return The map
     */
    public <K, V> LocalMap<K, V> getLocalMap() {
        return getLocalMap(null);
    }

    @Override
    public <K, V> LocalMap<K, V> getLocalMap(String name) {
        return sharedData.getLocalMap(sharedName(name));
    }

    private String sharedName(String name) {
        return String.format("%s-%s#%s", NeonBee.class.getSimpleName(), accessClass.getName(),
                Optional.ofNullable(name).orElse(DEFAULT_NAME));
    }

    private <K, V> void handleSuccess(AsyncMap<K, V> map, Handler<AsyncResult<AsyncMap<K, V>>> resultHandler) {
        resultHandler.handle(succeededFuture(map));
    }

    private <K, V> void handleFailure(Throwable cause, Handler<AsyncResult<AsyncMap<K, V>>> resultHandler) {
        resultHandler.handle(failedFuture(cause));
    }
}
