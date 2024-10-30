package io.neonbee.internal;

import static io.vertx.core.Future.succeededFuture;

import io.neonbee.NeonBee;
import io.neonbee.logging.LoggingFacade;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.core.shareddata.SharedData;

/**
 * A registry to manage values in the {@link SharedData} shared map.
 * <p>
 * This class is a generic implementation of a registry that can be used to store values in a shared map.
 *
 * @param <T> the type of the value to store in the shared registry
 */
public class SharedRegistry<T> implements Registry<T> {

    private final LoggingFacade logger = LoggingFacade.create();

    private final SharedData sharedData;

    private final String registryName;

    /**
     * Create a new {@link WriteSafeRegistry}.
     *
     * @param vertx        the {@link Vertx} instance
     * @param registryName the name of the map registry
     */
    public SharedRegistry(Vertx vertx, String registryName) {
        this(registryName, new SharedDataAccessor(vertx, SharedRegistry.class));
    }

    /**
     * Create a new {@link WriteSafeRegistry}.
     *
     * @param registryName the name of the map registry
     * @param sharedData   the shared data
     */
    public SharedRegistry(String registryName, SharedData sharedData) {
        this.registryName = registryName;
        this.sharedData = sharedData;
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
        if (logger.isInfoEnabled()) {
            logger.info("register value: \"{}\" in shared map: \"{}\"", sharedMapKey, value);
        }

        Future<AsyncMap<String, Object>> sharedMap = getSharedMap();

        return sharedMap.compose(map -> map.get(sharedMapKey))
                .map(valueOrNull -> valueOrNull != null ? (JsonArray) valueOrNull : new JsonArray())
                .compose(valueArray -> {
                    if (!valueArray.contains(value)) {
                        valueArray.add(value);
                    }

                    if (logger.isInfoEnabled()) {
                        logger.info("Registered verticle {} in shared map.", value);
                    }

                    return sharedMap.compose(map -> map.put(sharedMapKey, valueArray));
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
        if (logger.isDebugEnabled()) {
            logger.debug("unregister value: \"{}\" from shared map: \"{}\"", sharedMapKey, value);
        }

        Future<AsyncMap<String, Object>> sharedMap = getSharedMap();

        return sharedMap.compose(map -> map.get(sharedMapKey)).map(jsonArray -> (JsonArray) jsonArray)
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

    @Override
    public Future<JsonArray> get(String sharedMapKey) {
        return getSharedMap().compose(map -> map.get(sharedMapKey)).map(o -> (JsonArray) o);
    }

    /**
     * Shared map that is used as registry.
     *
     * @return Future to the shared map
     */
    public Future<AsyncMap<String, Object>> getSharedMap() {
        return sharedData.getAsyncMap(registryName);
    }
}
