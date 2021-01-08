package io.neonbee.internal;

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
     * @see #getAsyncMap(String, Handler)
     * @param resultHandler The handler which returns the map asynchronously
     * @param <K>           The type of the key of the async map entries
     * @param <V>           The type of the value of the async map entries
     */
    public <K, V> void getAsyncMap(Handler<AsyncResult<AsyncMap<K, V>>> resultHandler) {
        getAsyncMap(null, resultHandler);
    }

    @Override
    public <K, V> void getAsyncMap(String name, Handler<AsyncResult<AsyncMap<K, V>>> resultHandler) {
        sharedData.getAsyncMap(sharedName(name), resultHandler);
    }

    /**
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
     * @see #getLocalAsyncMap(String, Handler)
     * @param resultHandler The handler which returns the map asynchronously
     * @param <K>           The type of the key of the async map entries
     * @param <V>           The type of the value of the async map entries
     */
    public <K, V> void getLocalAsyncMap(Handler<AsyncResult<AsyncMap<K, V>>> resultHandler) {
        getLocalAsyncMap(null, resultHandler);
    }

    @Override
    public <K, V> void getLocalAsyncMap(String name, Handler<AsyncResult<AsyncMap<K, V>>> resultHandler) {
        sharedData.getLocalAsyncMap(sharedName(name), resultHandler);
    }

    /**
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
     * @see #getClusterWideMap(String, Handler)
     * @param resultHandler The handler which returns the map asynchronously
     * @param <K>           The type of the key of the async map entries
     * @param <V>           The type of the value of the async map entries
     */
    public <K, V> void getClusterWideMap(Handler<AsyncResult<AsyncMap<K, V>>> resultHandler) {
        getClusterWideMap(null, resultHandler);
    }

    @Override
    public <K, V> void getClusterWideMap(String name, Handler<AsyncResult<AsyncMap<K, V>>> resultHandler) {
        sharedData.getClusterWideMap(sharedName(name), resultHandler);
    }

    /**
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
     * @see #getCounter(String, Handler)
     * @param resultHandler The handler
     */
    public void getCounter(Handler<AsyncResult<Counter>> resultHandler) {
        getCounter(null, resultHandler);
    }

    @Override
    public void getCounter(String name, Handler<AsyncResult<Counter>> resultHandler) {
        sharedData.getCounter(sharedName(name), resultHandler);
    }

    /**
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
     * @see #getLocalCounter(String, Handler)
     * @param resultHandler The handler
     */
    public void getLocalCounter(Handler<AsyncResult<Counter>> resultHandler) {
        getLocalCounter(null, resultHandler);
    }

    @Override
    public void getLocalCounter(String name, Handler<AsyncResult<Counter>> resultHandler) {
        sharedData.getLocalCounter(sharedName(name), resultHandler);
    }

    /**
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
     * @see #getLock(String, Handler)
     * @param resultHandler The handler
     */
    public void getLock(Handler<AsyncResult<Lock>> resultHandler) {
        getLock(null, resultHandler);
    }

    @Override
    public void getLock(String name, Handler<AsyncResult<Lock>> resultHandler) {
        sharedData.getLock(sharedName(name), resultHandler);
    }

    /**
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
     * @see #getLocalLock(String, Handler)
     * @param resultHandler The handler
     */
    public void getLocalLock(Handler<AsyncResult<Lock>> resultHandler) {
        getLocalLock(null, resultHandler);
    }

    @Override
    public void getLocalLock(String name, Handler<AsyncResult<Lock>> resultHandler) {
        sharedData.getLocalLock(sharedName(name), resultHandler);
    }

    /**
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
     * @see #getLockWithTimeout(String, long, Handler)
     * @param timeout       The timeout in ms
     * @param resultHandler The handler
     */
    public void getLockWithTimeout(long timeout, Handler<AsyncResult<Lock>> resultHandler) {
        getLockWithTimeout(null, timeout, resultHandler);
    }

    @Override
    public void getLockWithTimeout(String name, long timeout, Handler<AsyncResult<Lock>> resultHandler) {
        sharedData.getLockWithTimeout(sharedName(name), timeout, resultHandler);
    }

    /**
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
     * @see #getLocalLockWithTimeout(String, long, Handler)
     * @param timeout       The timeout in ms
     * @param resultHandler The handler
     */
    public void getLocalLockWithTimeout(long timeout, Handler<AsyncResult<Lock>> resultHandler) {
        getLocalLockWithTimeout(null, timeout, resultHandler);
    }

    @Override
    public void getLocalLockWithTimeout(String name, long timeout, Handler<AsyncResult<Lock>> resultHandler) {
        sharedData.getLocalLockWithTimeout(sharedName(name), timeout, resultHandler);
    }

    /**
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
}
