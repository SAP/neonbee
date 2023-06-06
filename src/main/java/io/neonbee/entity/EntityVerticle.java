package io.neonbee.entity;

import static io.neonbee.entity.EntityModelManager.EVENT_BUS_MODELS_LOADED_ADDRESS;
import static io.neonbee.entity.EntityModelManager.getBufferedOData;
import static io.neonbee.internal.helper.AsyncHelper.allComposite;
import static io.neonbee.internal.helper.StringHelper.EMPTY;
import static io.neonbee.internal.verticle.ConsolidationVerticle.ENTITY_TYPE_NAME_HEADER;
import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.olingo.commons.api.edm.FullQualifiedName;
import org.apache.olingo.server.api.uri.UriInfo;
import org.apache.olingo.server.core.uri.parser.Parser;

import com.google.common.annotations.VisibleForTesting;

import io.neonbee.NeonBee;
import io.neonbee.data.DataContext;
import io.neonbee.data.DataQuery;
import io.neonbee.data.DataRequest;
import io.neonbee.data.DataVerticle;
import io.neonbee.internal.helper.AsyncHelper;
import io.neonbee.internal.registry.Registry;
import io.neonbee.internal.verticle.ConsolidationVerticle;
import io.neonbee.logging.LoggingFacade;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;

public abstract class EntityVerticle extends DataVerticle<EntityWrapper> {

    /**
     * Name for the {@link NeonBee#getEntityRegistry()}.
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

    /**
     * Convenience method for calling the {@link #requestEntity(DataRequest, DataContext)} method.
     *
     * @param request The DataRequest specifying the data to request from another EntityVerticle
     * @param context The {@link DataContext data context} which keeps track of all the request-level information during
     *                the lifecycle of requesting data
     * @return a future to the data requested
     * @see #requestEntity(DataRequest, DataContext)
     */
    public Future<EntityWrapper> requestEntity(DataRequest request, DataContext context) {
        return requestEntity(getVertx(), request, context);
    }

    /**
     * Requesting data from other EntityVerticles.
     *
     * @param vertx   The Vert.x instance
     * @param request The DataRequest specifying the data to request from another EntityVerticle
     * @param context The {@link DataContext data context} which keeps track of the request-level data during a request
     * @return a future to the data requested
     */
    public static Future<EntityWrapper> requestEntity(Vertx vertx, DataRequest request, DataContext context) {
        FullQualifiedName entityTypeName = request.getEntityTypeName();
        if (entityTypeName == null) {
            throw new IllegalArgumentException(
                    "A entity request must specify an entity type name to request data from");
        }

        /*
         * TODO, as soon as having multiple verticle for an entity is not longer a corner case, I would recommend that
         * we send the "qualifiedNames" in a header to the ConsolidationVerticle. Then it wouldn't be necessary to do
         * the getVerticlesForEntityType call twice.
         */
        return getVerticlesForEntityType(vertx, entityTypeName).compose(qualifiedNames -> {
            if (qualifiedNames.isEmpty()) {
                return failedFuture("No verticle registered listening to entity type name "
                        + entityTypeName.getFullQualifiedNameAsString());
            } else if (qualifiedNames.size() == 1) {
                return requestData(vertx, new DataRequest(qualifiedNames.get(0), request.getQuery()), context);
            } else {
                DataQuery query = request.getQuery().copy().setHeader(ENTITY_TYPE_NAME_HEADER,
                        entityTypeName.getFullQualifiedNameAsString());
                return requestData(vertx,
                        new DataRequest(ConsolidationVerticle.QUALIFIED_NAME, query).setLocalOnly(true), context);
            }
        }).compose(entity -> entity instanceof EntityWrapper ? succeededFuture((EntityWrapper) entity)
                : failedFuture("The result of entity verticle must be an EntityWrapper"));
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
    protected static Future<UriInfo> parseUriInfo(Vertx vertx, DataQuery query) {
        return parseUriInfo(NeonBee.get(vertx), query);
    }

    /**
     * Parses a given DataQuery to a OData UriInfo object.
     *
     * @param neonBee the NeonBee instance to be used
     * @param query   the DataQuery to convert
     * @return a future to an UriInfo for a given DataQuery
     */
    protected static Future<UriInfo> parseUriInfo(NeonBee neonBee, DataQuery query) {
        // the uriPath with trimmed leading forward slash e.g. <schemaNamespace>/<entitySet> where <schemaNamespace> is
        // <namespace>.<service> or <service> (if no namespace was used in the CDS model file)
        Matcher uriMatcher = URI_PATH_PATTERN.matcher(query.getUriPath());
        if (!uriMatcher.find()) {
            return failedFuture("Failed to match the URI path " + query.getUriPath()
                    + " for an OData URI, the path must at least contain one forward slash to separate the service by the entity set name");
        }

        String serviceName = uriMatcher.group(SERVICE_NAMESPACE_GROUP);
        return neonBee.getModelManager().getSharedModel(EntityModelDefinition.retrieveNamespace(serviceName))
                .compose(entityModel -> AsyncHelper.executeBlocking(neonBee.getVertx(), () -> {
                    return new Parser(entityModel.getEdmxMetadata(serviceName).getEdm(), getBufferedOData())
                            .parseUri(uriMatcher.group(ENTITY_PATH_GROUP), query.getRawQuery(), EMPTY, EMPTY);
                }));
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
        return getRegistry(vertx).get(sharedEntityMapName(entityTypeName));
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
        return String.format("_%s-%d", getClass().getSimpleName(), getClass().getName().hashCode());
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

        announceEntityVerticle(vertx).compose(nothing -> Future.<Void>future(super::start)).onComplete(promise);
    }

    /**
     * Announces that this EntityVerticle is handling certain {@link #entityTypeNames()} to the rest of the cluster by
     * adding the EntityTypes to a shared map in a secure and cluster-wide thread safe manner.
     */
    private Future<Void> announceEntityVerticle(Vertx vertx) {
        return announceOrConcealEntities(entityType -> getRegistry(vertx).register(entityType, getQualifiedName()));
    }

    /**
     * Conceals that this EntityVerticle is handling certain {@link #entityTypeNames()} to the rest of the cluster by
     * removing the EntityTypes from a shared map in a secure and cluster-wide thread safe manner.
     */
    private Future<Void> concealEntityVerticle() {
        return announceOrConcealEntities(entityType -> getRegistry(vertx).unregister(entityType, getQualifiedName()));
    }

    @Override
    public void stop(Promise<Void> stopPromise) throws Exception {
        concealEntityVerticle().compose(v -> Future.<Void>future(stopPromiseFromSuper -> {
            try {
                super.stop(stopPromiseFromSuper);
            } catch (Exception e) {
                stopPromiseFromSuper.fail(e);
            }
        })).onComplete(stopPromise);
    }

    private Future<Void> announceOrConcealEntities(Function<String, Future<Void>> announceOrConceal) {
        return entityTypeNames()
                .map(entityTypeNames -> entityTypeNames != null ? entityTypeNames : Set.<FullQualifiedName>of())
                .compose(entityTypeNames -> {
                    List<Future<Void>> announceFutures =
                            entityTypeNames.stream().map(EntityVerticle::sharedEntityMapName)
                                    .map(name -> announceOrConceal.apply(name)).collect(toList());
                    return allComposite(announceFutures).mapEmpty();
                });
    }

    private static Registry<String> getRegistry(Vertx vertx) {
        return NeonBee.get(vertx).getEntityRegistry();
    }
}
