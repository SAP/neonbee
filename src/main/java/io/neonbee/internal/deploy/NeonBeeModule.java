package io.neonbee.internal.deploy;

import static io.neonbee.internal.Helper.allComposite;
import static io.neonbee.internal.Helper.joinComposite;
import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.io.ByteStreams;

import io.neonbee.NeonBee;
import io.neonbee.entity.EntityModelManager;
import io.neonbee.internal.SelfFirstClassLoader;
import io.neonbee.internal.scanner.ClassPathScanner;
import io.neonbee.logging.LoggingFacade;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

public class NeonBeeModule {
    /**
     * The jar file manifest descriptor for the deployables.
     */
    public static final String NEONBEE_DEPLOYABLES = "NeonBee-Deployables";

    @VisibleForTesting
    @SuppressWarnings("checkstyle:JavadocVariable")
    public static final String NEONBEE_MODULE = "NeonBee-Module";

    @VisibleForTesting
    @SuppressWarnings("checkstyle:JavadocVariable")
    public static final String NEONBEE_HOOKS = "NeonBee-Hooks";

    @VisibleForTesting
    @SuppressWarnings("checkstyle:JavadocVariable")
    public static final String NEONBEE_MODELS = "NeonBee-Models";

    @VisibleForTesting
    @SuppressWarnings("checkstyle:JavadocVariable")
    public static final String NEONBEE_MODEL_EXTENSIONS = "NeonBee-Model-Extensions";

    private static final LoggingFacade LOGGER = LoggingFacade.create();

    @VisibleForTesting
    final List<Class<Verticle>> verticleClasses;

    @VisibleForTesting
    // CSN model payloads
    final Map<String, byte[]> models;

    @VisibleForTesting
    // Extension model payloads such EDMX
    final Map<String, byte[]> extensionModels;

    @VisibleForTesting
    List<Deployment> succeededDeployments = List.of();

    private final String identifier;

    private final Path jarPath;

    private final Vertx vertx;

    private final String correlationId;

    @VisibleForTesting
    NeonBeeModule(Vertx vertx, String identifier, String correlationId, Path jarPath,
            List<Class<Verticle>> verticleClasses, Map<String, byte[]> models, Map<String, byte[]> extensionModels) {
        this.vertx = vertx;
        this.identifier = identifier;
        this.correlationId = correlationId;
        this.jarPath = jarPath;
        this.verticleClasses = verticleClasses;
        this.models = models;
        this.extensionModels = extensionModels;
    }

    /**
     * Returns the correlation id which was created when this module became deployed.
     *
     * @return the correlation id
     */
    public String getCorrelationId() {
        return correlationId;
    }

    /**
     * Unique identifier of the module which has following strucutre: module name : module version.
     *
     * @return the identifier
     */
    public String getIdentifier() {
        return identifier;
    }

    /**
     * If something fails, it performs a cleanup (undeploy) but still return a failed future to show that the deployment
     * has failed.
     *
     * @return A succeeded future if the deployment of the NeonBeeModule is successful. Returns a failed future
     *         otherwise.
     */
    public Future<Void> deploy() {
        if (LOGGER.isInfoEnabled()) {
            getCorrelatedLogger().info("Start to deploy NeonBeeModule from JAR file: {}", jarPath.toAbsolutePath());

            String deployablesString = verticleClasses.stream().map(Class::getName).collect(Collectors.joining(","));
            getCorrelatedLogger().info("NeonBee deployables found: {}", deployablesString);

            if (models.isEmpty()) {
                getCorrelatedLogger().info("No NeonBee models found");
            } else {
                String modelsString = String.join(",", new ArrayList<>(models.keySet()));
                getCorrelatedLogger().info("NeonBee models found: {}", modelsString);
            }
        }
        return deployModels().compose(v -> transformVerticlesToDeployables()).compose(this::deployDeployments)
                .compose(v -> {
                    getCorrelatedLogger().info("Successfully deployed NeonBeeModule from JAR file: {})",
                            jarPath.toAbsolutePath());
                    return Future.<Void>succeededFuture();
                }).recover(t -> {
                    getCorrelatedLogger().error("Unexpected error occurred during deployment", t);
                    // Perform a clean up in case that something went wrong
                    getCorrelatedLogger().info("Start to clean up failed deployment");
                    return undeploy(succeededDeployments).compose(compositedUndeployments -> undeployModels())
                            .compose(v -> {
                                // Return a failed future to indicate that something went wrong, even if the clean up
                                // succeeded.
                                getCorrelatedLogger().info("Clean up succeeded");
                                return failedFuture(t);
                            });
                });
    }

    private Future<Void> deployModels() {
        if (models.isEmpty()) {
            return succeededFuture();
        } else {
            getCorrelatedLogger().info("Start to register models at EntityModelManager");
            return EntityModelManager.registerModels(vertx, identifier, models, extensionModels)
                    .map(compositeFuture -> {
                        getCorrelatedLogger().info("Finished registering models at EntityModelManager.");
                        return null;
                    });
        }
    }

    @VisibleForTesting
    Future<List<Deployable>> transformVerticlesToDeployables() {
        List<Future<Deployable>> deployableFutures = verticleClasses.stream()
                .map(verticleClass -> Deployable.fromClass(vertx, verticleClass, getCorrelationId(), new JsonObject()))
                .collect(Collectors.toList());

        return allComposite(deployableFutures).compose(compositeFuture -> succeededFuture(compositeFuture.list()
                .stream().map(deployable -> (Deployable) deployable).collect(Collectors.toList())));
    }

    private Future<Void> deployDeployments(List<Deployable> deployables) {
        return joinComposite(deployables.stream().map(deployable -> {
            try {
                return deployable.deploy(vertx, getCorrelationId()).future();
            } catch (Exception e) {
                return failedFuture(e);
            }
        }).collect(Collectors.toList())).compose(compositedDeployments -> {
            // If a deployment failed, the list contains an entry with null
            succeededDeployments = compositedDeployments.<Deployment>list().stream().filter(Objects::nonNull)
                    .collect(Collectors.toList());
            if (compositedDeployments.failed()) {
                // Some Verticles weren't deployed successfully. -> Undeploy all successfully deployed Verticles
                getCorrelatedLogger().error("Not all deployables of jar file ({}) deployed successfully",
                        jarPath.toAbsolutePath());
                return failedFuture(compositedDeployments.cause());
            } else {
                return succeededFuture();
            }
        });
    }

    /**
     * Undeploys the related NeonBeeModule.
     *
     * @return A succeeded future if the undeploy of the NeonBeeModule was successful. Returns a failed future
     *         otherwise.
     */
    public Future<Void> undeploy() {
        getCorrelatedLogger().info("Start to undeploy NeonBeeModule from JAR file: {}", jarPath.toAbsolutePath());
        return undeployModels().compose(v -> undeploy(succeededDeployments)).compose(compositedUndeployments -> {
            getCorrelatedLogger().info("Finished undeployment of NeonBeeModule from JAR file: {}",
                    jarPath.toAbsolutePath());
            if (compositedUndeployments.succeeded()) {
                return succeededFuture();
            } else {
                return failedFuture(compositedUndeployments.cause());
            }
        });
    }

    @SuppressWarnings("PMD.UseProperClassLoader")
    private Future<CompositeFuture> undeploy(List<Deployment> deployments) {
        return joinComposite(deployments.stream().map(Deployment::undeploy).collect(Collectors.toList()))
                .compose(compositedUndeployments -> {
                    if (!verticleClasses.isEmpty()) {
                        // If verticleClasses is empty the module has no deployable classes and not class loader was
                        // created.
                        try {
                            URLClassLoader cl = (URLClassLoader) verticleClasses.get(0).getClassLoader();
                            cl.close();
                        } catch (IOException e) {
                            return failedFuture(e);
                        }
                    }
                    return succeededFuture(compositedUndeployments);
                }).recover(t -> {
                    getCorrelatedLogger().error("Unexpected error occurred during undeploy", t);
                    return failedFuture(t);
                });
    }

    private Future<Void> undeployModels() {
        EntityModelManager.unregisterModels(vertx, identifier);
        return succeededFuture(null);
    }

    /**
     * This method parses a JAR from a given path and returns a NeonBeeModule representing the passed JAR. <br>
     * This method is composed of the following logical steps:
     * <ol>
     * <li>Extract all classes referenced in manifest attribute <i>NeonBee-Deployables</i>
     * <li>Extract all EDMX models referenced in manifest attribute <i>NeonBee-Models</i>
     * <li>Transforms extracted classes into {@link Deployable}s
     * <li>Create and returns new NeonBeeModule containing the extracted EDMX models and Deployable
     * </ol>
     *
     * @param vertx         The Vert.x instance
     * @param pathOfJar     The {@link Path} to the jar file.
     * @param correlationId The correlationId to correlate log messages
     * @return a Future containing the NeonBeeModule
     */
    public static Future<NeonBeeModule> fromJar(Vertx vertx, Path pathOfJar, String correlationId) {
        Promise<NeonBeeModule> jarParsingDonePromise = Promise.promise();
        // Parsing a JAR and extracting the content takes a lot of time and IO, therefore the logic is executed on the
        // worker pool.
        vertx.executeBlocking(promise -> {
            List<Class<Verticle>> verticleClasses = new ArrayList<>();
            try {
                if (!Files.exists(pathOfJar)) {
                    promise.fail(new IOException("JAR path does not exist: " + pathOfJar.toString()));
                }
                URL[] jarUrl = { pathOfJar.toUri().toURL() };

                // To ensure that ClassPathScanner finds only models from this JAR manifest, a ClassLoader which only
                // contains this JAR is needed.
                try (URLClassLoader classLoader = new URLClassLoader(jarUrl, null)) {
                    ClassPathScanner cps = new ClassPathScanner(classLoader);
                    String moduleName = cps.retrieveManifestAttribute(NEONBEE_MODULE);
                    Map<String, byte[]> models = loadModelPayloads(classLoader, cps.scanManifestFiles(NEONBEE_MODELS));
                    Map<String, byte[]> extensionModels =
                            loadModelPayloads(classLoader, cps.scanManifestFiles(NEONBEE_MODEL_EXTENSIONS));
                    SelfFirstClassLoader moduleClassLoader =
                            new SelfFirstClassLoader(jarUrl, ClassLoader.getSystemClassLoader(),
                                    NeonBee.instance(vertx).getConfig().getPlatformClasses());
                    verticleClasses.addAll(loadClassesToDeploy(cps, moduleClassLoader));
                    promise.complete(new NeonBeeModule(vertx, moduleName, correlationId, pathOfJar, verticleClasses,
                            models, extensionModels));
                }
            } catch (RuntimeException | IOException | ClassNotFoundException | LinkageError e) {
                promise.fail(e);
            }
        }, jarParsingDonePromise);

        return jarParsingDonePromise.future();
    }

    @VisibleForTesting
    static Map<String, byte[]> loadModelPayloads(URLClassLoader classLoader, List<String> modelPaths)
            throws IOException {
        Map<String, byte[]> modelData = new HashMap<>(modelPaths.size());
        for (String modelPath : modelPaths) {
            InputStream in = Objects.requireNonNull(classLoader.getResourceAsStream(modelPath),
                    "Specified model path wasn't found in NeonBee module");
            modelData.put(modelPath, ByteStreams.toByteArray(in));
        }
        return modelData;
    }

    @VisibleForTesting
    static List<Class<Verticle>> loadClassesToDeploy(ClassPathScanner cps, SelfFirstClassLoader moduleClassLoader)
            throws IOException, ClassNotFoundException {
        List<String> verticleIdentifiers = cps.scanManifestFiles(NEONBEE_DEPLOYABLES);
        List<Class<Verticle>> verticleClasses = new ArrayList<>(verticleIdentifiers.size());
        // To load the verticle for which an identifier was found, a ClassLoader which also has access
        // to e.g. Vert.x core classes is needed.
        for (String identifier : verticleIdentifiers) {
            @SuppressWarnings("unchecked")
            Class<Verticle> verticleClass = (Class<Verticle>) moduleClassLoader.loadClass(identifier);
            // TODO: Check if verticleClass is instance of DataVerticle or JobVerticle
            // This check requires a huge change in the test base, because all the verticle which are
            // dynamically generated during tests are not instance of DataVerticle or JobVerticle.
            verticleClasses.add(verticleClass);
        }

        return verticleClasses;
    }

    private LoggingFacade getCorrelatedLogger() {
        return LOGGER.correlateWith(correlationId);
    }
}
