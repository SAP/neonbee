package io.neonbee.data;

import static io.neonbee.internal.Helper.EMPTY;
import static java.util.Objects.requireNonNull;

import java.util.Optional;

import org.apache.olingo.commons.api.edm.FullQualifiedName;

public class DataRequest {
    /**
     * The ResolutionStrategy effectively defines, how DataRequests are processed by Vert.x.
     */
    public enum ResolutionStrategy {
        /**
         * A recursive / head first resolution strategy for the data
         * <p>
         * This strategy will first get the required data of the current verticle by calling the
         * {@link DataVerticle#requireData(DataQuery,DataContext)} method. Individual messages are then sent to all data
         * verticle providing the query. The process will then recursively repeat for the verticle receiving the
         * message. As soon as all futures for the required data complete, the
         * {@link DataVerticle#requireData(DataQuery,DataContext)} method will be invoked and the result will be
         * propagated back to the callee.
         * <p>
         * Advantages: Traffic via the event bus is reduced to a minimum. Web requests are distributed to several nodes.
         * <p>
         * Disadvantages: The {@link DataVerticle#requireData(DataQuery,DataContext)} is the limiting factor, as the
         * data is propagated upwards through the future chain and are called bottom up after one of each other.
         */
        RECURSIVE,

        /**
         * An optimized / parallel / tail first resolution strategy for the data
         * <p>
         * This strategy will first recursively send a message via the event bus to collect all the
         * {@link DataVerticle#requireData(DataQuery,DataContext)} for all the verticle involved (could also be done
         * locally to be even faster). The list of data requests is then optimized, e.g. by removing duplicates and
         * reducing the data requests if possible (e.g. by joining filters). Afterwards the data will be (in parallel)
         * requested from all the verticle the data request was targeted to. As soon as the first requests are finished,
         * it will be checked if the data is enough for any further requests to resolve, by sending further requests to
         * the {@link DataVerticle#requireData(DataQuery,DataContext)} methods via the event bus. This is done via a
         * handler cascade, build up, when determining the required data. If one request resolves, the next verticle
         * will be called immediately.
         * <p>
         * Advantages: Optimizations on the data requests are possible. Initially every data request is send in
         * parallel.
         * <p>
         * Disadvantages: More traffic via the event bus, to first determine the required data and then the required
         * data is sent back and forth in order for the verticle to get the data they needed in the first place. Also
         * the outgoing network traffic could be focused on specific nodes.
         * <p>
         * Notes: Further optimizations possible, e.g. data requests which are only required by one certain verticle (so
         * it could not be optimized in the optimization step), could be directly done by the respective data verticle.
         * This would reduce the amount of data exchanged via the event bus.
         */
        OPTIMIZED
    }

    private DataSource<?> dataSource;

    private DataSink<?> dataSink;

    private String qualifiedName;

    private FullQualifiedName entityTypeName;

    private DataQuery query;

    private ResolutionStrategy resolutionStrategy;

    private long sendTimeout = -1;

    private boolean localOnly;

    private boolean broadcasting;

    private boolean localPreferred = true;

    /**
     * Request data from a DataSource.
     *
     * @param dataSource The DataSource to request the data from
     */
    public DataRequest(DataSource<?> dataSource) {
        this(dataSource, new DataQuery());
    }

    /**
     * Request data from a DataSource w/ a given query.
     *
     * @param dataSource The DataSource to request the data from
     * @param query      The query of data to request from the source
     */
    public DataRequest(DataSource<?> dataSource, DataQuery query) {
        this.dataSource = requireNonNull(dataSource, "the data source cannot be null");
        this.query = requireNonNull(query, "the query cannot be null");
    }

    /**
     * Request a data manipulation from a DataSink.
     *
     * @param dataSink The DataSink to manipulate the data with
     * @param query    The query for the data manipulation, must not be null
     * @param <T>      The type of the {@link DataSink}
     */
    public <T> DataRequest(DataSink<T> dataSink, DataQuery query) {
        this.dataSink = requireNonNull(dataSink, "the data sink cannot be null");
        this.query = requireNonNull(query, "the query cannot be null");
    }

    /**
     * Request data from a DataVerticle via the event bus.
     *
     * @param qualifiedName The qualified name of the DataVerticle
     */
    public DataRequest(String qualifiedName) {
        this(qualifiedName, new DataQuery());
    }

    /**
     * Request data or a data manipulation from a DataVerticle via the event bus w/ a given query.
     *
     * @param qualifiedName The qualified name of the DataVerticle
     * @param query         The query of data to request from the verticle or a data manipulation
     */
    public DataRequest(String qualifiedName, DataQuery query) {
        this.qualifiedName = requireNonNull(qualifiedName, "the qualified name cannot be null");
        this.query = requireNonNull(query, "the query cannot be null");
    }

    /**
     * Request an entity (collection) or a entity manipulation from a EntityVerticle via the event bus w/ a given query.
     *
     * @param entityTypeNamespace The namespace of the entity to request from an entity verticle
     * @param entityTypeName      The name of the entity to request from an entity verticle
     * @param query               The query of data to request or manipulate by the called verticle. The query uriPath
     *                            has to at least contain the name of the entitySet to request from the given entity
     *                            verticle. Which means a data query is a required attribute.
     */
    public DataRequest(String entityTypeNamespace, String entityTypeName, DataQuery query) {
        this(new FullQualifiedName(entityTypeNamespace, entityTypeName), query);
    }

    /**
     * Request an entity (collection) or a entity manipulation from a EntityVerticle via the event bus w/ a given query.
     *
     * @param entityTypeName The full qualified entity name to request from an entity verticle
     * @param query          The query of data to request or manipulate by the called verticle. The query uriPath has to
     *                       at least contain the name of the entitySet to request from the given entity verticle. Which
     *                       means a data query is a required attribute.
     */
    public DataRequest(FullQualifiedName entityTypeName, DataQuery query) {
        this.entityTypeName = requireNonNull(entityTypeName, "the entity type name cannot be null");
        this.query = requireNonNull(query, "the query cannot be null");
    }

    /**
     * Returns the DataSource instance to get the data from.
     *
     * @return the DataSource instance
     */
    public DataSource<?> getDataSource() {
        return dataSource;
    }

    /**
     * Returns the DataSink instance to manipulate the data with.
     *
     * @return The DataSink instance
     */
    public DataSink<?> getDataSink() {
        return dataSink;
    }

    /**
     * Returns the qualified name of the verticle to request the data from.
     *
     * @return The qualified name of the verticle
     */
    public String getQualifiedName() {
        return qualifiedName;
    }

    /**
     * Returns the entityTypeName of the EntityVerticle to request data from.
     *
     * @return the entityTypeName of the EntityVerticle
     */
    public FullQualifiedName getEntityTypeName() {
        return entityTypeName;
    }

    /**
     * Returns the query to request.
     *
     * @return the query
     */
    public DataQuery getQuery() {
        return query;
    }

    /**
     * Returns the resolutionStrategy used for this request.
     *
     * @return the resolutionStrategy
     */
    public ResolutionStrategy getResolutionStrategy() {
        return resolutionStrategy;
    }

    /**
     * Sets the resolutionStrategy for this request.
     *
     * @param resolutionStrategy the resolutionStrategy to set
     * @return this DataRequest for chaining
     */
    public DataRequest setResolutionStrategy(ResolutionStrategy resolutionStrategy) {
        this.resolutionStrategy = resolutionStrategy;
        return this;
    }

    /**
     * Get the send timeout.
     * <p>
     * When sending a message with a response handler a send timeout can be provided. If no response is received within
     * the timeout the handler will be called with a failure.
     *
     * @return the value of send timeout
     */
    public long getSendTimeout() {
        return sendTimeout;
    }

    /**
     * Set the send timeout. For values smaller than 1, the default send timeout will be applied.
     *
     * @param sendTimeout the timeout value, in ms.
     * @return a reference to this, so the API can be used fluently
     */
    public DataRequest setSendTimeout(long sendTimeout) {
        this.sendTimeout = sendTimeout;
        return this;
    }

    /**
     * Check if this request should be executed only locally.
     *
     * @return true if the request is only executed locally, otherwise false.
     */
    public boolean isLocalOnly() {
        return localOnly;
    }

    /**
     * Set if this request should be executed only locally.
     *
     * @param localOnly the localOnly to set
     * @return this DataRequest for chaining
     */
    public DataRequest setLocalOnly(boolean localOnly) {
        this.localOnly = localOnly;
        return this;
    }

    /**
     * Check if this request should be executed locally and only if it can't find a local consumer it is executed
     * cluster wide.
     *
     * @return the localPreferred
     */
    public boolean isLocalPreferred() {
        return localPreferred;
    }

    /**
     * Set if this request should be executed locally and only if it can't find a local consumer it is executed cluster
     * wide.
     *
     * @param localPreferred the localPreferred to set
     * @return this DataRequest for chaining
     */
    public DataRequest setLocalPreferred(boolean localPreferred) {
        this.localPreferred = localPreferred;
        return this;
    }

    /**
     * Will be removed. See Issue #18
     *
     * @return the broadcasting
     */
    public boolean isBroadcasting() {
        return broadcasting;
    }

    /**
     * Broadcasting indicates, that this request will be published to all consumers within the cluster, which have
     * registered as consumer under a target address.
     *
     * In normal cases, where broadcasting is set to false, the request will only be sent to one consumer.
     *
     * @param broadcasting the broadcasting to set
     * @return this DataRequest for chaining
     */
    public DataRequest setBroadcasting(boolean broadcasting) {
        this.broadcasting = broadcasting;
        return this;
    }

    @Override
    public String toString() {
        return Optional.ofNullable(dataSource).map(Object::getClass).map(Class::getName)
                .or(() -> Optional.ofNullable(qualifiedName))
                .or(() -> Optional.ofNullable(entityTypeName).map(Object::toString)).orElse(EMPTY);
    }
}
