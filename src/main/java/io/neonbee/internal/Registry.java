package io.neonbee.internal;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;

/**
 * Interface for an asynchronous registry implementation.
 */
public interface Registry<T> {

    /**
     * Register a value in the registry.
     *
     * @param key   a key
     * @param value the value to register
     * @return the future
     */
    Future<Void> register(String key, T value);

    /**
     * Unregister a value.
     *
     * @param key   a key
     * @param value the value to unregister
     * @return the future
     */
    Future<Void> unregister(String key, T value);

    /**
     * Get the registered values for the key.
     *
     * @param key a key
     * @return future with a JsonArray of the registered values
     */
    Future<JsonArray> get(String key);
}
