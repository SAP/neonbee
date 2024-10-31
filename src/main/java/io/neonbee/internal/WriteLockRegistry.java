package io.neonbee.internal;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import io.neonbee.logging.LoggingFacade;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.core.shareddata.Lock;
import io.vertx.core.shareddata.SharedData;

/**
 * A {@link WriteSafeRegistry} implementation that uses a lock to lock all write operations to ensure that only one
 * operation is executed at a time.
 *
 * @param <T> the type of data this registry stores
 */
public class WriteLockRegistry<T> implements Registry<T> {

    private static final String LOCK_KEY = WriteLockRegistry.class.getName();

    private final LoggingFacade logger = LoggingFacade.create();

    private final Registry<T> registry;

    private final SharedData sharedData;

    private final String registryName;

    private final Vertx vertx;

    /**
     * Create a new {@link WriteSafeRegistry}.
     *
     * @param vertx        the {@link Vertx} instance
     * @param registryName the name of the map registry
     */
    public WriteLockRegistry(Vertx vertx, String registryName) {
        this(vertx, registryName, new SharedDataAccessor(vertx, WriteLockRegistry.class));
    }

    /**
     * Create a new {@link WriteSafeRegistry}.
     *
     * @param vertx        the {@link Vertx} instance
     * @param registryName the name of the map registry
     * @param sharedData   the shared data
     */
    public WriteLockRegistry(Vertx vertx, String registryName, SharedData sharedData) {
        this.vertx = vertx;
        registry = new SharedRegistry<>(registryName, sharedData);
        this.registryName = registryName;
        this.sharedData = sharedData;
    }

    @Override
    public Future<Void> register(String sharedMapKey, T value) {
        return lock(LOCK_KEY)
                .compose(lock -> lock.execute(() -> registry.register(sharedMapKey, value)))
                .mapEmpty();
    }

    @Override
    public Future<Void> unregister(String sharedMapKey, T value) {
        return lock(LOCK_KEY)
                .compose(lock -> lock.execute(() -> registry.unregister(sharedMapKey, value)))
                .mapEmpty();
    }

    @Override
    public Future<JsonArray> get(String key) {
        return registry.get(key);
    }

    /**
     * Method that acquires a lock for the registry and released the lock after the returned TimeOutLock executed the
     * futureSupplier or the timeout is reached.
     *
     * @return the futureSupplier
     */
    public Future<TimeOutLock> lock() {
        return lock(LOCK_KEY);
    }

    /**
     * Method that acquires a lock for the sharedMapKey and released the lock after the futureSupplier is executed.
     *
     * @param sharedMapKey the shared map key
     * @return the futureSupplier
     */
    private Future<TimeOutLock> lock(String sharedMapKey) {
        logger.debug("Get lock for {}", sharedMapKey);
        return sharedData.getLock(sharedMapKey)
                .map(TimeOutLock::new);
    }

    /**
     * Shared map that is used as registry.
     * <p>
     * It is not safe to write to the shared map directly. Use the {@link WriteSafeRegistry#register(String, Object)}
     * and {@link WriteSafeRegistry#unregister(String, Object)} methods.
     *
     * @return Future to the shared map
     */
    public Future<AsyncMap<String, Object>> getSharedMap() {
        return sharedData.getAsyncMap(registryName);
    }

    /**
     * A lock that is released after 10 seconds.
     */
    public class TimeOutLock implements Lock {

        private static final long DEFAULT_TIME_OUT_DELAY_MS = TimeUnit.SECONDS.toMillis(1000);

        private final UUID id = UUID.randomUUID();

        private final LoggingFacade logger = LoggingFacade.create();

        private final Lock lock;

        private final long timerId;

        private final Promise<Void> timeOutPromise;

        private final long startTime;

        private TimeOutLock(Lock lock) {
            this.lock = lock;
            this.timeOutPromise = Promise.promise();
            this.timerId = startTimer();
            this.startTime = System.currentTimeMillis();
            logger.debug("Lock {} created", id);
        }

        private long startTimer() {
            if (logger.isDebugEnabled()) {
                logger.debug("start lock timer {}", id);
            }
            return vertx.setTimer(DEFAULT_TIME_OUT_DELAY_MS, id -> {
                logger.warn("Lock {} timed out after {} ms. Lock is released.", id,
                        startTime - System.currentTimeMillis());
                stopRelease();
                timeOutPromise
                        .fail(new TimeoutException("Lock for " + registryName + " timed out. Lock is released."));
            });
        }

        @Override
        public void release() {
            stopRelease();
        }

        /**
         * Execute the futureSupplier and release the lock after the futureSupplier is executed. If the supplied future
         * does not complete within the timeout, the lock is released.
         *
         * @param futureSupplier the futureSupplier
         * @param <U>            the type of the future
         * @return the future
         */
        public <U> Future<U> execute(Supplier<Future<U>> futureSupplier) {
            if (logger.isDebugEnabled()) {
                logger.debug("execute lock {}", id);
            }
            Future<U> future = futureSupplier.get().onComplete(event -> {
                if (logger.isDebugEnabled()) {
                    logger.debug("completed lock {}, succeeded {}", id, event.succeeded());
                }
                stopRelease();
            });
            Future<U> suppliedFuture = Future.future(future::onComplete);
            Future<Void> timeOutFuture = timeOutPromise.future();
            return Future.all(suppliedFuture, timeOutFuture)
                    .<U>mapEmpty()
                    .recover(throwable -> {
                        if (logger.isDebugEnabled()) {
                            logger.debug("recover lock {}", id);
                        }
                        if (timeOutFuture.failed()) {
                            return Future.failedFuture(timeOutFuture.cause());
                        } else {
                            return suppliedFuture;
                        }
                    });
        }

        private void stopRelease() {
            if (logger.isDebugEnabled()) {
                logger.debug("Releasing lock {}", id);
            }

            vertx.cancelTimer(timerId);
            lock.release();
            timeOutPromise.tryComplete();

            if (logger.isDebugEnabled()) {
                logger.debug("lock {} released", id);
            }
        }
    }
}
