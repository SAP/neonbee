package io.neonbee.registry;

import java.util.List;

import io.vertx.core.Future;

/**
 * Interface for an asynchronous registry implementation.
 *
 * @param <T> the type of data this registry stores
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
     * @return future with a List of the registered values
     */
    Future<List<T>> get(String key);
}
