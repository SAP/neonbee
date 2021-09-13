package io.neonbee.internal.deploy;

import static io.neonbee.internal.helper.AsyncHelper.allComposite;
import static io.neonbee.internal.helper.AsyncHelper.joinComposite;
import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.NoSuchFileException;
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
import io.neonbee.internal.helper.FileSystemHelper;
import io.neonbee.internal.scanner.ClassPathScanner;
import io.neonbee.logging.LoggingFacade;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
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

    private final URLClassLoader moduleClassLoader;

    @VisibleForTesting
    NeonBeeModule(Vertx vertx, String identifier, String correlationId, Path jarPath, URLClassLoader moduleClassLoader,
            List<Class<Verticle>> verticleClasses, Map<String, byte[]> models, Map<String, byte[]> extensionModels) {
        this.vertx = vertx;
        this.identifier = identifier;
        this.correlationId = correlationId;
        this.jarPath = jarPath;
        this.moduleClassLoader = moduleClassLoader; // could be null, in case the module has no verticles to deploy
        this.verticleClasses = verticleClasses;
        if (verticleClasses != null && !verticleClasses.isEmpty() && moduleClassLoader == null) {
            throw new IllegalStateException("Missing module class loader for provided verticle classes");
        }
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
     * Unique identifier of the module which has following structure: module name : module version.
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
                    if (moduleClassLoader != null) {
                        // if this module has a own moduleClassLoader, close it after the verticles have been undeployed
                        try {
                            moduleClassLoader.close();
                        } catch (IOException e) {
                            return failedFuture(e);
                        }
                    }
                    return succeededFuture(compositedUndeployments);
                }).onFailure(t -> {
                    getCorrelatedLogger().error("Unexpected error occurred during undeploy", t);
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
     * @return a future to the NeonBeeModule
     */
    // passing a null moduleClassLoader to the NeonBeeModule constructor is fine as it is checked inside the constructor
    @SuppressWarnings("PMD.NullAssignment")
    public static Future<NeonBeeModule> fromJar(Vertx vertx, Path pathOfJar, String correlationId) {
        return FileSystemHelper.exists(vertx, pathOfJar).compose(jarExists -> {
            if (!jarExists) {
                return failedFuture(new NoSuchFileException("JAR path does not exist: " + pathOfJar.toString()));
            }

            URL[] jarUrl;
            try {
                jarUrl = new URL[] { pathOfJar.toUri().toURL() };
            } catch (MalformedURLException e) {
                return failedFuture(e);
            }

            // To ensure that ClassPathScanner finds only models from this JAR manifest, a ClassLoader which only
            // contains this JAR is needed.
            URLClassLoader classLoader = new URLClassLoader(jarUrl, null);
            ClassPathScanner cps = new ClassPathScanner(classLoader);
            return cps.scanManifestFiles(vertx, NEONBEE_MODULE).compose(moduleNames -> {
                if (moduleNames.isEmpty()) {
                    return failedFuture("Invalid NeonBee-Module: No " + NEONBEE_MODULE + "attribute found.");
                } else if (moduleNames.size() > 1) {
                    return failedFuture("Invalid NeonBee-Module: Too many " + NEONBEE_MODULE + "attributes found.");
                }

                // load the classes to deploy using a new self-first class loader. note that the SelfFirstClassLoader
                // *stays* open, for however long this module stays either not deployed yet, or is deployed. the class
                // loader will get closed as soon as the NeonBeeModule is undeployed, as we don't know whether classes
                // loaded with this class loader might load further classes at any point in time.
                SelfFirstClassLoader moduleClassLoader = new SelfFirstClassLoader(jarUrl,
                        ClassLoader.getSystemClassLoader(), NeonBee.get(vertx).getConfig().getPlatformClasses());
                Future<List<Class<Verticle>>> verticleClassesToDeploy =
                        loadClassesToDeploy(vertx, cps, moduleClassLoader).onSuccess(verticleClasses -> {
                            // if there are no verticles to deploy, immediately close the self-first class loader
                            if (verticleClasses.isEmpty()) {
                                try {
                                    moduleClassLoader.close();
                                } catch (IOException e) {
                                    LOGGER.error("Cloud not close the modules SelfFirstClassLoader", e);
                                }
                            }
                        });

                // scan for models and model extensions, before then creating the NeonBee module object
                Future<Map<String, byte[]>> models = cps.scanManifestFiles(vertx, NEONBEE_MODELS)
                        .compose(modelNames -> loadModelPayloads(vertx, classLoader, modelNames));
                Future<Map<String, byte[]>> extensionModels = cps.scanManifestFiles(vertx, NEONBEE_MODEL_EXTENSIONS)
                        .compose(extensionModelNames -> loadModelPayloads(vertx, classLoader, extensionModelNames));

                return CompositeFuture.all(verticleClassesToDeploy, models, extensionModels)
                        .map(compositeResult -> new NeonBeeModule(vertx, moduleNames.get(0), correlationId, pathOfJar,
                                verticleClassesToDeploy.result().isEmpty() ? null : moduleClassLoader,
                                verticleClassesToDeploy.result(), models.result(), extensionModels.result()));
            }).onComplete(anyResult -> {
                // after class path scanning completed, close the classLoader
                try {
                    classLoader.close();
                } catch (IOException e) {
                    LOGGER.error("Cloud not close the URLClassLoader", e);
                }
            });
        });
    }

    @VisibleForTesting
    static Future<Map<String, byte[]>> loadModelPayloads(Vertx vertx, URLClassLoader classLoader,
            List<String> modelPaths) {
        return vertx.executeBlocking(promise -> {
            try {
                Map<String, byte[]> modelData = new HashMap<>(modelPaths.size());
                for (String modelPath : modelPaths) {
                    InputStream in = Objects.requireNonNull(classLoader.getResourceAsStream(modelPath),
                            "Specified model path wasn't found in NeonBee module");
                    modelData.put(modelPath, ByteStreams.toByteArray(in));
                }
                promise.complete(modelData);
            } catch (IOException e) {
                promise.fail(e);
            }
        });
    }

    @VisibleForTesting
    static Future<List<Class<Verticle>>> loadClassesToDeploy(Vertx vertx, ClassPathScanner cps,
            SelfFirstClassLoader moduleClassLoader) {
        return cps.scanManifestFiles(vertx, NEONBEE_DEPLOYABLES)
                .compose(verticleIdentifiers -> vertx.executeBlocking(promise -> {
                    try {
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

                        promise.complete(verticleClasses);
                    } catch (ClassNotFoundException e) {
                        promise.fail(e);
                    }
                }));
    }

    private LoggingFacade getCorrelatedLogger() {
        return LOGGER.correlateWith(correlationId);
    }
}
