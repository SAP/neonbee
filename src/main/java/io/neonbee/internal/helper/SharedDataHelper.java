package io.neonbee.internal.helper;

import java.util.function.Supplier;

import io.neonbee.internal.SharedDataAccessor;
import io.neonbee.logging.LoggingFacade;
import io.vertx.core.Future;
import io.vertx.core.Vertx;

public final class SharedDataHelper {
    private static final LoggingFacade LOGGER = LoggingFacade.create();

    private static SharedDataAccessor getSharedDataAccessor(Vertx vertx) {
        return new SharedDataAccessor(vertx, SharedDataHelper.class);
    }

    /**
     * Method that acquires a lock for the key and released the lock after the futureSupplier is executed.
     *
     * @param vertx          the related Vert.x instance
     * @param key            the shared map key
     * @param futureSupplier supplier for the future to be secured by the lock
     * @return the futureSupplier
     */
    // FIXME: this lock method could lead to unwanted lock wait because every one that use the same key would have to
    // wait to acquire the lock even when the keys are unrelated
    public static Future<Void> lock(Vertx vertx, String key, Supplier<Future<Void>> futureSupplier) {
        LOGGER.debug("Get lock for key \"{}\"", key);
        return getSharedDataAccessor(vertx)
                .getLock(key)
                .onFailure(throwable -> LOGGER.error("Error acquiring lock for key \"{}\"", key, throwable))
                .compose(lock -> {
                    LOGGER.debug("Received lock for key \"{}\"", key);
                    return futureSupplier.get().onComplete(anyResult -> {
                        LOGGER.debug("Releasing lock for key \"{}\"", key);
                        lock.release();
                    });
                });
    }

    /**
     * This helper class cannot be instantiated.
     */
    private SharedDataHelper() {}
}
