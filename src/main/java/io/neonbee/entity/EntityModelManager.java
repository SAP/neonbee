package io.neonbee.entity;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;

import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.olingo.server.api.OData;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Functions;

import io.neonbee.NeonBee;
import io.neonbee.internal.SharedDataAccessor;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;

/**
 * The {@link EntityModelManager} is used to manage shared model files across a given NeonBee instance.
 *
 * It loads and manages a shared instance of any number of {@link EntityModel EntityModels}, that are loaded from the
 * classpath / the NeonBee models folder and which might be extended by external {@link EntityModelDefinition
 * EntityModelDefinitions}.
 */
public final class EntityModelManager {
    /**
     * Every time new models are loaded a message will be published to this event bus address.
     */
    public static final String EVENT_BUS_MODELS_LOADED_ADDRESS = EntityModelManager.class.getSimpleName() + "Loaded";

    /**
     * A map of all buffered NeonBee models.
     *
     * Do not use a {@link ConcurrentHashMap} here, as the references to Vert.x need to stay weak for Vert.x to properly
     * garbage collected, at the end of its lifetime. This means the {@link #BUFFERED_MODELS} map must never be iterated
     * over, in order to not cause any {@link ConcurrentModificationException}. The inner maps are unmodifiable in any
     * case.
     */
    @VisibleForTesting
    static final Map<NeonBee, Map<String, EntityModel>> BUFFERED_MODELS = new WeakHashMap<>();

    /**
     * A set of externally managed model definition files. External models definitions are added to the model loading
     * step and can be added / removed from any client of the {@link EntityModelManager}.
     *
     * Same as for the {@link #BUFFERED_MODELS} the NeonBee reference needs to stay weak, in order to allow for garbage
     * collection to occur. As also the internal set is written to, the set needs to be a concurrent one.
     */
    @VisibleForTesting
    static final Map<NeonBee, Set<EntityModelDefinition>> EXTERNAL_MODEL_DEFINITIONS = new WeakHashMap<>();

    /**
     * @deprecated remove with {@link #unregisterModels(Vertx, String)}
     */
    @Deprecated(forRemoval = true)
    private static final Map<NeonBee, Map<String, EntityModelDefinition>> EXTERNAL_MODEL_IDENTIFIERS =
            new WeakHashMap<>();

    private static final ThreadLocal<OData> THREAD_LOCAL_ODATA = ThreadLocal.withInitial(OData::newInstance);

    private static final DeliveryOptions LOCAL_DELIVERY = new DeliveryOptions().setLocalOnly(true);

    /**
     * The {@link EntityModelManager} doesn't need to get instantiated.
     */
    private EntityModelManager() {}

    /**
     * Get a buffered instance to OData.
     *
     * @return a thread local buffered instance of OData
     */
    public static OData getBufferedOData() {
        return THREAD_LOCAL_ODATA.get();
    }

    /**
     * Synchronously returns the buffered models, which could be null, in case {@link #getSharedModels(Vertx)} or
     * {@link #reloadModels(Vertx)} was never called or returned no valid metadata so far.
     * <p>
     * This is a convenience function, as for some instances (e.g. in EventBus Message Codec) the current metadata
     * definition needs to be received synchronously and without the involvement of futures. Whenever possible, use
     * {@link #getSharedModels(Vertx)} instead.
     *
     * @see #getBufferedModels(NeonBee)
     * @deprecated use {@link #getBufferedModels(NeonBee)} instead
     * @param vertx the {@link Vertx} instance
     * @return The buffered models for all schema namespaces or null in case no models have been loaded so far
     */
    @Deprecated(forRemoval = true)
    public static Map<String, EntityModel> getBufferedModels(Vertx vertx) {
        return getBufferedModels(NeonBee.get(vertx));
    }

    /**
     * Synchronously returns the buffered models, which could be null, in case {@link #getSharedModels(NeonBee)} or
     * {@link #reloadModels(NeonBee)} was never called or returned no valid metadata so far.
     * <p>
     * This is a convenience function, as for some instances (e.g. in EventBus Message Codec) the current metadata
     * definition needs to be received synchronously and without the involvement of futures. Whenever possible, use
     * {@link #getSharedModels(NeonBee)} instead.
     *
     * @param neonBee the {@link NeonBee} instance
     * @return The buffered models for all schema namespaces or null in case no models have been loaded so far
     */
    public static Map<String, EntityModel> getBufferedModels(NeonBee neonBee) {
        return BUFFERED_MODELS.get(neonBee);
    }

    /**
     * Synchronously returns the buffered model for a specific schema namespace, which could be null, in case
     * {@link #getSharedModels(Vertx)} or {@link #reloadModels(Vertx)} was never called or returned no valid metadata so
     * far.
     * <p>
     * This is a convenience function, as for some instances (e.g. in EventBus Message Codec) the current metadata
     * definition needs to be received synchronously and without the involvement of futures. Whenever possible, use
     * {@link #getSharedModel(Vertx, String)} instead.
     *
     * @see #getBufferedModel(NeonBee, String)
     * @deprecated use {@link #getBufferedModel(NeonBee, String)} instead
     * @param vertx           the {@link Vertx} instance
     * @param schemaNamespace the namespace of the service
     * @return the buffered model for a specific schema namespace or null in case no models have been loaded so far, or
     *         no model with the given schemaNamespace is found
     */
    @Deprecated(forRemoval = true)
    public static EntityModel getBufferedModel(Vertx vertx, String schemaNamespace) {
        return getBufferedModel(NeonBee.get(vertx), schemaNamespace);
    }

    /**
     * Synchronously returns the buffered model for a specific schema namespace, which could be null, in case
     * {@link #getSharedModels(NeonBee)} or {@link #reloadModels(NeonBee)} was never called or returned no valid
     * metadata so far.
     * <p>
     * This is a convenience function, as for some instances (e.g. in EventBus Message Codec) the current metadata
     * definition needs to be received synchronously and without the involvement of futures. Whenever possible, use
     * {@link #getSharedModel(NeonBee, String)} instead.
     *
     * @param neonBee         the {@link NeonBee} instance
     * @param schemaNamespace the namespace of the service
     * @return the buffered model for a specific schema namespace or null in case no models have been loaded so far, or
     *         no model with the given schemaNamespace is found
     * @see #getBufferedModels(Vertx)
     */
    public static EntityModel getBufferedModel(NeonBee neonBee, String schemaNamespace) {
        Map<String, EntityModel> models = BUFFERED_MODELS.get(neonBee);
        return models != null ? models.get(schemaNamespace) : null;
    }

    /**
     * Either returns a future to the buffered models, or tries to load / build the model definition files (from file
     * system and / or from the classpath).
     *
     * @see #getSharedModels(NeonBee)
     * @deprecated use {@link #getSharedModels(NeonBee)} instead
     * @param vertx the {@link Vertx} instance
     * @return a {@link Future} to a map from schema namespace to EntityModel
     */
    @Deprecated(forRemoval = true)
    public static Future<Map<String, EntityModel>> getSharedModels(Vertx vertx) {
        return getSharedModels(NeonBee.get(vertx));
    }

    /**
     * Either returns a future to the buffered models, or tries to load / build the model definition files (from file
     * system and / or from the classpath).
     *
     * @param neonBee the {@link NeonBee} instance
     * @return a {@link Future} to a map from schema namespace to EntityModel
     */
    public static Future<Map<String, EntityModel>> getSharedModels(NeonBee neonBee) {
        // ignore the race condition here, in case serviceMetadata changes from if to return command, the new version
        // can be returned, in case it is null, it'll be checked once again inside of the synchronized block
        Map<String, EntityModel> models = getBufferedModels(neonBee);
        if (models != null) {
            return succeededFuture(models);
        }

        // if not try to reload the models and return the loaded data model
        return new SharedDataAccessor(neonBee.getVertx(), EntityModelManager.class).getLocalLock()
                .transform(asyncLocalLock -> {
                    Map<String, EntityModel> retryModels = getBufferedModels(neonBee);
                    if (retryModels != null) {
                        if (asyncLocalLock.succeeded()) {
                            asyncLocalLock.result().release();
                        }
                        return succeededFuture(retryModels);
                    } else {
                        // ignore the lockResult, worst case, we are reading the serviceMetadata twice
                        return reloadModels(neonBee).onComplete(loadedModels -> {
                            if (asyncLocalLock.succeeded()) {
                                asyncLocalLock.result().release();
                            }
                        });
                    }
                });
    }

    /**
     * Either returns a future to the buffered model instance for one schema namespace, or tries to load / build the
     * model definition files (from file system and / or from the classpath) first, before returning the metadata.
     *
     * @see #getSharedModel(NeonBee, String)
     * @deprecated use {@link #getSharedModel(NeonBee, String)} instead
     * @param vertx           the {@link Vertx} instance
     * @param schemaNamespace The name of the schema namespace
     * @return a succeeded {@link Future} to a specific EntityModel with a given schema namespace, or a failed future in
     *         case no models could be loaded or no model matching the schema namespace could be found
     */
    @Deprecated(forRemoval = true)
    public static Future<EntityModel> getSharedModel(Vertx vertx, String schemaNamespace) {
        return getSharedModel(NeonBee.get(vertx), schemaNamespace);
    }

    /**
     * Either returns a future to the buffered model instance for one schema namespace, or tries to load / build the
     * model definition files (from file system and / or from the classpath) first, before returning the metadata.
     *
     * @param neonBee         the {@link NeonBee} instance
     * @param schemaNamespace The name of the schema namespace
     * @return a succeeded {@link Future} to a specific EntityModel with a given schema namespace, or a failed future in
     *         case no models could be loaded or no model matching the schema namespace could be found
     */
    public static Future<EntityModel> getSharedModel(NeonBee neonBee, String schemaNamespace) {
        return getSharedModels(neonBee).compose(models -> {
            EntityModel model = models.get(schemaNamespace);
            return model != null ? succeededFuture(model)
                    : failedFuture(new NoSuchElementException(
                            "Cannot find data model for schema namespace " + schemaNamespace));
        });
    }

    /**
     * Returns a future to a freshly loaded EntityModel instance and updates the globally shared instance. Please note
     * that all models files will be reloaded (from file system and / or classpath). This method will also update the
     * buffered models.
     *
     * @see #reloadModels(NeonBee)
     * @deprecated use {@link #reloadModels(NeonBee)} instead
     * @param vertx the {@link Vertx} instance
     * @return a {@link Future} to a map from schema namespace to EntityModel
     */
    @Deprecated(forRemoval = true)
    public static Future<Map<String, EntityModel>> reloadModels(Vertx vertx) {
        return reloadModels(NeonBee.get(vertx));
    }

    /**
     * Returns a future to a freshly loaded EntityModel instance and updates the globally shared instance. Please note
     * that all models files will be reloaded (from file system and / or classpath). This method will also update the
     * buffered models.
     *
     * @param neonBee the {@link NeonBee} instance
     * @return a {@link Future} to a map from schema namespace to EntityModel
     */
    public static Future<Map<String, EntityModel>> reloadModels(NeonBee neonBee) {
        return EntityModelLoader
                .load(neonBee.getVertx(), EXTERNAL_MODEL_DEFINITIONS.getOrDefault(neonBee, Collections.emptySet()))
                .onSuccess(models -> {
                    BUFFERED_MODELS.put(neonBee, models);
                    // publish the event local only! models must be present locally on very instance in a cluster!
                    neonBee.getVertx().eventBus().publish(EVENT_BUS_MODELS_LOADED_ADDRESS, null, LOCAL_DELIVERY);
                });
    }

    /**
     * Register an external model. External models will be loaded alongside any models that are in the classpath or in
     * the models directory.
     *
     * @see #registerModels(NeonBee, EntityModelDefinition)
     * @deprecated use {@link EntityModelDefinition#EntityModelDefinition(Map, Map)} to initialize a
     *             {@link EntityModelDefinition} and use {@link #registerModels(NeonBee, EntityModelDefinition)} instead
     * @param vertx                      the {@link Vertx} instance
     * @param definitionIdentifier       the definition identifier of this model
     * @param csnModelDefinitions        the CSN model definitions
     * @param associatedModelDefinitions the associated model definitions, such as EDMX files
     * @return a {@link Future} to a map from schema namespace to EntityModel
     */
    @Deprecated(forRemoval = true)
    public static Future<Map<String, EntityModel>> registerModels(Vertx vertx, String definitionIdentifier,
            Map<String, byte[]> csnModelDefinitions, Map<String, byte[]> associatedModelDefinitions) {
        return registerModels(NeonBee.get(vertx), EXTERNAL_MODEL_IDENTIFIERS
                .computeIfAbsent(NeonBee.get(vertx), Functions.forSupplier(ConcurrentHashMap::new)).computeIfAbsent(
                        definitionIdentifier, newDefinitionIdentifier -> new EntityModelDefinition(csnModelDefinitions,
                                associatedModelDefinitions)));
    }

    /**
     * Register an external model. External models will be loaded alongside any models that are in the classpath or in
     * the models directory.
     *
     * @param neonBee    the Vert.x instance
     * @param definition the entity model definition
     * @return a {@link Future} to a map from schema namespace to EntityModel
     */
    public static Future<Map<String, EntityModel>> registerModels(NeonBee neonBee, EntityModelDefinition definition) {
        EXTERNAL_MODEL_DEFINITIONS.computeIfAbsent(neonBee, Functions.forSupplier(ConcurrentHashMap::newKeySet))
                .add(definition);
        return reloadModels(neonBee);
    }

    /**
     * Unregisters a given external model and reload the models.
     *
     * @see #unregisterModels(NeonBee, EntityModelDefinition)
     * @deprecated use {@link #unregisterModels(NeonBee, EntityModelDefinition)} instead
     * @param vertx                the {@link Vertx} instance
     * @param definitionIdentifier the model definition identifier to remove
     * @return a {@link Future} to a map from schema namespace to EntityModel
     */
    @Deprecated(forRemoval = true)
    public static Future<Map<String, EntityModel>> unregisterModels(Vertx vertx, String definitionIdentifier) {
        NeonBee neonBee = NeonBee.get(vertx);
        Map<String, EntityModelDefinition> identifiers = EXTERNAL_MODEL_IDENTIFIERS.get(neonBee);
        return identifiers != null ? unregisterModels(neonBee, identifiers.get(definitionIdentifier))
                : getSharedModels(neonBee);
    }

    /**
     * Unregisters a given external model and reload the models.
     *
     * @param neonBee    the Vert.x instance
     * @param definition the model definition to remove
     * @return a {@link Future} to a map from schema namespace to EntityModel
     */
    public static Future<Map<String, EntityModel>> unregisterModels(NeonBee neonBee, EntityModelDefinition definition) {
        Set<EntityModelDefinition> definitions = EXTERNAL_MODEL_DEFINITIONS.get(neonBee);
        return definitions != null && definitions.remove(definition) ? reloadModels(neonBee) : getSharedModels(neonBee);
    }
}
