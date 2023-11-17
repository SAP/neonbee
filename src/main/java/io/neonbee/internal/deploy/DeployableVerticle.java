package io.neonbee.internal.deploy;

import static io.neonbee.internal.helper.ConfigHelper.readConfig;
import static io.vertx.core.Future.succeededFuture;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import com.google.common.annotations.VisibleForTesting;

import io.neonbee.NeonBee;
import io.neonbee.internal.helper.ThreadHelper;
import io.neonbee.internal.json.ImmutableJsonObject;
import io.neonbee.internal.scanner.ClassPathScanner;
import io.neonbee.logging.LoggingFacade;
import io.vertx.core.CompositeFuture;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

/**
 * A {@link DeployableVerticle} is a container which wraps all necessary information to deploy a Verticle on a NeonBee
 * instance. The necessary information are: <br>
 * <ul>
 * <li><b>identifier</b>: An identifier is the full qualified class name of the Verticle which should get deployed</li>
 * <li><b>options</b>: The {@link DeploymentOptions} of the Verticle which should get deployed</li>
 * </ul>
 */
public class DeployableVerticle extends Deployable {
    /**
     * The JAR file MANIFEST.MF descriptor for the deployable verticles.
     */
    public static final String NEONBEE_DEPLOYABLES = "NeonBee-Deployables";

    private static final LoggingFacade LOGGER = LoggingFacade.create();

    @VisibleForTesting
    final Verticle verticleInstance;

    @VisibleForTesting
    final Class<? extends Verticle> verticleClass;

    @VisibleForTesting
    final DeploymentOptions options;

    /**
     * Creates {@link DeployableVerticle DeployableVerticles} by scanning the class path of a given class-loader for
     * MANIFEST.MF files containing the {@link #NEONBEE_DEPLOYABLES} descriptor.
     *
     * @param vertx               the Vert.x instance used to scan the class path
     * @param jarPath             the path to a JAR to scan
     * @param verticleClassLoader the class loader instance to load the found verticle classes with
     * @return a {@link DeployableModels}
     */
    public static Future<Collection<DeployableVerticle>> fromJar(Vertx vertx, Path jarPath,
            ClassLoader verticleClassLoader) {
        return ClassPathScanner.forJarFile(vertx, jarPath).compose(classPathScanner -> {
            return scanClassPath(vertx, classPathScanner, verticleClassLoader)
                    .eventually(() -> classPathScanner.close(vertx));
        });
    }

    static Future<Collection<DeployableVerticle>> scanClassPath(Vertx vertx, ClassPathScanner classPathScanner,
            ClassLoader verticleClassLoader) {
        return classPathScanner.scanManifestFiles(vertx, NEONBEE_DEPLOYABLES).compose(classNames -> {
            List<Future<DeployableVerticle>> deployables =
                    classNames.stream().map(className -> fromClassName(vertx, className, verticleClassLoader))
                            .collect(Collectors.toList());
            return Future.all(deployables).map(CompositeFuture::list);
        });
    }

    /**
     * Creates a {@link DeployableVerticle} from any valid identifier which is available in the system class loader.
     *
     * @see Vertx#deployVerticle(String)
     * @param vertx     the Vert.x instance used to read the configuration
     * @param className the identifier of the verticle
     * @return a {@link DeployableVerticle}
     */
    public static Future<DeployableVerticle> fromClassName(Vertx vertx, String className) {
        return fromClassName(vertx, className, (JsonObject) null);
    }

    /**
     * Creates a {@link DeployableVerticle} from any valid identifier which is available in the system class loader.
     *
     * @see Vertx#deployVerticle(String)
     * @param vertx         the Vert.x instance used to read the configuration
     * @param className     the identifier of the verticle
     * @param defaultConfig the default deployment configuration to apply when loading this verticle, or null
     * @return a {@link DeployableVerticle}
     */
    public static Future<DeployableVerticle> fromClassName(Vertx vertx, String className, JsonObject defaultConfig) {
        return fromClassName(vertx, className, ThreadHelper.getClassLoader(), defaultConfig); // NOPMD false positive
    }

    /**
     * Creates a {@link DeployableVerticle} from any valid identifier which is available in the system class loader.
     *
     * @see Vertx#deployVerticle(String)
     * @param vertx       the Vert.x instance used to read the configuration
     * @param className   the identifier of the verticle
     * @param classLoader the class loader to load the class from
     * @return a {@link DeployableVerticle}
     */
    public static Future<DeployableVerticle> fromClassName(Vertx vertx, String className, ClassLoader classLoader) {
        return fromClassName(vertx, className, classLoader, null);
    }

    /**
     * Creates a {@link DeployableVerticle} from any valid identifier which is available in the system class loader.
     *
     * @see Vertx#deployVerticle(String)
     * @param vertx         the Vert.x instance used to read the configuration
     * @param className     the identifier of the verticle
     * @param classLoader   the class loader to load the class from
     * @param defaultConfig the default deployment configuration to apply when loading this verticle, or null
     * @return a {@link DeployableVerticle}
     */
    @SuppressWarnings("unchecked")
    public static Future<DeployableVerticle> fromClassName(Vertx vertx, String className, ClassLoader classLoader,
            JsonObject defaultConfig) {
        // to load the verticle for which an identifier was found, a ClassLoader which also has access to e.g. Vert.x
        // core classes is needed. TODO: Check if verticleClass is instance of DataVerticle or JobVerticle, this check
        // requires a huge change in the test base, because all the verticle which are dynamically generated during
        // tests are not instance of DataVerticle or JobVerticle. so we maybe should make this check optional and just
        // enabled during execution runtime execution of NeonBee but disable it for the tests
        return vertx.executeBlocking(() -> (Class<Verticle>) classLoader.loadClass(className))
                .compose(verticleClass -> fromClass(vertx, verticleClass, defaultConfig));
    }

    /**
     * Creates a {@link DeployableVerticle} from any verticle class.
     *
     * @param vertx         the Vert.x instance used to read the configuration
     * @param verticleClass the verticle class
     * @return a {@link DeployableVerticle}
     */
    public static Future<DeployableVerticle> fromClass(Vertx vertx, Class<? extends Verticle> verticleClass) {
        return fromClass(vertx, verticleClass, null);
    }

    /**
     * Creates a {@link DeployableVerticle} from any verticle class.
     *
     * @param vertx         the Vert.x instance used to read the configuration
     * @param verticleClass the verticle class
     * @param defaultConfig the default deployment configuration to apply when loading this verticle, or null
     * @return a {@link DeployableVerticle}
     */
    public static Future<DeployableVerticle> fromClass(Vertx vertx, Class<? extends Verticle> verticleClass,
            JsonObject defaultConfig) {
        return readVerticleConfig(vertx, verticleClass.getName(), defaultConfig).compose(
                deploymentOptions -> succeededFuture(new DeployableVerticle(verticleClass, deploymentOptions)));
    }

    /**
     * Creates a {@link DeployableVerticle} from an already instantiated verticle.
     *
     * @param vertx            the Vert.x instance used to read the configuration
     * @param verticleInstance the verticle instance
     * @return a {@link DeployableVerticle}
     */
    public static Future<DeployableVerticle> fromVerticle(Vertx vertx, Verticle verticleInstance) {
        return fromVerticle(vertx, verticleInstance, null);
    }

    /**
     * Creates a {@link DeployableVerticle} from an already instantiated verticle.
     *
     * @param vertx            the Vert.x instance used to read the configuration
     * @param verticleInstance the verticle instance
     * @param defaultConfig    the default deployment configuration to apply when loading this verticle, or null
     * @return a {@link DeployableVerticle}
     */
    public static Future<DeployableVerticle> fromVerticle(Vertx vertx, Verticle verticleInstance,
            JsonObject defaultConfig) {
        return readVerticleConfig(vertx, verticleInstance.getClass().getName(), defaultConfig)
                .compose(options -> succeededFuture(new DeployableVerticle(verticleInstance, options)));
    }

    /**
     * Create a new {@link DeployableVerticle} with a verticle class.
     *
     * @param verticleClass The verticle class
     * @param options       The deployment options
     */
    public DeployableVerticle(Class<? extends Verticle> verticleClass, DeploymentOptions options) {
        super();
        this.verticleInstance = null; // NOPMD
        this.verticleClass = verticleClass;
        this.options = options;
    }

    /**
     * Create a new {@link DeployableVerticle} with a verticle instance.
     *
     * @param verticleInstance The verticle instance
     * @param options          The deployment options
     */
    public DeployableVerticle(Verticle verticleInstance, DeploymentOptions options) {
        super();
        this.verticleInstance = verticleInstance;
        this.verticleClass = null; // NOPMD
        this.options = options;
    }

    @Override
    public String getIdentifier() {
        if (verticleClass != null) {
            return verticleClass.getName();
        } else {
            return verticleInstance.getClass().getName();
        }
    }

    @Override
    public PendingDeployment deploy(NeonBee neonBee) {
        Vertx vertx = neonBee.getVertx();
        return new PendingDeployment(neonBee, this,
                verticleInstance != null ? vertx.deployVerticle(verticleInstance, options)
                        : vertx.deployVerticle(verticleClass, options)) {
            @Override
            protected Future<Void> undeploy(String deploymentId) {
                return vertx.undeploy(deploymentId);
            }
        };
    }

    /**
     * Reads a verticle config from the /config named identifier.* directory in the current working directory.
     * <p>
     * First tries to read a YAML configuration file, if it fails falls back to JSON.
     *
     * @param vertx         the Vert.x instance to read the config files with
     * @param className     the verticle identifier to read the config file for
     * @param defaultConfig the default configuration when reading this verticle
     * @return a new deployment options instance either with the configuration, or with
     */
    @VisibleForTesting
    @SuppressWarnings("ReferenceEquality")
    static Future<DeploymentOptions> readVerticleConfig(Vertx vertx, String className, JsonObject defaultConfig) {
        return readConfig(vertx, className, defaultConfig != null ? defaultConfig : ImmutableJsonObject.EMPTY)
                .map(config -> {
                    // merge the config into the default config two levels deep (verticle settings & config first level)
                    return defaultConfig != null && config != defaultConfig ? defaultConfig.copy().mergeIn(config, 2)
                            : config; // if there is no default config, or what we read was the default config return it
                }).onFailure(throwable -> {
                    LOGGER.warn("Could not read deployment options for deployable {}", className, throwable);
                }).map(DeploymentOptions::new);
    }
}
