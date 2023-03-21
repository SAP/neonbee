package io.neonbee.internal.registry;

import static io.neonbee.internal.helper.SharedDataHelper.lock;
import static io.vertx.core.Future.succeededFuture;

import java.util.Collection;
import java.util.function.Supplier;

import io.vertx.core.Future;
import io.vertx.core.Vertx;

public class WriteSafeRegistry<T> extends NonLockingRegistry<T> {
    /**
     * Create a new {@link WriteSafeRegistry} based on a {@link NonLockingRegistry}.
     *
     * @param vertx        the {@link Vertx} instance
     * @param registryName the name of the map registry
     */
    public WriteSafeRegistry(Vertx vertx, String registryName) {
        super(vertx, registryName);
    }

    @Override
    public Future<Void> register(String key, Collection<T> values) {
        return register(key, values, () -> succeededFuture());
    }

    /**
     * Register multiple unique values in the registry.
     *
     * @param key         a key
     * @param values      the values to register
     * @param alterAction an action that is called after the registry was modified, but before the related lock is
     *                    released.
     * @return the future
     */
    public Future<Void> register(String key, Collection<T> values, Supplier<Future<Void>> alterAction) {
        return lock(vertx, getLockKey(key), () -> super.register(key, values).compose(v -> alterAction.get()));
    }

    @Override
    public Future<Void> unregister(String key, Collection<T> values) {
        return unregister(key, values, () -> succeededFuture());
    }

    /**
     * Unregister multiple values.
     *
     * @param key         a key
     * @param values      the values to unregister
     * @param alterAction an action that is called after the registry was modified, but before the related lock is
     *                    released.
     * @return the future
     */
    public Future<Void> unregister(String key, Collection<T> values, Supplier<Future<Void>> alterAction) {
        return lock(vertx, getLockKey(key), () -> super.unregister(key, values).compose(v -> alterAction.get()));
    }

    private String getLockKey(String key) {
        return registryName + "-" + key;
    }
}
