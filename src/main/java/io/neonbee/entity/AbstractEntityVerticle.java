package io.neonbee.entity;

import static io.neonbee.data.DataException.FAILURE_CODE_PROCESSING_FAILED;
import static io.neonbee.data.internal.DataContextImpl.decodeContextFromString;
import static io.neonbee.data.internal.DataContextImpl.encodeContextToString;
import static io.neonbee.entity.EntityModelManager.EVENT_BUS_MODELS_LOADED_ADDRESS;
import static io.neonbee.entity.EntityModelManager.getBufferedOData;
import static io.neonbee.internal.helper.StringHelper.EMPTY;
import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.olingo.commons.api.edm.FullQualifiedName;
import org.apache.olingo.server.api.uri.UriInfo;
import org.apache.olingo.server.core.uri.parser.Parser;

import com.google.common.annotations.VisibleForTesting;

import io.neonbee.NeonBee;
import io.neonbee.data.DataContext;
import io.neonbee.data.DataException;
import io.neonbee.data.DataQuery;
import io.neonbee.data.DataVerticle;
import io.neonbee.data.internal.DataContextImpl;
import io.neonbee.internal.Registry;
import io.neonbee.internal.WriteSafeRegistry;
import io.neonbee.logging.LoggingFacade;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonArray;

/**
 * This verticle is an intermediary layer between {@link DataVerticle} and {@link EntityVerticle}, which supports
 * EntityVerticle's model registration, but allows other payload types such as Buffer than an {@link EntityWrapper}
 *
 * This layer is introduced to support odataproxy endpoint, where the verticle instead of Neonbee is doing the OData
 * handling.
 *
 * @param <T> payload type
 */
public abstract class AbstractEntityVerticle<T> extends DataVerticle<T> {

    /**
     * Name for the {@link WriteSafeRegistry}.
     */
    public static final String REGISTRY_NAME = "EntityVerticleRegistry";

    @VisibleForTesting
    static final String SHARED_ENTITY_MAP_NAME = "entityVerticles[%s]";

    @VisibleForTesting
    static final int SERVICE_NAMESPACE_GROUP = 1;

    @VisibleForTesting
    static final int CDS_NAMESPACE_GROUP = 2;

    @VisibleForTesting
    static final int CDS_SERVICE_NAME_GROUP = 3;

    @VisibleForTesting
    static final int ENTITY_PATH_GROUP = 4;

    @VisibleForTesting
    static final int ENTITY_SET_NAME_GROUP = 5;

    @VisibleForTesting
    static final int ENTITY_PROPERTY_NAME_GROUP = 6;

    /**
     * Pattern to match OData URI paths.
     * <p>
     * Matches OData URI paths, applying the following rules:
     * <ul>
     * <li>the last forward slash (/) splits the <i>serviceNamespace</i> from the <i>entitySetName</i>
     * <li>the last dot in <i>serviceNamespace</i> splits the <i>cdsNamespace</i> and the <i>cdsServiceName</i>
     * <li>Any leading forward slashes (/) will be ignored
     * <li>The entity name ends at the first character, which is not a valid SimpleIdentifier (non-normatively speaking
     * it has to start with a letter or underscore, followed by at most 127 letters, underscores or digits), e.g. it may
     * be delimited by the next opening bracket ((), in case a key was specified, or the next forward slashes (/) in
     * case e.g. of a /$count operation
     * </ul>
     * Some examples:
     *
     * <pre>
     * URI path                          | Group 1 (Svc. Nsp.) | 2. CDS Nsp. | 3. Name | 4. Enty. Path | 5. ESN | 6. Property
     * ----------------------------------------------------------------------------------------------------------------------
     * my.very/own.Service/Entity('Key') | my.very/own.Service | my.very/own | Service | Entity('Key') | Entity |
     * my.Service/Entity('Key')          | my.Service          | my          | Service | Entity('Key') | Entity |
     * Service/Entity('Key')/property    | Service             |             | Service | Entity('Key') | Entity | property
     * Service/Entity('Key')             | Service             |             | Service | Entity('Key') | Entity |
     * </pre>
     */
    @VisibleForTesting
    static final Pattern URI_PATH_PATTERN =
            Pattern.compile("^/*((?:(.*)\\.)?(.*?))/(([A-Za-z_]\\w+).*?)(?:(?<=\\))/(.*))?$");

    private static final LoggingFacade LOGGER = LoggingFacade.create();

    @SuppressWarnings("PMD.NullAssignment")
    private List<MessageConsumer<DataQuery>> proxyConsumers = List.of();

    private List<String> proxyAddresses = List.of();

    /**
     * Create a new {@link DataVerticle}.
     */
    protected AbstractEntityVerticle() {
        super();
    }

    /**
     * Parses a given DataQuery to a OData UriInfo object.
     *
     * @param query the DataQuery to convert
     * @return a future to an UriInfo for a given DataQuery
     */
    protected Future<UriInfo> parseUriInfo(DataQuery query) {
        return parseUriInfo(vertx, query);
    }

    /**
     * Parses a given DataQuery to a OData UriInfo object.
     *
     * @param vertx the Vertx instance to be used
     * @param query the DataQuery to convert
     * @return a future to an UriInfo for a given DataQuery
     * @see #parseUriInfo(NeonBee, DataQuery)
     */
    protected Future<UriInfo> parseUriInfo(Vertx vertx, DataQuery query) {
        return parseUriInfo(NeonBee.get(vertx), query);
    }

    /**
     * Parses a given DataQuery to a OData UriInfo object.
     *
     * @param neonBee the NeonBee instance to be used
     * @param query   the DataQuery to convert
     * @return a future to an UriInfo for a given DataQuery
     */
    protected Future<UriInfo> parseUriInfo(NeonBee neonBee, DataQuery query) {
        // the uriPath with trimmed leading forward slash e.g. <schemaNamespace>/<entitySet> where <schemaNamespace> is
        // <namespace>.<service> or <service> (if no namespace was used in the CDS model file)
        Matcher uriMatcher = URI_PATH_PATTERN.matcher(query.getUriPath());
        if (!uriMatcher.find()) {
            return failedFuture("Failed to match the URI path " + query.getUriPath()
                    + " for an OData URI, the path must at least contain one forward slash to separate the service by the entity set name");
        }

        String serviceName = uriMatcher.group(SERVICE_NAMESPACE_GROUP);
        return neonBee.getModelManager().getSharedModel(EntityModelDefinition.retrieveNamespace(serviceName))
                .compose(entityModel -> neonBee.getVertx().executeBlocking(
                        () -> new Parser(entityModel.getEdmxMetadata(serviceName).getEdm(), getBufferedOData())
                                .parseUri(uriMatcher.group(ENTITY_PATH_GROUP), query.getRawQuery(), EMPTY, EMPTY)));
    }

    /**
     * Get the (entity) verticle names registered for a certain entityTypeName.
     * <p>
     * In case one verticle has been registered multiple times, this method will reduce the result down to a set.
     *
     * @param vertx          The Vert.x instance
     * @param entityTypeName The entityTypeName to query
     * @return A list of all (entity) verticle names as qualified names
     */
    public static Future<List<String>> getVerticlesForEntityType(Vertx vertx, FullQualifiedName entityTypeName) {
        return Future
                .future(asyncGet -> getRegistry(vertx).get(sharedEntityMapName(entityTypeName))
                        .onSuccess(asyncGet::complete).onFailure(asyncGet::fail))
                .map(qualifiedNames -> ((List<?>) Optional.ofNullable((JsonArray) qualifiedNames)
                        .orElseGet(JsonArray::new).getList()).stream().map(Object::toString).distinct()
                                .toList());
    }

    /**
     * This method is constructing the key for the shared map that contains the information, which EntityVerticles
     * offering which entity types.
     *
     * @param entityTypeName The entity type name of the entity to announce
     * @return A key for the shared map which is based on the passed entity type name
     */
    static String sharedEntityMapName(FullQualifiedName entityTypeName) {
        return String.format(SHARED_ENTITY_MAP_NAME, entityTypeName.getFullQualifiedNameAsString());
    }

    @Override
    public final String getName() {
        // Entity verticle are generally not exposed via any web interface, but only via the event bus. Also, they are
        // generally never accessed directly, but only via the shared entity name map, so return a generated name here.
        // The name must be unique in the Vert.x instance / cluster and the same for every entity verticle of this type.
        return getName(getClass());
    }

    /**
     * Returns a unique name for a given EntityVerticle class.
     *
     * @param clazz the EntityVerticle class
     * @return a unique name for a given EntityVerticle class
     */
    public static String getName(Class<? extends AbstractEntityVerticle> clazz) {
        return String.format("_%s-%d", clazz.getSimpleName(), clazz.getName().hashCode());
    }

    /**
     * Returns a set of all names of entity types, this EntityVerticle is able to deal with.
     *
     * @return a set of FullQualifiedNames of entity types this EntityVerticle handles
     */
    public abstract Future<Set<FullQualifiedName>> entityTypeNames();

    /**
     * Will start this entity verticle and registers itself to the message bus for entity query requests.
     */
    @Override
    public void start(Promise<Void> promise) {
        vertx.eventBus().consumer(EVENT_BUS_MODELS_LOADED_ADDRESS, message -> {
            announceEntityVerticle(vertx).onFailure(throwable -> {
                if (LOGGER.isErrorEnabled()) {
                    LOGGER.error("Updating announcements of entity verticle {} failed", getQualifiedName(), throwable);
                }
            });
        });
        announceEntityVerticle(vertx).compose(nothing -> Future.<Void>future(super::start))
                .onSuccess(nothing -> {
                    if (LOGGER.isInfoEnabled()) {
                        LOGGER.info("Entity verticle {} is listening on event bus address {}",
                                getQualifiedName(), getAddress());
                    }
                }).compose(nothing -> registerProxyConsumerIfNecessary()).onComplete(promise);
    }

    /**
     * Announces that this EntityVerticle is handling certain {@link #entityTypeNames()} to the rest of the cluster by
     * adding the EntityTypes to a shared map in a secure and cluster-wide thread safe manner.
     */
    private Future<Void> announceEntityVerticle(Vertx vertx) {
        if (!supportsODataRequests()) {
            return succeededFuture();
        }

        // in case this entity verticle does not listen to any entityTypeNames, do not add it to the shared map
        return entityTypeNames()
                .map(entityTypeNames -> entityTypeNames != null ? entityTypeNames : Set.<FullQualifiedName>of())
                .compose(entityTypeNames -> {
                    List<Future<Void>> announceFutures =
                            entityTypeNames.stream().map(AbstractEntityVerticle::sharedEntityMapName).map(name -> {
                                String qualifiedName = getQualifiedName();
                                return getRegistry(vertx).register(name, qualifiedName);
                            }).toList();
                    return Future.all(announceFutures).mapEmpty();
                });
    }

    private static Registry<String> getRegistry(Vertx vertx) {
        return NeonBee.get(vertx).getEntityRegistry();
    }

    private Future<Void> registerProxyConsumerIfNecessary() {
        if (!supportsProxyRequests()) {
            proxyConsumers = List.of();
            proxyAddresses = List.of();
            return succeededFuture();
        }

        return proxyQualifiedNames().compose(aliasQualifiedNames -> {
            LinkedHashSet<String> qualifiedNames = new LinkedHashSet<>();
            qualifiedNames.add(getQualifiedName());
            qualifiedNames.addAll(aliasQualifiedNames);

            List<MessageConsumer<DataQuery>> consumers = new ArrayList<>();
            List<String> addresses = new ArrayList<>();
            List<Future<Void>> registrations = new ArrayList<>();

            for (String qualifiedName : qualifiedNames) {
                String address = getProxyAddress(qualifiedName);
                Promise<Void> registration = Promise.promise();
                MessageConsumer<DataQuery> consumer = registerProxyConsumer(address, registration);
                consumers.add(consumer);
                addresses.add(address);
                registrations.add(registration.future());
            }

            return Future.all(registrations)
                    .onSuccess(v -> {
                        proxyConsumers = List.copyOf(consumers);
                        proxyAddresses = List.copyOf(addresses);

                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug("Entity verticle {} registered proxy addresses {}", getQualifiedName(),
                                    addresses);
                        }
                    })
                    .onFailure(reason -> consumers.forEach(MessageConsumer::unregister)).mapEmpty();
        });
    }

    private MessageConsumer<DataQuery> registerProxyConsumer(String address, Promise<Void> registration) {
        MessageConsumer<DataQuery> consumer = vertx.eventBus().consumer(address, message -> {
            DataContext decodedContext = decodeContextFromString(message.headers().get(DataVerticle.CONTEXT_HEADER));
            DataContext context = decodedContext != null ? decodedContext : new DataContextImpl();

            if (context instanceof DataContextImpl contextImpl) {
                contextImpl.pushVerticleToPath(getQualifiedName());
                contextImpl.amendTopVerticleCoordinate(deploymentID());
            }

            retrieveProxyData(message.body(), context).onComplete(asyncResult -> {
                if (context instanceof DataContextImpl contextImpl) {
                    contextImpl.popVerticleFromPath();
                }

                if (asyncResult.succeeded()) {
                    DeliveryOptions replyOptions = new DeliveryOptions()
                            .addHeader(DataVerticle.CONTEXT_HEADER, encodeContextToString(context));
                    message.reply(asyncResult.result(), replyOptions);
                } else {
                    Throwable cause = asyncResult.cause();
                    if (LOGGER.isWarnEnabled()) {
                        LOGGER.correlateWith(context).warn("Proxy request of {} failed", getQualifiedName(), cause);
                    }

                    if (cause instanceof DataException dataException) {
                        message.reply(dataException);
                    } else {
                        message.fail(FAILURE_CODE_PROCESSING_FAILED, cause.getMessage());
                    }
                }
            });
        });

        consumer.completionHandler(asyncCompletion -> {
            if (asyncCompletion.succeeded()) {
                NeonBee neonBee = NeonBee.get(vertx);
                if (neonBee != null) {
                    neonBee.registerLocalConsumer(address);
                }
                registration.complete();
            } else {
                registration.fail(asyncCompletion.cause());
            }
        });

        return consumer;
    }

    private Future<Set<String>> proxyQualifiedNames() {
        return entityTypeNames().otherwise(Set.of()).map(typeNames -> {
            if (typeNames == null || typeNames.isEmpty()) {
                return Set.<String>of();
            }

            LinkedHashSet<String> aliases = new LinkedHashSet<>();
            for (FullQualifiedName typeName : typeNames) {
                if (typeName == null || typeName.getName() == null || typeName.getName().isBlank()) {
                    continue;
                }

                aliases.addAll(expandProxyQualifiedNames(typeName));
            }

            return Collections.unmodifiableSet(aliases);
        }).recover(throwable -> {
            if (LOGGER.isWarnEnabled()) {
                LOGGER.warn("Failed to resolve proxy aliases for {}", getQualifiedName(), throwable);
            }
            return succeededFuture(Set.of());
        });
    }

    private static Set<String> expandProxyQualifiedNames(FullQualifiedName entityTypeName) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        String entityName = entityTypeName.getName();
        String namespace = entityTypeName.getNamespace();

        if (namespace == null || namespace.isBlank()) {
            names.add(entityName);
        } else {
            names.add(DataVerticle.createQualifiedName(namespace, entityName));

            if (namespace.contains(".")) {
                names.add(DataVerticle.createQualifiedName(namespace.replace('.', '/'), entityName));
            }
        }

        return names;
    }

    /**
     * Signals whether this entity verticle exposes the proxy event-bus endpoint.
     *
     * @return {@code true} if proxy requests should be handled, otherwise {@code false}
     */
    protected boolean supportsProxyRequests() {
        return false;
    }

    /**
     * Signals whether this entity verticle exposes the regular OData handlers.
     *
     * @return {@code true} if OData requests should be handled, otherwise {@code false}
     */
    protected boolean supportsODataRequests() {
        return !supportsProxyRequests();
    }

    /**
     * Produces the serialized payload for a proxy request.
     *
     * @param query   the data query describing the requested resource
     * @param context the data context accompanying the request
     * @return a future that resolves to the buffer to be returned to the caller
     */
    protected Future<Buffer> retrieveProxyData(DataQuery query, DataContext context) {
        return failedFuture(new UnsupportedOperationException(
                String.format("Proxy requests are not supported by %s", getQualifiedName())));
    }

    /**
     * Computes the proxy event-bus address for the given qualified entity verticle name.
     *
     * @param qualifiedName the qualified entity verticle name
     * @return the proxy event-bus address used by the proxy endpoint
     */
    public static String getProxyAddress(String qualifiedName) {
        return String.format("%s[%s]", AbstractEntityVerticle.class.getSimpleName() + "Proxy", qualifiedName);
    }

    @Override
    @SuppressWarnings("PMD.NullAssignment")
    public void stop() throws Exception {
        if (!proxyConsumers.isEmpty()) {
            proxyConsumers.forEach(MessageConsumer::unregister);
            proxyConsumers = List.of();
        }

        NeonBee neonBee = NeonBee.get(vertx);
        if (neonBee != null) {
            proxyAddresses.forEach(neonBee::unregisterLocalConsumer);
        }

        proxyAddresses = List.of();

        super.stop();
    }
}
