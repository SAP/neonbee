package io.neonbee.data;

import java.util.AbstractMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.olingo.commons.api.edm.FullQualifiedName;

import io.neonbee.entity.EntityWrapper;
import io.neonbee.internal.helper.FunctionalHelper;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;

/**
 * A (unmodifiable) map between a data request and the respective asynchronous. result for the request.
 *
 * The DataMap can either be used {@link Map}-like, with the methods provided by the {@link Map} interface, as well as
 * additional methods like {@link #findFirst(String)} or {@link #findAll(String)} or
 * {@link AsyncResult}/{@link CompositeFuture}-like (which itself resembles more a {@link java.util.List}) with the
 * methods provided by the {@link AsyncResult} interface, as well as additional methods like {@link #resultFor(String)}
 * or {@link #succeeded(String)}. The two types of methods can be used interchangeably and sometimes are just an alias
 * for the same logic executed.
 */
public class DataMap extends AbstractMap<DataRequest, AsyncResult<?>> implements AsyncResult<DataMap> {
    private Set<Entry<DataRequest, AsyncResult<?>>> entries;

    /**
     * Initialize the data map with a underlying map.
     *
     * @param map the map to use the entries from
     */
    public DataMap(Map<DataRequest, AsyncResult<?>> map) {
        this(map.entrySet());
    }

    /**
     * Initialize the data map with a given set of entries.
     *
     * @param entries the entries of the map
     */
    public DataMap(Set<Entry<DataRequest, AsyncResult<?>>> entries) {
        super();
        this.entries = entries;
    }

    /*
     * Begin of {@link Map}-like interface methods
     */

    // according to the JavaDoc of @{link AbstractMap}, to implement a unmodifiable
    // map it is sufficient, to only implement the entrySet() method of AbstractMap
    @Override
    public Set<Entry<DataRequest, AsyncResult<?>>> entrySet() {
        return entries;
    }

    /**
     * Returns the result for a given data verticle. In case multiple data requests have been made to the same verticle,
     * this method will return the first result only
     *
     * @param qualifiedName the qualified name of the verticle, the data was required from
     * @param <U>           The type of the result
     * @return the result of the data request, or null in case no data was required from this verticle
     */
    public <U> Optional<AsyncResult<U>> findFirst(String qualifiedName) {
        return this.<U>entryStream(qualifiedName).findFirst().map(Entry::getValue);
    }

    /**
     * Returns entities required for a given entity verticle. In case multiple data requests have been made to the same
     * verticle, this method will return the first result only
     *
     * @see #findFirst(FullQualifiedName)
     * @param entityTypeNamespace the namespace of the entity type data was required from
     * @param entityTypeName      the name of the entity type data was required from
     * @return the result of the data request, or null in case no data was required from this verticle
     */
    public Optional<AsyncResult<EntityWrapper>> findFirst(String entityTypeNamespace, String entityTypeName) {
        return entryStream(entityTypeNamespace, entityTypeName).findFirst().map(Entry::getValue);
    }

    /**
     * Returns entities required for a given entity verticle. In case multiple data requests have been made to the same
     * verticle, this method will return the first result only
     *
     * @param entityTypeName the full qualified name of the entity type data was required from
     * @return the result of the data request, or null in case no data was required from this verticle
     */
    public Optional<AsyncResult<EntityWrapper>> findFirst(FullQualifiedName entityTypeName) {
        return entryStream(entityTypeName).findFirst().map(Entry::getValue);
    }

    /**
     * Returns all results for a given data verticle in order.
     *
     * @param qualifiedName the qualified name of the verticle, the data was required from
     * @param <U>           The type of the results
     * @return the list of results, or an empty list in case no data was required from this verticle
     */
    public <U> List<AsyncResult<U>> findAll(String qualifiedName) {
        return this.<U>entryStream(qualifiedName).map(Entry::getValue).collect(Collectors.toList());
    }

    /**
     * Returns all results for a given entity verticle in order.
     *
     * @see #findAll(FullQualifiedName)
     * @param entityTypeNamespace the namespace of the entity type data was required from
     * @param entityTypeName      the name of the entity type data was required from
     * @return the list of results, or an empty list in case no data was required from this verticle
     */
    public List<AsyncResult<EntityWrapper>> findAll(String entityTypeNamespace, String entityTypeName) {
        return entryStream(entityTypeNamespace, entityTypeName).map(Entry::getValue).collect(Collectors.toList());
    }

    /**
     * Returns all results for a given entity verticle in order.
     *
     * @param entityTypeName the full qualified name of the entity type data was required from
     * @return the list of results, or an empty list in case no data was required from this verticle
     */
    public List<AsyncResult<EntityWrapper>> findAll(FullQualifiedName entityTypeName) {
        return entryStream(entityTypeName).map(Entry::getValue).collect(Collectors.toList());
    }

    /**
     * Convenience method for finding any failed asynchronous result.
     */
    @SuppressWarnings("unchecked")
    private Optional<AsyncResult<?>> findAnyFailed() {
        return (Optional<AsyncResult<?>>) (Optional<?>) entryStream().map(Entry::getValue).filter(AsyncResult::failed)
                .findAny();
    }

    /**
     * Convenience method for finding any failed asynchronous result.
     */
    @SuppressWarnings("unchecked")
    private Optional<AsyncResult<?>> findAnyFailed(String qualifiedName) {
        return (Optional<AsyncResult<?>>) (Optional<?>) entryStream(qualifiedName).map(Entry::getValue)
                .filter(AsyncResult::failed).findAny();
    }

    /**
     * Convenience method for finding any failed asynchronous result.
     */
    @SuppressWarnings("unchecked")
    private Optional<AsyncResult<?>> findAnyFailed(String entityTypeNamespace, String entityTypeName) {
        return (Optional<AsyncResult<?>>) (Optional<?>) entryStream(entityTypeNamespace, entityTypeName)
                .map(Entry::getValue).filter(AsyncResult::failed).findAny();
    }

    /**
     * Convenience method for finding any failed asynchronous result.
     */
    @SuppressWarnings("unchecked")
    private Optional<AsyncResult<?>> findAnyFailed(FullQualifiedName entityTypeName) {
        return (Optional<AsyncResult<?>>) (Optional<?>) entryStream(entityTypeName).map(Entry::getValue)
                .filter(AsyncResult::failed).findAny();
    }

    /**
     * Returns a sub map of elements of a given verticle.
     *
     * @param qualifiedName the qualified name of the verticle
     * @return a map, only containing the elements
     */
    @SuppressWarnings("unchecked")
    public DataMap subMap(String qualifiedName) {
        return new DataMap((Set<Entry<DataRequest, AsyncResult<?>>>) (Set<?>) entryStream(qualifiedName)
                .collect(Collectors.toCollection(LinkedHashSet::new)));
    }

    /**
     * Returns a sub map of elements of a given entity verticle.
     *
     * @see #subMap(FullQualifiedName)
     * @param entityTypeNamespace the namespace of the entity type data was required from
     * @param entityTypeName      the name of the entity type data was required from
     * @return a map, only containing the elements
     */
    @SuppressWarnings("unchecked")
    public DataMap subMap(String entityTypeNamespace, String entityTypeName) {
        return new DataMap(
                (Set<Entry<DataRequest, AsyncResult<?>>>) (Set<?>) entryStream(entityTypeNamespace, entityTypeName)
                        .collect(Collectors.toCollection(LinkedHashSet::new)));
    }

    /**
     * Returns a sub map of elements of a given entity verticle.
     *
     * @param entityTypeName the full qualified name of the entity type data was required from
     * @return a map, only containing the elements
     */
    @SuppressWarnings("unchecked")
    public DataMap subMap(FullQualifiedName entityTypeName) {
        return new DataMap((Set<Entry<DataRequest, AsyncResult<?>>>) (Set<?>) entryStream(entityTypeName)
                .collect(Collectors.toCollection(LinkedHashSet::new)));
    }

    /**
     * Convenience stream method for streaming entries.
     */
    private Stream<Entry<DataRequest, AsyncResult<?>>> entryStream() {
        return entries.stream();
    }

    /**
     * Convenience stream method for filtering entries with only the qualifiedName specified.
     */
    private <U> Stream<Entry<DataRequest, AsyncResult<U>>> entryStream(String qualifiedName) {
        return entryStream().filter(entry -> qualifiedName.equals(entry.getKey().getQualifiedName()))
                .map(FunctionalHelper::uncheckedMapper);
    }

    /**
     * Convenience stream method for filtering entries with only the entityTypeNamespace / entityTypeName specified.
     */
    private Stream<Entry<DataRequest, AsyncResult<EntityWrapper>>> entryStream(String entityTypeNamespace,
            String entityTypeName) {
        return entryStream().filter(entry -> {
            FullQualifiedName entryEntityTypeName = entry.getKey().getEntityTypeName();
            return entityTypeNamespace.equals(entryEntityTypeName.getNamespace())
                    && entityTypeName.equals(entryEntityTypeName.getName());
        }).map(FunctionalHelper::uncheckedMapper);
    }

    /**
     * Convenience stream method for filtering entries with only the entityTypeName specified.
     */
    private Stream<Entry<DataRequest, AsyncResult<EntityWrapper>>> entryStream(FullQualifiedName entityTypeName) {
        return entryStream().filter(entry -> entityTypeName.equals(entry.getKey().getEntityTypeName()))
                .map(FunctionalHelper::uncheckedMapper);
    }

    /*
     * Begin of {@link AsyncResult}/{@link java.util.List}-like interface methods
     */
    @Override
    public DataMap result() {
        return this;
    }

    /**
     * Returns the result for the matching {@link DataRequest}.
     *
     * @see #findFirst(String)
     *
     * @param qualifiedName The qualified name of the DataVerticle, data is requested from
     * @param <U>           The type of the result of the data request
     * @return The result of the data request, or null in case no data was required from the verticle
     */
    @SuppressWarnings("TypeParameterUnusedInFormals")
    public <U> U resultFor(String qualifiedName) {
        return this.<U>findFirst(qualifiedName).map(AsyncResult::result).orElse(null);
    }

    /**
     * Returns the result for the matching {@link DataRequest}.
     *
     * @see #findFirst(String, String)
     * @param entityTypeNamespace The namespace of the entity type, data is requested from
     * @param entityTypeName      The full qualified name of the entity type, data is requested from
     * @return The result of the data request, or null in case no data was required from the verticle
     */
    public EntityWrapper resultFor(String entityTypeNamespace, String entityTypeName) {
        return findFirst(entityTypeNamespace, entityTypeName).map(AsyncResult::result).orElse(null);
    }

    /**
     * Returns the result for the matching {@link DataRequest}.
     *
     * @see #findFirst(FullQualifiedName)
     * @param entityTypeName The full qualified name of the entity type, data is requested from
     * @return The result of the data request, or null in case no data was required from the verticle
     */
    public EntityWrapper resultFor(FullQualifiedName entityTypeName) {
        return findFirst(entityTypeName).map(AsyncResult::result).orElse(null);
    }

    /**
     * Returns the result for the matching {@link DataRequest}.
     *
     * @return a list of all results of this DataMap, in case the corresponding data request is needed, see
     *         {@link #entrySet()} or the other {@link Map}-like methods
     */
    public List<?> results() {
        return entryStream().map(Entry::getValue).map(AsyncResult::result).collect(Collectors.toList());
    }

    /**
     * Returns the result for the matching {@link DataRequest}.
     *
     * @see #findAll(String)
     * @param qualifiedName The qualified name of the entity verticle, data was required from
     * @param <U>           The type of the returned list elements
     * @return A list with the results
     */
    public <U> List<U> resultsFor(String qualifiedName) {
        return this.<U>entryStream(qualifiedName).map(Entry::getValue).map(AsyncResult::result)
                .collect(Collectors.toList());
    }

    /**
     * Returns the result for the matching {@link DataRequest}.
     *
     * @see #findAll(String, String)
     * @param entityTypeNamespace The namespace of the entity type
     * @param entityTypeName      The full qualified name of the entity type
     * @return A list of type {@link EntityWrapper} with the results
     */
    public List<EntityWrapper> resultsFor(String entityTypeNamespace, String entityTypeName) {
        return entryStream(entityTypeNamespace, entityTypeName).map(Entry::getValue).map(AsyncResult::result)
                .collect(Collectors.toList());
    }

    /**
     * Returns the result for the matching {@link DataRequest}.
     *
     * @see #findAll(FullQualifiedName)
     * @param entityTypeName The full qualified name of the entity type
     * @return A list of type {@link EntityWrapper} with the results
     */
    public List<EntityWrapper> resultsFor(FullQualifiedName entityTypeName) {
        return entryStream(entityTypeName).map(Entry::getValue).map(AsyncResult::result).collect(Collectors.toList());
    }

    @Override
    public boolean succeeded() {
        return !failed();
    }

    /**
     * Checks if all results for a given data verticle succeeded.
     *
     * @param qualifiedName the qualified name of the verticle, the data was required from
     * @return true if all data verticle with the given qualifiedName succeeded
     * @see #findAll(String)
     */
    public boolean succeeded(String qualifiedName) {
        return !failed(qualifiedName);
    }

    /**
     * Checks if all requests for a given entity verticle succeeded.
     *
     * @param entityTypeNamespace the namespace of the entity type data was required from
     * @param entityTypeName      the name of the entity type data was required from
     * @return true if all entity verticle with the given entityTypeNamespace and entityTypeName succeeded
     * @see #findAll(String, String)
     */
    public boolean succeeded(String entityTypeNamespace, String entityTypeName) {
        return !failed(entityTypeNamespace, entityTypeName);
    }

    /**
     * Checks if all requests for a given entity verticle succeeded.
     *
     * @param entityTypeName the full qualified name of the entity type data was required from
     * @return true if all entity verticle with the given entityTypeName succeeded
     * @see #findAll(FullQualifiedName)
     */
    public boolean succeeded(FullQualifiedName entityTypeName) {
        return !failed(entityTypeName);
    }

    @Override
    public boolean failed() {
        return findAnyFailed().isPresent();
    }

    /**
     * Checks if any result for a given data verticle failed.
     *
     * @param qualifiedName the qualified name of the verticle, the data was required from
     * @return true if any data verticle with the given qualifiedName failed
     */
    public boolean failed(String qualifiedName) {
        return findAnyFailed(qualifiedName).isPresent();
    }

    /**
     * Checks if all requests for a given entity verticle succeeded.
     *
     * @param entityTypeNamespace the namespace of the entity type data was required from
     * @param entityTypeName      the name of the entity type data was required from
     * @return true if any entity verticle with the given entityTypeNamespace and entityTypeName failed
     */
    public boolean failed(String entityTypeNamespace, String entityTypeName) {
        return findAnyFailed(entityTypeNamespace, entityTypeName).isPresent();
    }

    /**
     * Checks if all requests for a given entity verticle succeeded.
     *
     * @param entityTypeName the full qualified name of the entity type data was required from
     * @return true if any entity verticle with the given entityTypeName failed
     */
    public boolean failed(FullQualifiedName entityTypeName) {
        return findAnyFailed(entityTypeName).isPresent();
    }

    @Override
    public Throwable cause() {
        return findAnyFailed().map(AsyncResult::cause).orElse(null);
    }

    /**
     * Returns the cause of any failed entity verticle request with this qualifiedName.
     *
     * @param qualifiedName the qualified name of the verticle, the data was required from
     * @return true if any data verticle with the given qualifiedName failed
     */
    public Throwable cause(String qualifiedName) {
        return findAnyFailed(qualifiedName).map(AsyncResult::cause).orElse(null);
    }

    /**
     * Returns the cause of any failed entity verticle request with this entityTypeNamespace and entityTypeName.
     *
     * @param entityTypeNamespace the namespace of the entity type data was required from
     * @param entityTypeName      the name of the entity type data was required from
     * @return true if any entity verticle with the given entityTypeNamespace and entityTypeName failed
     */
    public Throwable cause(String entityTypeNamespace, String entityTypeName) {
        return findAnyFailed(entityTypeNamespace, entityTypeName).map(AsyncResult::cause).orElse(null);
    }

    /**
     * Returns the cause of any failed entity verticle request with this entityTypeName.
     *
     * @param entityTypeName the full qualified name of the entity type data was required from
     * @return true if any entity verticle with the given entityTypeName failed
     */
    public Throwable cause(FullQualifiedName entityTypeName) {
        return findAnyFailed(entityTypeName).map(AsyncResult::cause).orElse(null);
    }
}
