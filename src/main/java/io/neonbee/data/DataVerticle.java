package io.neonbee.data;

import static io.neonbee.data.DataAction.READ;
import static io.neonbee.data.DataException.FAILURE_CODE_MISSING_MESSAGE_CODEC;
import static io.neonbee.data.DataException.FAILURE_CODE_NO_HANDLERS;
import static io.neonbee.data.DataException.FAILURE_CODE_PROCESSING_FAILED;
import static io.neonbee.data.DataException.FAILURE_CODE_TIMEOUT;
import static io.neonbee.data.DataException.FAILURE_CODE_UNKNOWN_STRATEGY;
import static io.neonbee.data.DataRequest.ResolutionStrategy.RECURSIVE;
import static io.neonbee.data.internal.DataContextImpl.decodeContextFromString;
import static io.neonbee.entity.EntityVerticle.requestEntity;
import static io.neonbee.internal.helper.StringHelper.EMPTY;
import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static java.util.Collections.emptyList;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.olingo.commons.api.edm.FullQualifiedName;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;

import io.micrometer.core.instrument.ImmutableTag;
import io.micrometer.core.instrument.Tag;
import io.neonbee.NeonBee;
import io.neonbee.NeonBeeDeployable;
import io.neonbee.config.MetricsConfig;
import io.neonbee.data.DataRequest.ResolutionStrategy;
import io.neonbee.data.internal.DataContextImpl;
import io.neonbee.data.internal.metrics.ConfiguredDataVerticleMetrics;
import io.neonbee.data.internal.metrics.DataVerticleMetrics;
import io.neonbee.internal.helper.FunctionalHelper;
import io.neonbee.logging.LoggingFacade;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.MessageCodec;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.json.JsonObject;

@SuppressWarnings("PMD.GodClass")
public abstract class DataVerticle<T> extends AbstractVerticle implements DataAdapter<T> {
    /**
     * The name of the context header of an event bus message.
     */
    public static final String CONTEXT_HEADER = "context";

    /**
     * Metrics configuration name.
     */
    public static final String CONFIG_METRICS_KEY = "metrics";

    static final String RESOLUTION_STRATEGY_HEADER = "resolutionStrategy";

    private static final LoggingFacade LOGGER = LoggingFacade.create();

    private static final String SUCCEEDED_RESPONSE_COUNT = "succeeded response count";

    @SuppressWarnings("UnnecessaryLambda") // overridden in DummyVerticleHelper, as getNamespace is final
    private final Supplier<String> namespaceSupplier =
            () -> Optional.ofNullable(this.getClass().getAnnotation(NeonBeeDeployable.class))
                    .map(NeonBeeDeployable::namespace).map(Strings::emptyToNull).map(String::toLowerCase).orElse(null);

    private DataVerticleMetrics dataVerticleMetrics;

    /**
     * Requesting data from other DataSources or Data/EntityVerticles.
     *
     * @param vertx   The Vertx instance
     * @param request The DataRequest specifying the data to request
     * @param context The {@link DataContext data context} which keeps track of all the request-level data during a
     *                request
     * @param <U>     The type of the returned future
     * @return a future to the data requested
     */
    public static <U> Future<U> requestData(Vertx vertx, DataRequest request, DataContext context) {
        DataSource<?> dataSource = request.getDataSource();

        if (dataSource != null) {
            return dataSource.retrieveData(request.getQuery(), context).map(FunctionalHelper::uncheckedMapper);
        }

        DataSink<?> dataSink = request.getDataSink();
        if (dataSink != null) {
            return dataSink.manipulateData(request.getQuery(), context).map(FunctionalHelper::uncheckedMapper);
        }

        String qualifiedName = request.getQualifiedName();
        if (qualifiedName != null) {
            /*
             * Event bus outbound message handling.
             */
            LOGGER.correlateWith(context).debug("Sending message via the event bus to {}", qualifiedName);
            String address = getAddress(qualifiedName);
            return vertx.eventBus()
                    .<U>request(address, request.getQuery(), requestDeliveryOptions(vertx, request, context, address))
                    .transform(asyncReply -> {
                        LOGGER.correlateWith(context).debug("Received event bus reply");

                        if (asyncReply.succeeded()) {
                            U body = asyncReply.result().body();
                            if (body instanceof DataException) {
                                if (LOGGER.isWarnEnabled()) {
                                    LOGGER.correlateWith(context).warn("Received a event bus reply failure from {}",
                                            qualifiedName, (DataException) body);
                                }
                                return failedFuture((DataException) body);
                            } else {
                                DataContext responseDataContext =
                                        decodeContextFromString(asyncReply.result().headers().get(CONTEXT_HEADER));
                                context.setData(
                                        Optional.ofNullable(responseDataContext).map(DataContext::data).orElse(null));
                                context.mergeResponseData(Optional.ofNullable(responseDataContext)
                                        .map(DataContext::responseData).orElse(null));
                                return succeededFuture(asyncReply.result().body());
                            }
                        } else {
                            Throwable cause = asyncReply.cause();
                            if (LOGGER.isWarnEnabled()) {
                                LOGGER.correlateWith(context).warn("Failed to receive event bus reply from {}",
                                        qualifiedName, cause);
                            }
                            return failedFuture(mapException(cause));
                        }
                    });
        }

        FullQualifiedName entityTypeName = request.getEntityTypeName();
        if (entityTypeName != null) {
            return requestEntity(vertx, request, context).map(FunctionalHelper::uncheckedMapper);
        }

        return failedFuture(new IllegalArgumentException("Data request did not specify what data to request"));
    }

    /**
     * Convenience method for calling the {@link #requestData(Vertx, DataRequest, DataContext)} method.
     *
     * @param request The DataRequest specifying the data to request
     * @param context The {@link DataContext data context} which keeps track of all the request-level information during
     *                the lifecycle of requesting data
     * @param <U>     The type of the returned {@link Future}
     * @return a future to the data requested
     * @see #requestData(Vertx, DataRequest, DataContext)
     */
    public <U> Future<U> requestData(DataRequest request, DataContext context) {
        LOGGER.correlateWith(context).debug("Data verticle {} requesting data from {}", getQualifiedName(), request);

        Future<U> future = requestData(vertx, request, context);
        reportRequestDataMetrics(request, future);
        return future;
    }

    /**
     * Return a qualified name string for a verticle under a namespace.
     *
     * @param namespace    Namespace of the verticle
     * @param verticleName Name of the verticle
     * @return Qualified name of namespace and verticle name
     */
    public static String createQualifiedName(String namespace, String verticleName) {
        return String.format("%s/%s", namespace.toLowerCase(Locale.ROOT), verticleName);
    }

    /**
     * Computes the event bus address of this data verticle.
     *
     * @return A unique event bus address
     */
    protected final String getAddress() {
        return getAddress(getQualifiedName());
    }

    /**
     * Computes the event bus address for a given data verticle.
     *
     * @param qualifiedName The qualified name of the verticle to compute the address for
     * @return A unique event bus address
     */
    protected static String getAddress(String qualifiedName) {
        return String.format("%s[%s]", DataVerticle.class.getSimpleName(), qualifiedName);
    }

    /**
     * Creates a new delivery options object for any given data request and context.
     *
     * @param vertx   the vertx instance
     * @param request the data request
     * @param context the data context
     * @param address request address
     * @return a new DeliveryOptions
     */
    private static DeliveryOptions requestDeliveryOptions(Vertx vertx, DataRequest request, DataContext context,
            String address) {
        if (context instanceof DataContextImpl) { // will also perform a null check!
            // before encoding the context header, add the current qualified name of the verticle to the path stack
            ((DataContextImpl) context).pushVerticleToPath(request.getQualifiedName());
        }
        DeliveryOptions deliveryOptions = deliveryOptions(vertx, null, context);
        if (context instanceof DataContextImpl) { // will also perform a null check!
            // remove the verticle right after, as the same context (w/o copying) may be reused for multiple requests
            ((DataContextImpl) context).popVerticleFromPath();
        }

        // adapt further delivery options based on the request
        boolean localOnly = request.isLocalOnly()
                || (request.isLocalPreferred() && NeonBee.get(vertx).isLocalConsumerAvailable(address));
        deliveryOptions.setLocalOnly(localOnly);
        if (request.getSendTimeout() > 0) {
            deliveryOptions.setSendTimeout(request.getSendTimeout());
        }

        Optional.ofNullable(request.getResolutionStrategy()).map(ResolutionStrategy::name)
                .ifPresent(value -> deliveryOptions.addHeader(RESOLUTION_STRATEGY_HEADER, value));

        return deliveryOptions;
    }

    /**
     * Creates a new delivery options object for any given context.
     *
     * @param vertx   the vertx instance
     * @param codec   the message codec to use (if any)
     * @param context the data context
     * @return a new DeliveryOptions
     */
    private static DeliveryOptions deliveryOptions(Vertx vertx, MessageCodec<?, ?> codec, DataContext context) {
        DeliveryOptions deliveryOptions = new DeliveryOptions();
        deliveryOptions.setSendTimeout(SECONDS.toMillis(NeonBee.get(vertx).getConfig().getEventBusTimeout()))
                .setCodecName(Optional.ofNullable(codec).map(MessageCodec::name).orElse(null));
        Optional.ofNullable(context).map(DataContextImpl::encodeContextToString)
                .ifPresent(value -> deliveryOptions.addHeader(CONTEXT_HEADER, value));
        return deliveryOptions;
    }

    /**
     * Creates a new data exception for any given throwable cause.
     *
     * @param cause any throwable cause
     * @return a DataException passing the failure code in case it is a ReplyException
     */
    private static DataException mapException(Throwable cause) {
        if (cause instanceof DataException) {
            return (DataException) cause;
        }

        int failureCode = FAILURE_CODE_PROCESSING_FAILED;
        String message = cause.getMessage();

        if (cause instanceof ReplyException) {
            ReplyException replyException = (ReplyException) cause;
            switch (replyException.failureType()) {
            case NO_HANDLERS:
                failureCode = FAILURE_CODE_NO_HANDLERS;
                break;
            case TIMEOUT:
                failureCode = FAILURE_CODE_TIMEOUT;
                break;
            default:
                failureCode = replyException.failureCode();
                break;
            }
        }

        return new DataException(failureCode, message);
    }

    /**
     * The name of this data verticle (must be unique in one cluster)
     * <p>
     * Similar to the EventBus in Vert.x, NeonBee doesn't bother with any fancy naming schemes. The name is simply a
     * string. In contrast to Vert.x however, we have some very basic naming rules for the data verticle name:
     * <ul>
     * <li>The first letter of the verticle name must be any upper case latin letter [A-Z]</li>
     * <li>The only character disallowed in the verticle name is a forward slash /</li>
     * <li>In case the verticle shouldn't be exposed via any web interface (so only be available via the event bus), the
     * first letter can be set to an underscore character _</li>
     * </ul>
     * In other words the name is an unlimited-length sequence of characters (except the forward slash) and digits, the
     * first of which must be a uppercase latin letter or an underscore. The name will be for instance used in
     * addressing this verticle via the event bus (which is why the name must be unique in one NeonBee instance or
     * cluster), but also as part of a URL path scheme (which is why it must comply to the naming rules stated above).
     * The name must not contain any forward slashes as it might be prefixed with a namespace given by the
     * {@link NeonBeeDeployable} annotation, or by the `NeonBeeDeployables` manifest attribute. In case your verticle
     * name does not apply to the naming scheme above the communication via the event bus will likely still work, the
     * web-interfaces however can start to behave unpredictably.
     *
     * @return the name as string.
     */
    public abstract String getName();

    /**
     * The namespace of a verticle will be determined by a given {@link NeonBeeDeployable} annotation. If not present,
     * or empty no namespace will be prefixed to the verticle name. In case a namespace is provided, NeonBee will prefix
     * the namespace with a forward slash to the verticle name, e.g. namespace/VerticleName. It is required to also use
     * the forward slash as a sub-namespace separator if needed. The namespace is always treated lower case!
     *
     * @return the lowercase namespace of this data verticle or null in case no namespace is set
     */
    public final String getNamespace() {
        return namespaceSupplier.get();
    }

    /**
     * A custom message codec to be used to transmit the response of {@link #requestData(DataRequest, DataContext)} via
     * the event-bus. The codec is registered by the name given, at time of deployment of the verticle.
     *
     * @return a custom {@link MessageCodec} or null, in case no codec is needed to transmit the data type of this
     *         verticle
     */
    public MessageCodec<T, T> getMessageCodec() {
        return null;
    }

    @Override
    public void init(Vertx vertx, Context context) {
        super.init(vertx, context);
        JsonObject metrics = getMetricsConfig(NeonBee.get(vertx).getConfig().getMetricsConfig());
        this.dataVerticleMetrics = ConfiguredDataVerticleMetrics.configureMetricsReporting(NeonBee.get(vertx), metrics);

        // if present, register the custom codec. IMPORTANT: do NOT register the codec in the start method, as the
        // codec will need to be available on all instances, even if no instance of the verticle is started later on
        MessageCodec<T, T> codec = getMessageCodec();
        if (codec != null && codec.name() != null) {
            try {
                vertx.eventBus().registerCodec(codec);
            } catch (IllegalStateException e) {
                if (LOGGER.isDebugEnabled() && e.getMessage().startsWith("Already a codec registered with name")) {
                    LOGGER.debug("Codec {} is already registered. Ignore the exception.", codec.name());
                }
            }
        }
    }

    /**
     * Get the metric configuration.
     *
     * @param globalMetricsConfig the global {@link MetricsConfig} object.
     * @return the metric configuration as JSON object.
     */
    @VisibleForTesting
    JsonObject getMetricsConfig(MetricsConfig globalMetricsConfig) {
        MetricsConfig metricsConfig =
                globalMetricsConfig == null ? new MetricsConfig().setEnabled(false) : globalMetricsConfig;
        JsonObject verticleMetricsConfig =
                config() == null ? new JsonObject() : config().getJsonObject(CONFIG_METRICS_KEY, new JsonObject());

        Boolean enabled = verticleMetricsConfig.getBoolean(ConfiguredDataVerticleMetrics.ENABLED);
        // if there is no setting for the metric enabled in the DataVerticle configuration use the global setting.
        if (enabled == null || Boolean.TRUE.equals(enabled)) {
            verticleMetricsConfig.put(ConfiguredDataVerticleMetrics.ENABLED, metricsConfig.isEnabled());
        }
        return verticleMetricsConfig;
    }

    /**
     * Will start this data verticle and registers itself to the message bus for data query requests.
     */
    @Override
    public void start(Promise<Void> promise) {
        Promise<Void> registerDataVerticlePromise = Promise.promise();

        String address = getAddress();
        /*
         * Event bus inbound message handling.
         */
        vertx.eventBus().<DataQuery>consumer(address, message -> {
            ResolutionRoutine routine;
            MultiMap headers = message.headers();
            try {
                routine = message.body().getAction() == READ
                        ? resolutionRoutineForStrategy(Optional.ofNullable(headers.get(RESOLUTION_STRATEGY_HEADER))
                                .map(ResolutionStrategy::valueOf).orElse(RECURSIVE))
                        : new ManipulationRoutine();
            } catch (IllegalArgumentException e) {
                message.fail(FAILURE_CODE_UNKNOWN_STRATEGY, "Unknown data resolution strategy");
                return;
            }

            DataContext context = decodeContextFromString(headers.get(CONTEXT_HEADER));
            if (context instanceof DataContextImpl) {
                // the sender of the message can't know the deployment ID of the receiving verticle, so add it here!
                ((DataContextImpl) context).amendTopVerticleCoordinate(deploymentID());
            }
            if (LOGGER.isDebugEnabled()) {
                LOGGER.correlateWith(context).debug(
                        "Data verticle {} received event bus message from {}, using resolution routine {}",
                        getQualifiedName(), message.replyAddress(), routine.getClass().getSimpleName());
            }

            try {
                routine.execute(message.body(), context).onComplete(asyncResult -> {
                    try {
                        if (asyncResult.succeeded()) {
                            message.reply(asyncResult.result(), deliveryOptions(vertx, getMessageCodec(), context));

                        } else {
                            Throwable cause = asyncResult.cause();
                            if (LOGGER.isWarnEnabled()) {
                                LOGGER.correlateWith(context).warn("Data verticle {} routine execution failed",
                                        getQualifiedName(), cause instanceof DataException ? cause.toString() : EMPTY,
                                        cause);
                            }

                            if (cause instanceof DataException) {
                                message.reply(cause);
                            } else {
                                message.fail(FAILURE_CODE_PROCESSING_FAILED,
                                        "Processing of message failed. " + cause.getMessage());
                            }
                        }
                    } catch (Exception e) {
                        LOGGER.correlateWith(context).error("Processing of message failed", e);
                        message.fail(FAILURE_CODE_PROCESSING_FAILED, e.getMessage());
                    }
                });
            } catch (IllegalArgumentException e) {
                LOGGER.correlateWith(context).error("Missing message codec", e);
                message.fail(FAILURE_CODE_MISSING_MESSAGE_CODEC, e.getMessage());
            } catch (DataException e) {
                // the routine can either fail the future, or throw the DataException, if so propagate the failure
                LOGGER.correlateWith(context).error("Processing of message failed", e);
                message.fail(e.failureCode(), e.getMessage());
            }
        }).completionHandler(registerDataVerticlePromise);

        registerDataVerticlePromise.future().compose(v -> {
            try {
                start();
                NeonBee.get(vertx).registerLocalConsumer(address);
                return succeededFuture((Void) null);
            } catch (Exception e) {
                return failedFuture(e);
            }
        }).onComplete(promise);
    }

    @Override
    public void stop(Promise<Void> stopPromise) throws Exception {
        NeonBee neonBee = NeonBee.get(vertx);
        if (neonBee != null) { // NeonBee can be null, when the close hook has removed NeonBee - Vert.x mapping before
            neonBee.unregisterLocalConsumer(getAddress());
        }
        Future.<Void>future(stopPromiseFromSuper -> {
            try {
                super.stop(stopPromiseFromSuper);
            } catch (Exception e) {
                stopPromiseFromSuper.fail(e);
            }
        }).onComplete(stopPromise);
    }

    /**
     * In case the data processing of the DataSource requires data, you can pass back as many data requests as needed.
     * The data will be made available in the {@link #retrieveData(DataQuery, DataMap, DataContext)} method via the
     * <code>require</code> parameter.
     *
     * @param query   The query describing the data requested
     * @param context A {@link DataContext} object
     * @return a future to a collection of data requests to do, before the invocation of
     *         {@link #retrieveData(DataQuery, DataMap, DataContext)}
     */
    @SuppressWarnings("PMD.UnusedFormalParameter")
    public Future<Collection<DataRequest>> requireData(DataQuery query, DataContext context) {
        return succeededFuture(emptyList());
    }

    /**
     * Retrieve the requested data in an asynchronous manner and returns a future to the data expected.
     *
     * @param query   The query describing the data requested
     * @param require A map of the results required via {@link #requireData(DataQuery, DataContext)}
     * @param context A context object passed through the whole data retrieving life cycle
     * @return A future to the data requested
     */
    @SuppressWarnings("PMD.UnusedFormalParameter")
    public Future<T> retrieveData(DataQuery query, DataMap require, DataContext context) {
        // this call will return a failed future w/ a UnsupportedOperationException in case it is not implemented, this
        // safeguards that the user is free to implement either retrieveData(DataQuery, DataContext) in case no required
        // data is needed, or retrieveData(DataQuery, DataMap, DataContext) otherwise.
        return retrieveData(query, context);
    }

    private <U> void reportRequestDataMetrics(DataRequest request, Future<U> future) {
        List<Tag> tags;
        if (request.getQuery() == null || request.getQuery().getRawQuery().isEmpty()) {
            tags = List.of();
        } else {
            tags = List.of(new ImmutableTag("query", request.getQuery().getRawQuery()));
        }
        String qualifiedName = request.getQualifiedName();

        dataVerticleMetrics.reportTimingMetric("request.data.timer." + qualifiedName, "time to retrieve the data", tags,
                future);
        dataVerticleMetrics.reportStatusCounter("request.data.counter." + qualifiedName, SUCCEEDED_RESPONSE_COUNT, tags,
                future);
        dataVerticleMetrics.reportActiveRequestsGauge("request.data.active.requests." + qualifiedName,
                "Number of requests waiting for a response", List.of(), future);
        dataVerticleMetrics.reportNumberOfRequests("request.counter." + qualifiedName, "Number of requests sent", tags);
    }

    /**
     * Returns the qualified name (namespace if existing and name) of this verticle separated by a forward slash as a
     * namespace separator.
     *
     * @return The full qualified name
     */
    @VisibleForTesting
    public final String getQualifiedName() {
        String name = getName();
        String namespace = getNamespace();
        return namespace != null ? createQualifiedName(namespace, name) : name;
    }

    /**
     * Get an instance of a resolution routine for a certain strategy.
     *
     * @param strategy the strategy to obtain the resolution routine for
     * @return the resolution routine
     */
    private ResolutionRoutine resolutionRoutineForStrategy(ResolutionStrategy strategy) {
        // case RECURSIVE:
        if (strategy == ResolutionStrategy.OPTIMIZED) {
            return new OptimizedResolutionRoutine();
        }
        return new RecursiveResolutionRoutine();
    }

    /**
     * Interface for all resolution routines (actual implementations of resolution strategies).
     *
     * A resolution routine defines how required data is resolved and then requested from the individual data verticle
     */
    private interface ResolutionRoutine {
        /**
         * Tries to resolve a given data query and returns a future to data.
         *
         * @param query The query to resolve
         * @return A future to the data returned by the query
         */
        Future<?> execute(DataQuery query, DataContext context);
    }

    private class RecursiveResolutionRoutine implements ResolutionRoutine {
        @Override
        public Future<T> execute(DataQuery query, DataContext context) {
            // initialize the results map as a LinkedHashMap, this will safeguard that iterating it will return the same
            // order, as the collection returned via requireData. This also favours the previous implementation of
            // requireData(), where any index of the requireData array corresponded with the indexes of the data array
            Map<DataRequest, AsyncResult<?>> requestResults = new LinkedHashMap<>();
            Map<DataRequest, DataContext> receivedDataContextMap = new LinkedHashMap<>();
            return requireData(query, context).compose(requests -> {
                // ignore the result of the require data composite future (otherwiseEmpty), the retrieve data method
                // should decide if it needs to handle success or failure of any of the individual asynchronous results
                return CompositeFuture.join(
                        Optional.ofNullable(requests).map(Collection::stream).orElse(Stream.empty()).map(request -> {
                            // use one copy of DataContext for each request to avoid data clash
                            DataContext requestContext = context.copy();
                            receivedDataContextMap.put(request, requestContext);
                            return requestResults.computeIfAbsent(request, mapRequest -> {
                                Future<Object> future = requestData(vertx, request, requestContext);
                                reportRequestDataMetrics(request, future);
                                return future;
                            });
                        }).map(Future.class::cast).collect(Collectors.toList())).otherwiseEmpty();
            }).compose(requiredCompositeOrNothing -> {
                List<Tag> tags = retrieveDataTags();
                try {
                    Map<DataRequest, Map<String, Object>> receivedData = receivedDataContextMap.entrySet().stream()
                            .map(entry -> Map.entry(entry.getKey(), entry.getValue().responseData()))
                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                    context.setReceivedData(receivedData);
                    Future<T> future = retrieveData(query, new DataMap(requestResults), context);
                    reportRetrieveDataMetrics(tags, future);
                    return future;
                } catch (Exception e) {
                    dataVerticleMetrics.reportStatusCounter("retrieve.data.counter." + getAddress(),
                            SUCCEEDED_RESPONSE_COUNT, tags, failedFuture(e));
                    // handle any (runtime) exception here and fail the result future
                    return failedFuture(e);
                }
            });
        }

        /**
         * @return tags for the retrieve data metrics.
         */
        private List<Tag> retrieveDataTags() {
            List<Tag> tags = new ArrayList<>(2);
            String name = getName();
            if (name != null) {
                tags.add(new ImmutableTag("name", name));
            }
            String namespace = getNamespace();
            if (namespace != null) {
                tags.add(new ImmutableTag("namespace", namespace));
            }
            return tags;
        }

        private void reportRetrieveDataMetrics(List<Tag> tags, Future<T> future) {
            String address = getAddress();
            dataVerticleMetrics.reportTimingMetric("retrieve.data.timer." + address, "Time to retrieve data", tags,
                    future);
            dataVerticleMetrics.reportStatusCounter("retrieve.data.counter." + address, SUCCEEDED_RESPONSE_COUNT, tags,
                    future);
            dataVerticleMetrics.reportActiveRequestsGauge("retrieve.data.active.requests." + address,
                    "Number of requests waiting for a response", tags, future);
            dataVerticleMetrics.reportNumberOfRequests("retrieve.counter." + address, "Number of requests sent", tags);
        }
    }

    private class OptimizedResolutionRoutine implements ResolutionRoutine {
        @Override
        public Future<T> execute(DataQuery query, DataContext context) {
            return failedFuture(new UnsupportedOperationException("Optimized resolution strategy not available."));
        }
    }

    private class ManipulationRoutine implements ResolutionRoutine {
        @Override
        public Future<T> execute(DataQuery query, DataContext context) {
            try {
                return manipulateData(query, context);
            } catch (Exception e) {
                // handle any (runtime) exception here and fail the result future
                return failedFuture(e);
            }
        }
    }
}
