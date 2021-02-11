package io.neonbee.entity;

import static io.neonbee.internal.Helper.LOCAL_DELIVERY;
import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.unmodifiableMap;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.xml.stream.XMLStreamException;

import org.apache.olingo.server.api.OData;
import org.apache.olingo.server.api.ServiceMetadata;
import org.apache.olingo.server.core.MetadataParser;
import org.apache.olingo.server.core.SchemaBasedEdmProvider;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.sap.cds.reflect.CdsModel;

import io.neonbee.NeonBee;
import io.neonbee.NeonBeeOptions;
import io.neonbee.internal.Helper;
import io.neonbee.internal.Helper.BufferInputStream;
import io.neonbee.internal.SharedDataAccessor;
import io.neonbee.internal.helper.AsyncHelper;
import io.neonbee.internal.helper.FileSystemHelper;
import io.neonbee.internal.processor.etag.MetadataETagSupport;
import io.neonbee.internal.scanner.ClassPathScanner;
import io.neonbee.logging.LoggingFacade;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.FileSystemException;

public final class EntityModelManager {
    /**
     * Every time new models are loaded a message will be published to this event bus address.
     */
    public static final String EVENT_BUS_MODELS_LOADED_ADDRESS = EntityModelManager.class.getSimpleName() + "Loaded";

    @VisibleForTesting
    static final String NEONBEE_MODELS = "NeonBee-Models";

    // you shall not use a ConcurrentHashMap here, as the references to Vert.x need to stay weak for Vert.x to properly
    // garbage collected, at the end of its lifetime. This means the bufferedModels map MUST never be iterated over, in
    // order to not cause any ConcurrentModificationException. the inner map is unmodifiable in any case
    @VisibleForTesting
    static final Map<Vertx, Map<String, EntityModel>> BUFFERED_MODELS = new WeakHashMap<>();

    // This is a cache to manage the relationship between a NeonBee-Module and the models contributed by this module, so
    // that these models can be removed in case of module undeployment
    @VisibleForTesting
    static final Map<Vertx, Map<String, Map<String, EntityModel>>> BUFFERED_MODULE_MODELS = new WeakHashMap<>();

    private static final ThreadLocal<OData> THREAD_LOCAL_ODATA = ThreadLocal.withInitial(OData::newInstance);

    private EntityModelManager() {
        // no need to instantiate a manager class
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
     * Synchronously returns the buffered models, which could be null, in case {@link #getSharedModels(Vertx)} or
     * {@link #reloadModels(Vertx)} was never called or returned no valid metadata so far.
     * <p>
     * This is a convenience function, as for some instances (e.g. in EventBus Message Codec) the current metadata
     * definition needs to be received synchronously and without the involvement of futures. Whenever possible, use
     * {@link #getSharedModels(Vertx)} instead.
     *
     * @param vertx An instance of {@link Vertx}
     * @return The buffered models for all schema namespaces or null in case no models have been loaded so far
     */
    public static Map<String, EntityModel> getBufferedModels(Vertx vertx) {
        return BUFFERED_MODELS.get(vertx);
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
     * @param vertx           An instance of {@link Vertx}
     * @param schemaNamespace the namespace of the service
     * @return the buffered model for a specific schema namespace or null in case no models have been loaded so far, or
     *         no model with the given schemaNamespace is found
     * @see #getBufferedModels(Vertx)
     */
    public static EntityModel getBufferedModel(Vertx vertx, String schemaNamespace) {
        return Optional.ofNullable(BUFFERED_MODELS.get(vertx)).map(models -> models.get(schemaNamespace)).orElse(null);
    }

    /**
     * Either returns a future to the buffered models, or tries to load / build the model definition files (from file
     * system and / or from the classpath).
     *
     * @param vertx The Vert.x instance to asynchronously to load the models
     * @return a {@link Future} to a map from schema namespace to EntityModel
     */
    public static Future<Map<String, EntityModel>> getSharedModels(Vertx vertx) {
        // ignore the race condition here, in case serviceMetadata changes from if to return command, the new version
        // can be returned, in case it is null, it'll be checked once again inside of the synchronized block
        Map<String, EntityModel> models = getBufferedModels(vertx);
        if (models != null) {
            return succeededFuture(models);
        }

        // if not try to reload the models and return the loaded data model
        return Future.future(
                modelsHandler -> new SharedDataAccessor(vertx, EntityModelManager.class).getLocalLock(asyncLock -> {
                    Map<String, EntityModel> tryModels = getBufferedModels(vertx);
                    if (tryModels != null) {
                        if (asyncLock.succeeded()) {
                            asyncLock.result().release();
                        }
                        modelsHandler.complete(tryModels);
                    } else {
                        // ignore the lockResult, worst case, we are reading the serviceMetadata twice
                        reloadModels(vertx).onComplete(loadedModels -> {
                            if (asyncLock.succeeded()) {
                                asyncLock.result().release();
                            }
                            modelsHandler.handle(loadedModels);
                        });
                    }
                }));
    }

    /**
     * Either returns a future to the buffered model instance for one schema namespace, or tries to load / build the
     * model definition files (from file system and / or from the classpath) first, before returning the metadata.
     *
     * @param vertx           The Vert.x instance to asynchronously to load the models
     * @param schemaNamespace The name of the schema namespace
     * @return a succeeded {@link Future} to a specific EntityModel with a given schema namespace, or a failed future in
     *         case no models could be loaded or no model matching the schema namespace could be found
     */
    public static Future<EntityModel> getSharedModel(Vertx vertx, String schemaNamespace) {
        return getSharedModels(vertx).compose(models -> Optional.ofNullable(models.get(schemaNamespace))
                .map(Future::succeededFuture).orElseGet(() -> failedFuture(
                        new NoSuchElementException("Cannot find data model for schema namespace " + schemaNamespace))));
    }

    /**
     * Returns a future to a freshly loaded EntityModel instance and updates the globally shared instance. Please note
     * that all models files will be reloaded (from file system and / or classpath). This method will also update the
     * buffered models.
     *
     * @param vertx The Vert.x instance to use to load the files asynchronously
     * @return a {@link Future} to a map from schema namespace to EntityModel
     */
    public static Future<Map<String, EntityModel>> reloadModels(Vertx vertx) {
        return Loader.load(vertx).map(models -> {
            BUFFERED_MODELS.put(vertx, models);
            // publish the event local only! models must be present locally on very instance in a cluster!
            vertx.eventBus().publish(EVENT_BUS_MODELS_LOADED_ADDRESS, null, LOCAL_DELIVERY);
            return models;
        });
    }

    /**
     * Registers new model from a NeonBeeModule.
     *
     * @param vertx           the Vert.x instance
     * @param module          unique identifier of a NeonBee module
     * @param models          a map of CSN models
     * @param extensionModels a map of all available EDMX payloads
     * @return a {@link Future} model map of {@link String} and {@link EntityModel}
     */
    public static Future<Map<String, EntityModel>> registerModels(Vertx vertx, String module,
            Map<String, byte[]> models, Map<String, byte[]> extensionModels) {
        return Loader.loadModuleModels(vertx, models, extensionModels).map(modelMap -> {
            BUFFERED_MODULE_MODELS.putIfAbsent(vertx, new ConcurrentHashMap<>());
            BUFFERED_MODULE_MODELS.get(vertx).put(module, modelMap);
            Map<String, EntityModel> currentModels = BUFFERED_MODELS.get(vertx);
            BUFFERED_MODELS.put(vertx, Optional.ofNullable(currentModels).map(currentModelMap -> {
                Map<String, EntityModel> workingMap = new HashMap<>(currentModels);
                workingMap.putAll(modelMap);
                return unmodifiableMap(workingMap);
            }).orElse(unmodifiableMap(modelMap)));
            // publish the event local only! models must be present locally on every instance in a cluster!
            vertx.eventBus().publish(EVENT_BUS_MODELS_LOADED_ADDRESS, null, LOCAL_DELIVERY);
            return modelMap;
        });
    }

    /**
     * Registers new model from a NeonBeeModule.
     *
     * @param vertx  the Vert.x instance
     * @param module unique identifier of a NeonBee module
     */
    public static void unregisterModels(Vertx vertx, String module) {
        Optional.ofNullable(BUFFERED_MODULE_MODELS.get(vertx)).flatMap(map -> Optional.ofNullable(map.get(module)))
                .ifPresent(moduleModels -> Optional.ofNullable(BUFFERED_MODELS.get(vertx)).ifPresent(currentModels -> {
                    Map<String, EntityModel> workingMap = new HashMap<>(currentModels);
                    moduleModels.keySet().forEach(workingMap::remove);
                    BUFFERED_MODELS.put(vertx, unmodifiableMap(workingMap));
                }));
    }

    @VisibleForTesting
    static class Loader {
        private static final LoggingFacade LOGGER = LoggingFacade.create(Loader.class);

        private static final HashFunction HASH_FUNCTION = Hashing.murmur3_128();

        private static final PathMatcher MODELS_PATH_MATCHER = FileSystems.getDefault().getPathMatcher("glob:**.csn");

        private static final String CSN = ".csn";

        @VisibleForTesting
        Map<String, EntityModel> models = new HashMap<>();

        @VisibleForTesting
        Map<String, SchemaBasedEdmProvider> edmProviders = new HashMap<>();

        @VisibleForTesting
        Map<String, MetadataParser> metadataParsers = new HashMap<>();

        private final Vertx vertx;

        @VisibleForTesting
        Loader(Vertx vertx) {
            this.vertx = vertx;
        }

        /**
         * Load models from model directory and classpath.
         *
         * @param vertx the Vert.x instance
         * @return a unmodifiable map of loaded models from model directory and classpath
         */
        public static Future<Map<String, EntityModel>> load(Vertx vertx) {
            return new Loader(vertx).loadModelsFromModelDirectoryAndClasspath().map(Collections::unmodifiableMap);
        }

        /**
         * Load models from payloads provided as maps of models and extension models.
         *
         * @param vertx           the Vert.x instance
         * @param models          the models
         * @param extensionModels the extension models
         * @return a unmodifiable map of the loaded models from the module
         */
        public static Future<Map<String, EntityModel>> loadModuleModels(Vertx vertx, Map<String, byte[]> models,
                Map<String, byte[]> extensionModels) {
            return new Loader(vertx).loadModelsFromModule(models, extensionModels).map(Collections::unmodifiableMap);
        }

        /*
         * Get the service metadata of the bufferedOData instance w/o the schema namespace. This can be used e.g. in the
         * case if it is the initial loading and the schema namespace is unknown. Then the unknown schema namespace can
         * be fetched from the returned ServiceMetadata instance.
         *
         * ATTENTION: This method contains BLOCKING code and thus should only be called in a Vert.x worker thread!
         */
        private static ServiceMetadata createServiceMetadata(Buffer csdl) throws XMLStreamException {
            InputStreamReader reader = new InputStreamReader(new BufferInputStream(csdl), UTF_8);
            SchemaBasedEdmProvider edmProvider = new MetadataParser().referenceResolver(null).buildEdmProvider(reader);
            return getBufferedOData().createServiceMetadata(edmProvider, Collections.emptyList());
        }

        /**
         * Transform the EDMX file which contains the XML representation of the OData Common Schema Definition Language
         * (CSDL) to a ServiceMetadata instance.
         * <p>
         * ATTENTION: This method contains BLOCKING code and thus should only be called in a Vert.x worker thread!
         *
         * @param csdl            the String representation of the EDMX file's content
         * @param schemaNamespace the schema namespace of the service in the xsdl
         * @return a {@link ServiceMetadata} instance of the EDMX file which contains the XML representation of the CSDL
         *         to a ServiceMetadata instance
         */
        private ServiceMetadata createServiceMetadata(Buffer csdl, String schemaNamespace) throws XMLStreamException {
            synchronized (this) {
                // Create a metadata parser instance for the schema namespace if it is not existing
                MetadataParser parser = metadataParsers.computeIfAbsent(schemaNamespace,
                        newSchemaNamespace -> new MetadataParser().referenceResolver(null).parseAnnotations(true));

                Reader csdlReader = new InputStreamReader(new BufferInputStream(csdl), UTF_8);
                // does NOT to be a ConcurrentHashMap, as only get / put access! so no iteration is done!
                SchemaBasedEdmProvider provider = edmProviders.get(schemaNamespace);
                if (provider == null) {
                    edmProviders.put(schemaNamespace, provider = parser.buildEdmProvider(csdlReader));
                } else {
                    parser.addToEdmProvider(provider, csdlReader);
                }

                /*
                 * Please note: ETag for the service document and the metadata document. The same field for
                 * service-document and metadata-document ETag is used. It must change whenever the corresponding
                 * document changes.
                 */
                return getBufferedOData().createServiceMetadata(provider, Collections.emptyList(),
                        new MetadataETagSupport(generateMetadataETag(csdl)));
            }
        }

        /**
         * This method generates an ETag string as defined in RFC2616/RFC7232 based on the provided EDMX file content
         * contains the XML representation of the OData Common Schema Definition Language (CSDL).
         *
         * @return ETag string
         */
        private static String generateMetadataETag(Buffer cdsl) {
            return "\"" + HASH_FUNCTION.newHasher().putUnencodedChars(cdsl.toString()).hash().toString() + "\"";
        }

        /**
         * Tries to load models from the provided maps of a NeonBee-Module.
         *
         * @param models          CSN models
         * @param extensionModels EDMX models
         * @return a map of the loaded models from a module
         */
        private Future<Map<String, EntityModel>> loadModelsFromModule(Map<String, byte[]> models,
                Map<String, byte[]> extensionModels) {
            return Helper.allComposite(models.entrySet().stream()
                    .map(entry -> loadModel(entry.getKey(), entry.getValue(), extensionModels))
                    .collect(Collectors.toList())).map(this.models);
        }

        /**
         * Tries to load all model files available.
         */
        private Future<Map<String, EntityModel>> loadModelsFromModelDirectoryAndClasspath() {
            NeonBeeOptions options = NeonBee.instance(vertx).getOptions();
            return CompositeFuture.all(loadDir(options.getModelsDirectory()),
                    !options.shouldIgnoreClassPath() ? scanClassPath() : succeededFuture()).map(models);
        }

        /**
         * Tries to read / load all model files from a given directory recursively.
         *
         * @param path The path to the directory to load the files from
         * @return A succeeded future of Void, in case reading the model was a success
         */
        @VisibleForTesting
        Future<Void> loadDir(Path path) {
            return FileSystemHelper.readDir(vertx, path).recover(throwable -> {
                // ignore if the models directory does not exist, or a file / folder was deleted after readDir
                return ((throwable instanceof FileSystemException) && throwable.getMessage().contains("Does not exist"))
                        ? succeededFuture(Collections.emptyList())
                        : failedFuture(throwable);
            }).compose(
                    files -> CompositeFuture.all(files.stream()
                            .map(file -> FileSystemHelper.isDirectory(vertx, file)
                                    .compose(isDir -> isDir ? loadDir(file) : loadModel(file)))
                            .collect(Collectors.toList())))
                    .compose(future -> succeededFuture());
        }

        Future<Void> loadModel(Path csnFile) {
            if (!MODELS_PATH_MATCHER.matches(csnFile)) {
                return succeededFuture();
            }

            return loadCsnModel(csnFile)
                    .compose(cdsModel -> CompositeFuture
                            .all(ModelDefinitionHelper.resolveEdmxPaths(csnFile, cdsModel).stream()
                                    .map(this::loadEdmxModel).collect(Collectors.toList()))
                            .onSuccess(compositeFuture -> {
                                buildModelMap(cdsModel, compositeFuture.<ServiceMetadata>list());
                            }))
                    .mapEmpty();
        }

        Future<Void> loadModel(String csnFile, byte[] csnPayload, Map<String, byte[]> extensionModels) {
            return loadCsnModel(csnPayload).compose(cdsModel -> CompositeFuture.all(ModelDefinitionHelper
                    .resolveEdmxPaths(Path.of(csnFile), cdsModel).stream().map(Path::toString).map(path -> {
                        return Optional.ofNullable(extensionModels.get(path)).orElse(extensionModels
                                .get(path.replace(File.separatorChar, File.separatorChar == '/' ? '\\' : '/')));
                    }).map(this::loadEdmxModel).collect(Collectors.toList())).onSuccess(compositeFuture -> {
                        buildModelMap(cdsModel, compositeFuture.<ServiceMetadata>list());
                    })).mapEmpty();
        }

        private void buildModelMap(CdsModel cdsModel, List<ServiceMetadata> edmxModels) {
            Map<String, ServiceMetadata> edmxMap = edmxModels.stream()
                    .collect(Collectors.toMap(
                            serviceMetaData -> serviceMetaData.getEdm().getEntityContainer().getNamespace(),
                            Function.identity()));
            EntityModel entityModel = EntityModel.of(cdsModel, edmxMap);
            String namespace = ModelDefinitionHelper.getNamespace(cdsModel);
            models.put(namespace, entityModel);
            LOGGER.info("Entity model of model with schema namespace {} was added the entity model map.", namespace);
        }

        /**
         * Loads the CSN model located at a path.
         *
         * @param file path of the CSN model
         * @return a {@link Future} with loaded model inside
         */
        @VisibleForTesting
        Future<CdsModel> loadCsnModel(Path file) {
            return FileSystemHelper.readFile(vertx, file).map(Buffer::toString)
                    .compose(csnString -> AsyncHelper.executeBlocking(vertx, () -> CdsModel.read(csnString)));
        }

        /**
         * Loads the CSN model from byte array.
         *
         * @param csnModel payload of a CSN model
         * @return a {@link Future} with loaded model inside
         */
        @VisibleForTesting
        Future<CdsModel> loadCsnModel(byte[] csnModel) {
            return Future.future(handler -> {
                vertx.executeBlocking(blockingPromise -> {
                    try (InputStream inputStream = new ByteArrayInputStream(csnModel)) {
                        CdsModel cdsModel = CdsModel.read(inputStream);
                        blockingPromise.complete(cdsModel);
                    } catch (Exception e) {
                        blockingPromise.fail(e);
                    }
                }, handler);
            });
        }

        /**
         * Loads the EDMX model located at a path.
         *
         * @param file path of the EDMX model
         * @return a future with loaded model inside
         */
        private Future<ServiceMetadata> loadEdmxModel(Path file) {
            return FileSystemHelper.readFile(vertx, file).compose(this::convertPayloadToServiceMetaData);
        }

        /**
         * Loads the EDMX model from byte array.
         *
         * @param payload path of the EDMX model
         * @return a future with loaded model inside
         */
        private Future<ServiceMetadata> loadEdmxModel(byte[] payload) {
            return succeededFuture(Buffer.buffer(payload)).compose(this::convertPayloadToServiceMetaData);
        }

        private Future<ServiceMetadata> convertPayloadToServiceMetaData(Buffer buffer) {
            return Future.future(handler -> {
                vertx.executeBlocking(blockingPromise -> {
                    try {
                        // Get the service metadata first w/o the schema namespace, because we have to read it
                        ServiceMetadata serviceMetadata = createServiceMetadata(buffer);
                        String schemaNamespace = serviceMetadata.getEdm().getSchemas().get(0).getNamespace();
                        serviceMetadata = createServiceMetadata(buffer, schemaNamespace);
                        blockingPromise.complete(serviceMetadata);
                    } catch (Exception e) {
                        blockingPromise.fail(e);
                    }
                }, handler);
            });
        }

        /**
         * Tries to read / load all model files from the class path.
         */
        @VisibleForTesting
        Future<Void> scanClassPath() {
            LOGGER.info("Loading models from class path");
            return Future.<List<String>>future(blockingHandler -> vertx.executeBlocking(blockingResult -> {
                try {
                    // Vert.x FileResolver will actually also work for files only
                    // available on the class path, so loading the model like any other ordinary file, will just work!
                    List<String> files = new ArrayList<>();
                    ClassPathScanner scanner = new ClassPathScanner();
                    files.addAll(scanner.scanWithPredicate(name -> name.endsWith(CSN)));
                    files.addAll(scanner.scanManifestFiles(NEONBEE_MODELS));
                    blockingResult.complete(files);
                } catch (IOException e) {
                    blockingResult.fail(e);
                }
            }, blockingHandler)).compose(files -> CompositeFuture
                    // Use distinct because models mentioned in the manifest could also exists as file.
                    .all(files.stream().distinct().map(name -> loadModel(Path.of(name)).otherwise(throwable -> {
                        // models loaded from the class path are non-vital for NeonBee so continue anyways
                        LOGGER.warn("Loading model {} from class path failed", throwable, name);
                        return null;
                    })).collect(Collectors.toList()))).mapEmpty();
        }
    }
}
