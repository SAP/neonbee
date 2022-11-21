package io.neonbee.entity;

import static io.neonbee.entity.EntityModelDefinition.CSN;
import static io.neonbee.entity.EntityModelManager.getBufferedOData;
import static io.neonbee.internal.helper.AsyncHelper.allComposite;
import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.xml.stream.XMLStreamException;

import org.apache.olingo.commons.api.edm.Edm;
import org.apache.olingo.commons.api.edm.EdmEntityContainer;
import org.apache.olingo.server.api.ServiceMetadata;
import org.apache.olingo.server.api.etag.ServiceMetadataETagSupport;
import org.apache.olingo.server.core.MetadataParser;
import org.apache.olingo.server.core.SchemaBasedEdmProvider;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Streams;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.sap.cds.reflect.CdsEntity;
import com.sap.cds.reflect.CdsModel;

import io.neonbee.NeonBee;
import io.neonbee.NeonBeeOptions;
import io.neonbee.internal.helper.AsyncHelper;
import io.neonbee.internal.helper.BufferHelper.BufferInputStream;
import io.neonbee.internal.helper.FileSystemHelper;
import io.neonbee.internal.scanner.ClassPathScanner;
import io.neonbee.logging.LoggingFacade;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.FileSystemException;

class EntityModelLoader {
    @VisibleForTesting
    static final String NEONBEE_MODELS = "NeonBee-Models";

    private static final LoggingFacade LOGGER = LoggingFacade.create();

    private static final HashFunction HASH_FUNCTION = Hashing.murmur3_128();

    private static final PathMatcher MODELS_PATH_MATCHER = FileSystems.getDefault().getPathMatcher("glob:**.csn");

    @VisibleForTesting
    Map<String, EntityModel> models = new HashMap<>();

    @VisibleForTesting
    Map<String, SchemaBasedEdmProvider> edmProviders = new HashMap<>();

    @VisibleForTesting
    Map<String, MetadataParser> metadataParsers = new HashMap<>();

    private final Vertx vertx;

    @VisibleForTesting
    EntityModelLoader(Vertx vertx) {
        this.vertx = vertx;
    }

    /**
     * Load models from model directory and class path and return a future to a map of all loaded models.
     *
     * @return a map of all loaded models
     */
    public static Future<Map<String, EntityModel>> load(Vertx vertx) {
        return new EntityModelLoader(vertx).loadModelsFromModelDirectoryAndClassPath()
                .map(EntityModelLoader::getModels);
    }

    /**
     * Load models from model directory and class path, as well as from the maps provided and return a future to a map
     * of all loaded models.
     *
     * @return a map of all loaded models
     */
    public static Future<Map<String, EntityModel>> load(Vertx vertx, Collection<EntityModelDefinition> definitions) {
        LOGGER.trace("Start loading entity model definitions");
        return new EntityModelLoader(vertx).loadModelsFromModelDirectoryAndClassPath().compose(loader -> {
            return CompositeFuture
                    .all(definitions.stream().map(loader::loadModelsFromDefinition).collect(Collectors.toList()))
                    .map(loader);
        }).map(EntityModelLoader::getModels).onComplete(result -> {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Loading entity model definitions {}", result.succeeded() ? "succeeded" : "failed");
            }
        });
    }

    /**
     * Returns a map of all loaded models.
     *
     * @return a map of all loaded models
     */
    public Map<String, EntityModel> getModels() {
        return models;
    }

    /**
     * Load models from model directory and class path.
     *
     * @return a future to the {@link EntityModelLoader} instance
     */
    public Future<EntityModelLoader> loadModelsFromModelDirectoryAndClassPath() {
        NeonBeeOptions options = NeonBee.get(vertx).getOptions();
        return CompositeFuture.all(scanDir(options.getModelsDirectory()),
                options.shouldIgnoreClassPath() ? succeededFuture() : scanClassPath()).map(this);
    }

    /**
     * Load models from payloads provided as maps of models and extension models.
     *
     * @param definition the definition to load the models from
     * @return a future to the {@link EntityModelLoader} instance
     */
    public Future<EntityModelLoader> loadModelsFromDefinition(EntityModelDefinition definition) {
        Map<String, byte[]> csnModelDefinitions = definition.getCSNModelDefinitions();
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Load models from definition {}", csnModelDefinitions.keySet());
        }
        return allComposite(csnModelDefinitions.entrySet().stream()
                .map(entry -> parseModel(entry.getKey(), entry.getValue(), definition.getAssociatedModelDefinitions()))
                .collect(Collectors.toList())).map(this).onComplete(result -> {
                    if (LOGGER.isTraceEnabled()) {
                        LOGGER.trace("Loading models from definition {} {}", csnModelDefinitions.keySet(),
                                result.succeeded() ? "succeeded" : "failed");
                    }
                });
    }

    /**
     * Tries to read / load all model files from a given directory recursively.
     *
     * @param path The path to the directory to load the files from
     * @return A succeeded future of Void, in case reading the model was a success
     */
    @VisibleForTesting
    Future<Void> scanDir(Path path) {
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Scanning directory {}", path);
        }
        return FileSystemHelper.readDir(vertx, path).recover(throwable -> {
            // ignore if the models directory does not exist, or a file / folder was deleted after readDir
            return ((throwable instanceof FileSystemException) && throwable.getMessage().contains("Does not exist"))
                    ? succeededFuture(Collections.emptyList())
                    : failedFuture(throwable);
        }).compose(
                files -> CompositeFuture.all(files.stream()
                        .map(file -> FileSystemHelper.isDirectory(vertx, file)
                                .compose(isDir -> isDir ? scanDir(file) : loadModel(file)))
                        .collect(Collectors.toList())))
                .mapEmpty();
    }

    /**
     * Tries to read / load all model files from the class path.
     */
    @VisibleForTesting
    Future<Void> scanClassPath() {
        LOGGER.trace("Scanning class path");
        ClassPathScanner scanner = new ClassPathScanner();
        Future<List<String>> csnFiles = scanner.scanWithPredicate(vertx, name -> name.endsWith(CSN));
        Future<List<String>> modelFiles = scanner.scanManifestFiles(vertx, NEONBEE_MODELS);

        return CompositeFuture.all(csnFiles, modelFiles).compose(scanResult -> CompositeFuture
                // use distinct because models mentioned in the manifest could also exists as file.
                .all(Streams.concat(csnFiles.result().stream(), modelFiles.result().stream()).distinct()
                        .map(name -> loadModel(Path.of(name)).otherwise(throwable -> {
                            // models loaded from the class path are non-vital for NeonBee so continue anyways
                            LOGGER.warn("Loading model {} from class path failed", throwable, name);
                            return null;
                        })).collect(Collectors.toList())))
                .mapEmpty();
    }

    Future<Void> loadModel(Path csnFile) {
        if (!MODELS_PATH_MATCHER.matches(csnFile)) {
            return succeededFuture();
        }

        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Loading model {}", csnFile);
        }
        return readCsnModel(csnFile).compose(cdsModel -> {
            return CompositeFuture.all(EntityModelDefinition.resolveEdmxPaths(csnFile, cdsModel).stream()
                    .map(this::loadEdmxModel).collect(Collectors.toList())).onSuccess(compositeFuture -> {
                        buildModelMap(cdsModel, compositeFuture.<ServiceMetadata>list());
                    });
        }).mapEmpty();
    }

    Future<Void> parseModel(String csnFile, byte[] csnPayload, Map<String, byte[]> associatedModels) {
        LOGGER.trace("Parse CSN model file {}", csnFile);
        return parseCsnModel(csnPayload).onComplete(result -> {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Parsing CSN model file {} {}", csnFile, result.succeeded() ? "succeeded" : "failed");
            }
        }).compose(cdsModel -> {
            LOGGER.trace("Parse associated models of {}", csnFile);
            return CompositeFuture.all(EntityModelDefinition.resolveEdmxPaths(Path.of(csnFile), cdsModel).stream()
                    .map(Path::toString).map(path -> {
                        // we do not know if the path uses windows / unix path separators, try both!
                        return FileSystemHelper.getPathFromMap(associatedModels, path);
                    }).map(this::parseEdmxModel).collect(Collectors.toList())).onComplete(result -> {
                        if (LOGGER.isTraceEnabled()) {
                            LOGGER.trace("Parsing associated models of {} {}", csnFile,
                                    result.succeeded() ? "succeeded" : "failed");
                        }
                    }).onSuccess(compositeFuture -> {
                        buildModelMap(cdsModel, compositeFuture.<ServiceMetadata>list());
                    }).mapEmpty();
        });
    }

    private void buildModelMap(CdsModel cdsModel, List<ServiceMetadata> edmxModels) {
        String namespace = EntityModelDefinition.getNamespace(cdsModel);

        if (namespace == null) {
            if (LOGGER.isWarnEnabled()) {
                LOGGER.warn(
                        "Could not determine namespace of CDS model. Model (with entities {}) will not added to the model map."
                                + " Was any service defined in the CDS model?",
                        cdsModel.entities().map(CdsEntity::getName).collect(Collectors.joining(", ")));
            }
            return;
        } else {
            LOGGER.trace("Building model map for namespace {}", namespace);
        }

        Map<String, ServiceMetadata> edmxMap = edmxModels.stream()
                .collect(Collectors.toMap(EntityModelLoader::getSchemaNamespace, Function.identity()));
        if (models.put(namespace, EntityModel.of(cdsModel, edmxMap)) != null) {
            LOGGER.warn("Model with schema namespace {} replaced an existing model in the model map", namespace);
        } else {
            LOGGER.info("Model with schema namespace {} was added the model map", namespace);
        }
    }

    /**
     * Loads the CSN model located at a path.
     *
     * @param file path of the CSN model
     * @return a {@link Future} with loaded model inside
     */
    @VisibleForTesting
    Future<CdsModel> readCsnModel(Path file) {
        return FileSystemHelper.readFile(vertx, file).map(Buffer::getBytes).compose(this::parseCsnModel);
    }

    /**
     * Loads the CSN model from byte array.
     *
     * @param csnModel payload of a CSN model
     * @return a {@link Future} with loaded model inside
     */
    @VisibleForTesting
    Future<CdsModel> parseCsnModel(byte[] csnModel) {
        return AsyncHelper.executeBlocking(vertx, () -> {
            try (InputStream inputStream = new ByteArrayInputStream(csnModel)) {
                return CdsModel.read(inputStream);
            }
        });
    }

    /**
     * Loads the EDMX model located at a path.
     *
     * @param file path of the EDMX model
     * @return a future with loaded model inside
     */
    Future<ServiceMetadata> loadEdmxModel(Path file) {
        return FileSystemHelper.readFile(vertx, file).compose(this::createServiceMetadataWithSchema);
    }

    /**
     * Loads the EDMX model from byte array.
     *
     * @param payload path of the EDMX model
     * @return a future with loaded model inside
     */
    Future<ServiceMetadata> parseEdmxModel(byte[] payload) {
        return succeededFuture(Buffer.buffer(payload)).compose(this::createServiceMetadataWithSchema);
    }

    private Future<ServiceMetadata> createServiceMetadataWithSchema(Buffer csdl) {
        return AsyncHelper.executeBlocking(vertx, () -> {
            // Get the service metadata first w/o the schema namespace, because we have to read it
            return createServiceMetadataWithSchema(csdl, getSchemaNamespace(createServiceMetadata(csdl)));
        });
    }

    /**
     * Transform the EDMX file which contains the XML representation of the OData Common Schema Definition Language
     * (CSDL) to a ServiceMetadata instance.
     *
     * ATTENTION: This method contains BLOCKING code and thus should only be called in a Vert.x worker thread!
     *
     * @param csdl            the String representation of the EDMX file's content
     * @param schemaNamespace the schema namespace of the service in the xsdl
     * @return a {@link ServiceMetadata} instance of the EDMX file which contains the XML representation of the CSDL to
     *         a ServiceMetadata instance
     */
    private ServiceMetadata createServiceMetadataWithSchema(Buffer csdl, String schemaNamespace)
            throws XMLStreamException {
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

            return getBufferedOData().createServiceMetadata(provider, Collections.emptyList(),
                    new MetadataETagSupport(csdl));
        }
    }

    /*
     * Get the service metadata of the bufferedOData instance w/o the schema namespace. This can be used e.g. in the
     * case if it is the initial loading and the schema namespace is unknown. Then the unknown schema namespace can be
     * fetched from the returned ServiceMetadata instance.
     *
     * ATTENTION: This method contains BLOCKING code and thus should only be called in a Vert.x worker thread!
     */
    @VisibleForTesting
    static ServiceMetadata createServiceMetadata(Buffer csdl) throws XMLStreamException {
        InputStreamReader reader = new InputStreamReader(new BufferInputStream(csdl), UTF_8);
        SchemaBasedEdmProvider edmProvider = new MetadataParser().referenceResolver(null).buildEdmProvider(reader);
        return getBufferedOData().createServiceMetadata(edmProvider, Collections.emptyList());
    }

    @VisibleForTesting
    static String getSchemaNamespace(ServiceMetadata serviceMetadata) {
        // a schema without an entity container is still an valid EDMX, an EDMX without any schema is not, thus try to
        // determine the namespace of the schema containing the entity container first or fall back to use any schema
        // associated with the EDMX
        Edm edm = serviceMetadata.getEdm();
        EdmEntityContainer entityCollection = edm.getEntityContainer();
        return entityCollection != null ? entityCollection.getNamespace() : edm.getSchemas().get(0).getNamespace();
    }

    @VisibleForTesting
    static class MetadataETagSupport implements ServiceMetadataETagSupport {
        private final String metadataETag;

        private final String serviceDocumentETag;

        @SuppressWarnings("checkstyle:MissingJavadocMethod") // don't know exactly what this is for
        MetadataETagSupport(Buffer csdl) {
            /*
             * Please note: ETag for the service document and the metadata document. The same field for service-document
             * and metadata-document ETag is used. It must change whenever the corresponding document changes.
             */
            this.metadataETag = this.serviceDocumentETag = generateMetadataETag(csdl);
        }

        @Override
        public String getMetadataETag() {
            return metadataETag;
        }

        @Override
        public String getServiceDocumentETag() {
            return serviceDocumentETag;
        }

        /**
         * This method generates an ETag string as defined in RFC2616/RFC7232 based on the provided EDMX file content
         * contains the XML representation of the OData Common Schema Definition Language (CSDL).
         *
         * @return ETag string
         */
        private static String generateMetadataETag(Buffer csdl) {
            return "\"" + HASH_FUNCTION.newHasher().putUnencodedChars(csdl.toString()).hash().toString() + "\"";
        }
    }
}
