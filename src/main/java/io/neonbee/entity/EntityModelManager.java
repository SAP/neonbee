package io.neonbee.entity;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.olingo.server.api.OData;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Functions;

import io.neonbee.NeonBee;
import io.neonbee.internal.SharedDataAccessor;
import io.neonbee.logging.LoggingFacade;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.impl.VertxImpl;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.core.shareddata.Lock;
import io.vertx.core.spi.cluster.ClusterManager;

/**
 * The {@link EntityModelManager} is used to manage shared model files across a given NeonBee instance.
 * <p>
 * It loads and manages a shared instance of any number of {@link EntityModel EntityModels}, that are loaded from the
 * class path / the NeonBee models folder and which might be extended by external {@link EntityModelDefinition
 * EntityModelDefinitions}.
 */
public class EntityModelManager {

    /**
     * Every time new models are loaded a message will be published to this event bus address.
     */
    public static final String EVENT_BUS_MODELS_LOADED_ADDRESS = EntityModelManager.class.getSimpleName() + "Loaded";

    private static final ThreadLocal<OData> THREAD_LOCAL_ODATA = ThreadLocal.withInitial(OData::newInstance);

    private static final DeliveryOptions LOCAL_DELIVERY = new DeliveryOptions().setLocalOnly(true);

    private static final LoggingFacade LOGGER = LoggingFacade.create();

    private static final String ENTITY_MODEL_DEFINITIONS = "ENTITY_MODEL_DEFINITIONS";

    /**
     * A map of all buffered NeonBee models.
     * <p>
     * Do not use a {@link ConcurrentHashMap} here, as the references to Vert.x need to stay weak for Vert.x to properly
     * garbage collected, at the end of its lifetime. This means the {@link #bufferedModels} map must never be iterated
     * over, in order to not cause any {@link ConcurrentModificationException}. The inner maps are unmodifiable in any
     * case.
     */
    @VisibleForTesting
    private final AtomicReference<Map<String, EntityModel>> bufferedModels = new AtomicReference<>();

    @VisibleForTesting
    final NeonBee neonBee;

    @VisibleForTesting
    final Set<EntityModelDefinition> externalModelDefinitions = ConcurrentHashMap.newKeySet();

    /**
     * Create a new instance of an {@link EntityModelManager} for a given {@link NeonBee} instance.
     * <p>
     * Note that during the boot of NeonBee a {@link EntityModelManager} is created and assigned to the {@link NeonBee}
     * instance permanently. It is not possible to create another {@link EntityModelManager} and an
     * {@link IllegalArgumentException} will be thrown. Use {@code neonBee.getModelManager()} instead.
     *
     * @param neonBee the NeonBee instance this {@link EntityModelManager} is associated to
     */
    public EntityModelManager(NeonBee neonBee) {
        if (neonBee.getModelManager() != null) {
            throw new IllegalArgumentException(
                    "The passed NeonBee instance already has a EntityModelManager. Use neonBee.getModelManager() instead of creating a new EntityModelManager");
        }

        this.neonBee = neonBee;
    }

    /**
     * Get a buffered instance to OData.
     *
     * @return a thread local buffered instance of OData
     */
    public static OData getBufferedOData() {
        return THREAD_LOCAL_ODATA.get();
    }

    /**
     * Synchronously returns the buffered models, which could be null, in case or {@link #reloadModels(Vertx)} was never
     * called or returned no valid metadata so far.
     * <p>
     * This is a convenience function, as for some instances (e.g. in EventBus Message Codec) the current metadata
     * definition needs to be received synchronously and without the involvement of futures.
     *
     * @param vertx the {@link Vertx} instance
     * @return The buffered models for all schema namespaces or null in case no models have been loaded so far
     * @see #getBufferedModels(NeonBee)
     * @deprecated use {@link #getBufferedModels(NeonBee)} instead
     */
    @Deprecated(forRemoval = true)
    public static Map<String, EntityModel> getBufferedModels(Vertx vertx) {
        return getBufferedModels(NeonBee.get(vertx));
    }

    /**
     * Synchronously returns the buffered models, which could be null, in case {@link #reloadModels(NeonBee)} was never
     * called or returned no valid metadata so far.
     * <p>
     * This is a convenience function, as for some instances (e.g. in EventBus Message Codec) the current metadata
     * definition needs to be received synchronously and without the involvement of futures.
     *
     * @param neonBee the {@link NeonBee} instance
     * @return The buffered models for all schema namespaces or null in case no models have been loaded so far
     * @see #getBufferedModels()
     * @deprecated use {@code neonBee.getModelManager().getBufferedModels()} instead
     */
    @Deprecated(forRemoval = true)
    public static Map<String, EntityModel> getBufferedModels(NeonBee neonBee) {
        return neonBee.getModelManager().getBufferedModels();
    }

    /**
     * Synchronously returns the buffered models, which could be null, in case {@link #reloadModels()} was never called
     * or returned no valid metadata so far.
     * <p>
     * This is a convenience function, as for some instances (e.g. in EventBus Message Codec) the current metadata
     * definition needs to be received synchronously and without the involvement of futures.
     *
     * @return The buffered models for all schema namespaces or null in case no models have been loaded so far
     */
    public Map<String, EntityModel> getBufferedModels() {
        return bufferedModels.get();
    }

    /**
     * Synchronously returns the buffered model for a specific schema namespace, which could be null, in case
     * {@link #reloadModels(Vertx)} was never called or returned no valid metadata so far.
     * <p>
     *
     * @param vertx           the {@link Vertx} instance
     * @param schemaNamespace the namespace of the service
     * @return the buffered model for a specific schema namespace or null in case no models have been loaded so far, or
     *         no model with the given schemaNamespace is found
     * @see #getBufferedModel(NeonBee, String)
     * @see #getBufferedModels(Vertx)
     * @deprecated use {@link #getBufferedModel(NeonBee, String)} instead
     */
    @Deprecated(forRemoval = true)
    public static EntityModel getBufferedModel(Vertx vertx, String schemaNamespace) {
        return getBufferedModel(NeonBee.get(vertx), schemaNamespace);
    }

    /**
     * Synchronously returns the buffered model for a specific schema namespace, which could be null, in case
     * {@link #reloadModels(NeonBee)} was never called or returned no valid metadata so far.
     * <p>
     *
     * @param neonBee         the {@link NeonBee} instance
     * @param schemaNamespace the namespace of the service
     * @return the buffered model for a specific schema namespace or null in case no models have been loaded so far, or
     *         no model with the given schemaNamespace is found
     * @see #getBufferedModel(String)
     * @see #getBufferedModels(NeonBee)
     * @deprecated use {@code neonBee.getModelManager().getBufferedModel(schemaNamespace)} instead
     */
    @Deprecated(forRemoval = true)
    public static EntityModel getBufferedModel(NeonBee neonBee, String schemaNamespace) {
        return neonBee.getModelManager().getBufferedModel(schemaNamespace);
    }

    /**
     * Synchronously returns the buffered model for a specific schema namespace
     * <p>
     * This is a convenience function, as for some instances (e.g. in EventBus Message Codec) the current metadata
     * definition needs to be received synchronously and without the involvement of futures.
     *
     * @param schemaNamespace the namespace of the service
     * @return the buffered model for a specific schema namespace or null in case no models have been loaded so far, or
     *         no model with the given schemaNamespace is found
     * @see #getBufferedModels()
     */
    public EntityModel getBufferedModel(String schemaNamespace) {
        return getBufferedModels() != null ? getBufferedModels().get(schemaNamespace) : null;
    }

    /**
     * Returns a future to a freshly loaded EntityModel instance and updates the globally shared instance. Please note
     * that all models files will be reloaded (from file system and / or class path). This method will also update the
     * buffered models.
     *
     * @param vertx the {@link Vertx} instance
     * @return a {@link Future} to a map from schema namespace to EntityModel
     * @see #reloadModels(NeonBee)
     * @deprecated use {@link #reloadModels(NeonBee)} instead
     */
    @Deprecated(forRemoval = true)
    public static Future<Map<String, EntityModel>> reloadModels(Vertx vertx) {
        return reloadModels(NeonBee.get(vertx));
    }

    /**
     * Returns a future to a freshly loaded EntityModel instance and updates the globally shared instance. Please note
     * that all models files will be reloaded (from file system and / or class path). This method will also update the
     * buffered models.
     *
     * @param neonBee the {@link NeonBee} instance
     * @return a {@link Future} to a map from schema namespace to EntityModel
     * @see #reloadModels()
     * @deprecated use {@code neonBee.getModelManager().reloadModels()} instead
     */
    @Deprecated(forRemoval = true)
    public static Future<Map<String, EntityModel>> reloadModels(NeonBee neonBee) {
        return neonBee.getModelManager().reloadModels();
    }

    /**
     * Returns a future to a freshly loaded EntityModel instance and updates the globally shared instance. Please note
     * that all models files will be reloaded (from file system and / or class path). This method will also update the
     * buffered models.
     *
     * @return a {@link Future} to a map from schema namespace to EntityModel
     */
    public Future<Map<String, EntityModel>> reloadModels() {
        LOGGER.info("Reload models");
        return EntityModelLoader.load(neonBee.getVertx(), externalModelDefinitions).onSuccess(pair -> {
            bufferedModels.set(Collections.unmodifiableMap(pair.left));
            pushChanges(neonBee.getVertx()).onSuccess(dummy ->

            // publish the event local only! models must be present locally on very instance in a cluster!
            neonBee.getVertx().eventBus().publish(EVENT_BUS_MODELS_LOADED_ADDRESS, null, LOCAL_DELIVERY));

        }).onFailure(throwable -> {
            LOGGER.error("Failed to reload models", throwable);
        }).map(Functions.forSupplier(() -> getBufferedModels()));

    }

    /**
     * Unregisters a given external model and reload the models.
     *
     * @param modelDefinition the model definition to remove
     * @return a {@link Future} to a map from schema namespace to EntityModel
     */
    public Future<Map<String, EntityModel>> unregisterModels(EntityModelDefinition modelDefinition) {
        return externalModelDefinitions.remove(modelDefinition) ? reloadModels() : succeededFuture(getBufferedModels());
    }

    /**
     * copies changed model files to distributed map in cluster.
     *
     * @return future
     */
    public Future<Map<String, EntityModel>> registerModels(EntityModelDefinition modelDefinition) {
        return modelDefinition != null && !modelDefinition.getCSNModelDefinitions().isEmpty()
                && externalModelDefinitions.add(modelDefinition) ? reloadModels() : getSharedModels();
    }

    public Future<Void> pushChanges(Vertx vertx) {
        LOGGER.info("pushing entity models changes in shared memory");
        final List<Future> futures = new ArrayList<>(externalModelDefinitions.size());
        Future<Lock> lockFuture = getLock(vertx);
        // adding null check for tests using vertx mock
        if (lockFuture == null) {
            return succeededFuture();
        }
        return lockFuture.compose(lock -> getAsyncMap(vertx).compose(m -> {
            externalModelDefinitions.forEach(e -> futures.add(m.putIfAbsent(e.toBuffer(), getNodeId(vertx))));
            LOGGER.info("entity models written into shared memory by node id " + getNodeId(vertx) + " = "
                    + externalModelDefinitions);
            return CompositeFuture.all(futures).mapEmpty();
        }).onComplete(result -> lock.release())).mapEmpty();
    }

    private static String getNodeId(Vertx vertx) {
        VertxImpl vertxImpl = (VertxImpl) vertx;
        ClusterManager cm = vertxImpl.getClusterManager();
        return cm == null ? "1" : cm.getNodeId();
    }

    private Future<AsyncMap<Buffer, String>> getAsyncMap(Vertx vertx) {
        return vertx.sharedData().getAsyncMap(entityModelDefinitionsSharedName());
    }

    private Future<Lock> getLock(Vertx vertx) {
        return vertx.sharedData().getLock(entityModelDefinitionsSharedName());
    }

    public static String entityModelDefinitionsSharedName() {
        return String.format("%s-%s#%s", NeonBee.class.getSimpleName(), EntityModelManager.class.getSimpleName(),
                ENTITY_MODEL_DEFINITIONS);
    }

    /**
     * should be triggered to reload models from cluster wide map.
     *
     * @param vertx the {@link Vertx} instance
     * @return future
     */
    public Future<Map<String, EntityModel>> reloadRemoteModels(Vertx vertx) {
        LOGGER.info("in reloadRemoteModels in node " + getNodeId(vertx));
        return obtainRemoteModelDefinitions()
                .compose(changedModels -> EntityModelLoader.load(vertx, externalModelDefinitions).compose(models -> {
                    bufferedModels.set(models.left);
                    LOGGER.info("in reloadRemoteModels bufferedModels updated ");
                    return succeededFuture(getBufferedModels());
                }));
    }

    private Future<Set<EntityModelDefinition>> obtainRemoteModelDefinitions() {
        return getAsyncMap(Objects.requireNonNull(NeonBee.get()).getVertx()).compose(AsyncMap::keys).compose(m -> {
            LOGGER.info("obtaining remote model definitions iterating over async map ");
            Set<EntityModelDefinition> diff = new HashSet<>(m.size());
            m.forEach(buffer -> {
                EntityModelDefinition modelDefinition = EntityModelDefinition.fromBuffer(buffer);
                if (externalModelDefinitions.contains(modelDefinition)) {
                    LOGGER.info("obtained remote model definition " + modelDefinition + " is already known");
                } else {
                    diff.add(modelDefinition);
                    externalModelDefinitions.add(modelDefinition);
                }
            });
            LOGGER.info("obtained remote model definitions " + diff);
            return succeededFuture(diff);
        });
    }

    /**
     * Either returns a future to the buffered model instance for one schema namespace, or tries to load / build the
     * model definition files (from file system and / or from the class path) first, before returning the metadata.
     *
     * @param schemaNamespace The name of the schema namespace
     * @return a succeeded {@link Future} to a specific EntityModel with a given schema namespace, or a failed future in
     *         case no models could be loaded or no model matching the schema namespace could be found
     */
    public Future<EntityModel> getSharedModel(String schemaNamespace) {
        return getSharedModels().compose(models -> {
            EntityModel model = models.get(schemaNamespace);
            return model != null ? succeededFuture(model)
                    : failedFuture(new NoSuchElementException(
                            "Cannot find data model for schema namespace " + schemaNamespace));
        });
    }

    /**
     * exists for convenience.
     *
     * @return future
     */
    public Future<Map<String, EntityModel>> getSharedModels() {
        Map<String, EntityModel> models = getBufferedModels();
        if (models != null) {
            return succeededFuture(models);
        }
        // if not try to reload the models and return the loaded data model
        Future<Map<String, EntityModel>> localModels =
                new SharedDataAccessor(neonBee.getVertx(), EntityModelManager.class).getLocalLock()
                        .transform(asyncLocalLock -> {
                            Map<String, EntityModel> retryModels = getBufferedModels();
                            if (retryModels != null) {
                                if (asyncLocalLock.succeeded()) {
                                    asyncLocalLock.result().release();
                                }
                                return succeededFuture(retryModels);
                            } else {
                                // ignore the lockResult, worst case, we are reading the serviceMetadata twice
                                return reloadModels().onComplete(loadedModels -> {
                                    if (asyncLocalLock.succeeded()) {
                                        asyncLocalLock.result().release();
                                    }
                                });
                            }
                        });
        return localModels
                .compose(local -> local.isEmpty() ? reloadRemoteModels(neonBee.getVertx()) : succeededFuture(local));
    }
}
