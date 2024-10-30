package io.neonbee.internal;

import java.util.function.Supplier;

import io.neonbee.NeonBee;
import io.neonbee.logging.LoggingFacade;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.core.shareddata.SharedData;

/**
 * A registry to manage values in the {@link SharedDataAccessor} shared map.
 * <p>
 * The values under the key are stored in a JsonArray.
 *
 * @param <T> the type of data this registry stores
 */
public class WriteSafeRegistry<T> implements Registry<T> {

    private final LoggingFacade logger = LoggingFacade.create();

    private final SharedData sharedData;

    private final Registry<T> sharedRegistry;

    private final String registryName;

    /**
     * Create a new {@link WriteSafeRegistry}.
     *
     * @param vertx        the {@link Vertx} instance
     * @param registryName the name of the map registry
     */
    public WriteSafeRegistry(Vertx vertx, String registryName) {
        this(registryName, new SharedDataAccessor(vertx, WriteSafeRegistry.class));
    }

    /**
     * Create a new {@link WriteSafeRegistry}.
     *
     * @param registryName the name of the map registry
     * @param sharedData   the shared data
     */
    public WriteSafeRegistry(String registryName, SharedData sharedData) {
        this(registryName, sharedData, new SharedRegistry<>(registryName, sharedData));
    }

    /**
     * Create a new {@link WriteSafeRegistry}.
     *
     * @param registryName the name of the map registry
     * @param sharedData   the shared data
     * @param registry     the shared registry
     */
    WriteSafeRegistry(String registryName, SharedData sharedData, Registry<T> registry) {
        this.registryName = registryName;
        this.sharedData = sharedData;
        this.sharedRegistry = registry;
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

        return lock(sharedMapKey, () -> sharedRegistry.register(sharedMapKey, value));
    }

    @Override
    public Future<JsonArray> get(String sharedMapKey) {
        return sharedRegistry.get(sharedMapKey);
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

        return lock(sharedMapKey, () -> sharedRegistry.unregister(sharedMapKey, value));
    }

    /**
     * Method that acquires a lock for the sharedMapKey and released the lock after the futureSupplier is executed.
     *
     * @param sharedMapKey   the shared map key
     * @param futureSupplier supplier for the future to be secured by the lock
     * @return the futureSupplier
     */
    protected Future<Void> lock(String sharedMapKey, Supplier<Future<Void>> futureSupplier) {
        logger.debug("Get lock for {}", sharedMapKey);
        return sharedData.getLock(sharedMapKey).onFailure(throwable -> {
            logger.error("Error acquiring lock for {}", sharedMapKey, throwable);
        }).compose(lock -> Future.<Void>future(event -> futureSupplier.get().onComplete(event))
                .onComplete(anyResult -> {
                    logger.debug("Releasing lock for {}", sharedMapKey);
                    lock.release();
                }));
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
}
