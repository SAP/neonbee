package io.neonbee.internal.registry;

import static io.vertx.core.Future.succeededFuture;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import com.google.common.base.Joiner;

import io.neonbee.internal.SharedDataAccessor;
import io.neonbee.logging.LoggingFacade;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.shareddata.AsyncMap;

/**
 * A registry that is using a plain {@link SharedDataAccessor#getAsyncMap(String)} shared map.
 * <p>
 * <b>Warning:</b> This {@link Registry} implementation is not waiting for locks, or has other overhead. If the
 * application logic itself take care about this (e.g. only using unique keys) this could be a super-fast registry
 * implementation. If not have a look into {@link WriteSafeRegistry} or {@link SelfCleaningRegistry}.
 * <p>
 * The values under the key are stored in a JsonArray, which means only types that can be serialized and deserialized by
 * jackson are allowed.
 */
public class NonLockingRegistry<T> implements Registry<T> {
    private static final LoggingFacade LOGGER = LoggingFacade.create();

    protected final String registryName;

    protected final Vertx vertx;

    private final SharedDataAccessor sharedDataAccessor;

    /**
     * Create a new {@link NonLockingRegistry}.
     *
     * @param vertx        the {@link Vertx} instance
     * @param registryName the name of the map registry
     */
    public NonLockingRegistry(Vertx vertx, String registryName) {
        this.vertx = vertx;
        this.registryName = registryName;
        this.sharedDataAccessor = new SharedDataAccessor(vertx, this.getClass());
    }

    @Override
    public Future<Void> register(String key, Collection<T> values) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Begin to register value \"{}\" for key \"{}\" in registry \"{}\"", toString(values), key,
                    registryName);
        }
        Future<AsyncMap<String, JsonArray>> sharedMap = getSharedMap();

        return sharedMap.compose(map -> map.get(key))
                .map(valueOrNull -> valueOrNull != null ? valueOrNull : new JsonArray())
                .compose(valueArray -> {
                    for (T value : values) {
                        if (!valueArray.contains(value)) {
                            valueArray.add(value);
                        }
                    }

                    return sharedMap.compose(map -> map.put(key, valueArray))
                            .onSuccess(v -> {
                                if (LOGGER.isDebugEnabled()) {
                                    LOGGER.debug(
                                            "Register value \"{}\" for key \"{}\" in registry \"{}\" has been finished",
                                            toString(values),
                                            key, registryName);
                                }
                            });
                });
    }

    @Override
    public Future<Void> unregister(String key, Collection<T> values) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Begin to unregister value \"{}\" for key \"{}\" in registry \"{}\"", toString(values), key,
                    registryName);
        }
        Future<AsyncMap<String, JsonArray>> sharedMap = getSharedMap();

        return sharedMap.compose(map -> map.get(key)).compose(registeredValues -> {
            if (registeredValues == null) {
                return succeededFuture();
            }

            for (T value : values) {
                registeredValues.remove(value);
            }

            return sharedMap.compose(map -> {
                if (registeredValues.isEmpty()) {
                    LOGGER.debug("No value left for key \"{}\" in registry \"{}\" -> remove key from registry", key,
                            registryName);
                    return map.remove(key).mapEmpty();
                } else {
                    return map.put(key, registeredValues);
                }
            }).onSuccess(v -> {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug(
                            "Unregister value \"{}\" for key \"{}\" in registry \"{}\" has been finished",
                            toString(values), key, registryName);
                }
            });
        });
    }

    @Override
    @SuppressWarnings("unchecked")
    public Future<List<T>> get(String key) {
        return getSharedMap().compose(map -> map.get(key)).map(values -> {
            if (values == null) {
                return List.of();
            } else {
                List<T> transformedValues = new ArrayList<>();
                // It is important to call forEach here, because the iterator of JsonArray wraps the JSON values into a
                // Java type.
                values.forEach(value -> transformedValues.add((T) value));
                return transformedValues;
            }
        });
    }

    @Override
    public Future<Set<String>> getKeys() {
        return getSharedMap().compose(AsyncMap::keys);
    }

    private Future<AsyncMap<String, JsonArray>> getSharedMap() {
        return sharedDataAccessor.getAsyncMap(registryName);
    }

    private String toString(Collection<T> values) {
        return Joiner.on(", ").join(values);
    }
}
