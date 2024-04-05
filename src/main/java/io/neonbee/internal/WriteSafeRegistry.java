package io.neonbee.internal;

import static io.vertx.core.Future.succeededFuture;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import io.neonbee.NeonBee;
import io.neonbee.logging.LoggingFacade;
import io.neonbee.registry.Registry;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.shareddata.AsyncMap;

/**
 * A registry to manage values in the {@link SharedDataAccessor} shared map.
 * <p>
 * The values under the key are stored in a JsonArray.
 *
 * @param <T> the type of data this registry stores
 */
public class WriteSafeRegistry<T> implements Registry<T> {

    private final LoggingFacade logger = LoggingFacade.create();

    private final SharedDataAccessor sharedDataAccessor;

    private final String registryName;

    /**
     * Create a new {@link WriteSafeRegistry}.
     *
     * @param vertx        the {@link Vertx} instance
     * @param registryName the name of the map registry
     */
    public WriteSafeRegistry(Vertx vertx, String registryName) {
        this.registryName = registryName;
        this.sharedDataAccessor = new SharedDataAccessor(vertx, this.getClass());
    }

    /**
     * Register a value in the async shared map of {@link NeonBee} by key.
     *
     * @param sharedMapKey the shared map key
     * @param value        the value to register
     * @return the future
     */
    @Override
    public Future<Void> register(String sharedMapKey, T value) {
        logger.info("register value: \"{}\" in shared map: \"{}\"", sharedMapKey, value);

        return lock(sharedMapKey, () -> addValue(sharedMapKey, value));
    }

    @Override
    public Future<List<T>> get(String key) {
        return getSharedMap()
                .compose(map -> map.get(key))
                .map(values -> {
                    if (values == null) {
                        return List.of();
                    } else {
                        List<T> transformedValues = new ArrayList<>();
                        // It is important to call forEach here, because the iterator of JsonArray
                        // wraps the JSON values into a Java type.
                        values.forEach(value -> transformedValues.add((T) value));
                        return transformedValues;
                    }
                });
    }

    /**
     * Method that acquires a lock for the sharedMapKey and released the lock after the futureSupplier is executed.
     *
     * @param sharedMapKey   the shared map key
     * @param futureSupplier supplier for the future to be secured by the lock
     * @return the futureSupplier
     */
    private Future<Void> lock(String sharedMapKey, Supplier<Future<Void>> futureSupplier) {
        logger.debug("Get lock for {}", sharedMapKey);
        return sharedDataAccessor.getLock(sharedMapKey)
                .onFailure(throwable -> {
                    logger.error("Error acquiring lock for {}", sharedMapKey, throwable);
                }).compose(lock -> futureSupplier.get().onComplete(anyResult -> {
                    logger.debug("Releasing lock for {}", sharedMapKey);
                    lock.release();
                }));
    }

    private Future<Void> addValue(String sharedMapKey, T value) {
        Future<AsyncMap<String, JsonArray>> sharedMap = getSharedMap();

        return sharedMap.compose(map -> map.get(sharedMapKey))
                .map(valueOrNull -> valueOrNull != null ? valueOrNull : new JsonArray())
                .compose(values -> {
                    if (!values.contains(value)) {
                        values.add(value);
                    }

                    if (logger.isInfoEnabled()) {
                        logger.info("Registered verticle {} in shared map.", value);
                    }

                    return sharedMap.compose(map -> map.put(sharedMapKey, values));
                });
    }

    /**
     * Unregister the value in the {@link NeonBee} async shared map from the sharedMapKey.
     *
     * @param sharedMapKey the shared map key
     * @param value        the value to unregister
     * @return the future
     */
    @Override
    public Future<Void> unregister(String sharedMapKey, T value) {
        logger.debug("unregister value: \"{}\" from shared map: \"{}\"", sharedMapKey, value);

        return lock(sharedMapKey, () -> removeValue(sharedMapKey, value));
    }

    private Future<Void> removeValue(String sharedMapKey, Object value) {
        Future<AsyncMap<String, JsonArray>> sharedMap = getSharedMap();

        return sharedMap.compose(map -> map.get(sharedMapKey))
                .compose(values -> {
                    if (values == null) {
                        return succeededFuture();
                    }

                    if (logger.isInfoEnabled()) {
                        logger.info("Unregistered verticle {} in shared map.", value);
                    }

                    values.remove(value);
                    return sharedMap.compose(map -> map.put(sharedMapKey, values));
                });
    }

    /**
     * Shared map that is used as registry.
     *
     * @return Future to the shared map
     */
    public Future<AsyncMap<String, JsonArray>> getSharedMap() {
        return sharedDataAccessor.getAsyncMap(registryName);
    }
}
