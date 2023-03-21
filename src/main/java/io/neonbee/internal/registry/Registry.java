package io.neonbee.internal.registry;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.vertx.core.Future;

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
    default Future<Void> register(String key, T value) {
        return register(key, List.of(value));
    }

    /**
     * Register multiple unique values in the registry.
     *
     * @param key    a key
     * @param values the values to register
     * @return the future
     */
    Future<Void> register(String key, Collection<T> values);

    /**
     * Unregister a value.
     *
     * @param key   a key
     * @param value the value to unregister
     * @return the future
     */
    default Future<Void> unregister(String key, T value) {
        return unregister(key, List.of(value));
    }

    /**
     * Unregister multiple values.
     *
     * @param key    a key
     * @param values the values to unregister
     * @return the future
     */
    Future<Void> unregister(String key, Collection<T> values);

    /**
     * Get all registered values for the key.
     *
     * @param key a key
     * @return future with the List of the registered values. If no value is registered an empty List will be returned.
     */
    Future<List<T>> get(String key);

    /**
     * Get any registered value for the key. This is a convenient method in case that the registry has a 1:1
     * relationship.
     *
     * @param key a key
     * @return an Optional with any value for the registered key, or an empty one if no value is registered.
     */
    default Future<Optional<T>> getAny(String key) {
        return get(key).map(keys -> keys.stream().findAny());
    }

    /**
     * Get all registered keys.
     *
     * @return future with the Set of the registered keys. If no key is registered an empty Set will be returned.
     */
    Future<Set<String>> getKeys();
}
