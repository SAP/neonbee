package io.neonbee.internal.deploy;

import static io.neonbee.internal.Helper.readConfig;
import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;

import java.nio.file.NoSuchFileException;
import java.util.Optional;

import com.google.common.annotations.VisibleForTesting;

import io.neonbee.logging.LoggingFacade;
import io.vertx.core.AsyncResult;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

/**
 * A {@link Deployable} is a container which wraps all necessary information to deploy a Verticle on a NeonBee instance.
 * The necessary information are: <br>
 * <ul>
 * <li><b>identifier</b>: An identifier is the full qualified class name of the Verticle which should get deployed</li>
 * <li><b>options</b>: The {@link DeploymentOptions} of the Verticle which should get deployed</li>
 * </ul>
 */
public class Deployable {
    private static final LoggingFacade LOGGER = LoggingFacade.create();

    @VisibleForTesting
    Verticle verticleInstance;

    private final DeploymentOptions options;

    private Class<? extends Verticle> verticleClass;

    @VisibleForTesting
    public Deployable(Class<? extends Verticle> verticleClass, DeploymentOptions options) {
        this.verticleClass = verticleClass;
        this.options = options;
    }

    @VisibleForTesting
    public Deployable(Verticle verticleInstance, DeploymentOptions options) {
        this.verticleInstance = verticleInstance;
        this.options = options;
    }

    /**
     * The identifier is the full qualified class name of the Verticle to deploy.
     *
     * @return The identifier.
     */
    public String getIdentifier() {
        if (verticleClass != null) {
            return verticleClass.getName();
        } else {
            return verticleInstance.getClass().getName();
        }
    }

    /**
     * This method deploys the Deployable on the passed Vert.x instance.
     *
     * @param vertx         The Vert.x instance on which the Deployable will be deployed.
     * @param correlationId The correlation ID to correlate log messages.
     * @return A PendingDeployment of the Deployable
     */
    public PendingDeployment deploy(Vertx vertx, String correlationId) {
        return new PendingDeployment(vertx, getIdentifier(), correlationId, Future.future(promise -> {
            LOGGER.correlateWith(correlationId).info("Start to deploy: {}", getIdentifier());
            if (verticleInstance != null) {
                vertx.deployVerticle(verticleInstance, options, getDeployHandler(correlationId, promise));
            } else {
                vertx.deployVerticle(verticleClass, options, getDeployHandler(correlationId, promise));
            }
        }));
    }

    private Handler<AsyncResult<String>> getDeployHandler(String correlationId, Promise<String> deployPromise) {
        return deployResult -> {
            if (deployResult.failed()) {
                LOGGER.correlateWith(correlationId).error("Deployment of {} failed", getIdentifier(),
                        deployResult.cause());
                deployPromise.fail(deployResult.cause());
            } else {
                LOGGER.correlateWith(correlationId).info("Deployment of {} succeeded", getIdentifier());
                deployPromise.complete(deployResult.result());
            }
        };
    }

    /**
     * Creates a NeonBee deployable from any valid identifier which is available in the system class loader.
     *
     * @see Vertx#deployVerticle(String)
     * @param vertx         the Vert.x instance used to read the configuration
     * @param identifier    the identifier of the verticle
     * @param classLoader   the class loader to load the class from
     * @param correlationId An ID to correlate log messages
     * @param defaultConfig the default deployment configuration to apply when loading this verticle, or null
     * @return a NeonBee deployable
     */
    @SuppressWarnings("unchecked")
    public static Future<Deployable> fromIdentifier(Vertx vertx, String identifier, ClassLoader classLoader,
            String correlationId, JsonObject defaultConfig) {
        try {
            Class<Verticle> verticleClass = (Class<Verticle>) classLoader.loadClass(identifier);
            return fromClass(vertx, verticleClass, correlationId, defaultConfig);
        } catch (ClassNotFoundException e) {
            return failedFuture(e);
        }
    }

    /**
     * Creates a NeonBee deployable from any verticle class.
     *
     * @param vertx         the Vert.x instance used to read the configuration
     * @param verticleClass the verticle class
     * @param correlationId An ID to correlate log messages
     * @param defaultConfig the default deployment configuration to apply when loading this verticle, or null
     * @return a NeonBee deployable
     */
    public static Future<Deployable> fromClass(Vertx vertx, Class<? extends Verticle> verticleClass,
            String correlationId, JsonObject defaultConfig) {
        return readVerticleConfig(vertx, verticleClass.getName(), correlationId, defaultConfig)
                .compose(deploymentOptions -> succeededFuture(new Deployable(verticleClass, deploymentOptions)));
    }

    /**
     * Creates a NeonBee deployable from an already instantiated verticle.
     *
     * @param vertx            the Vert.x instance used to read the configuration
     * @param verticleInstance the verticle instance
     * @param correlationId    An ID to correlate log messages
     * @param defaultConfig    the default deployment configuration to apply when loading this verticle, or null
     * @return a NeonBee deployable
     */
    public static Future<Deployable> fromVerticle(Vertx vertx, Verticle verticleInstance, String correlationId,
            JsonObject defaultConfig) {
        return readVerticleConfig(vertx, verticleInstance.getClass().getName(), correlationId, defaultConfig)
                .compose(deploymentOptions -> succeededFuture(new Deployable(verticleInstance, deploymentOptions)));
    }

    /**
     * Reads a verticle config from the /config named identifier.* directory in the current working directory.
     * <p>
     * First tries to read a YAML configuration file, if it fails falls back to JSON.
     *
     * @param vertx         the Vert.x instance to read the config files with
     * @param identifier    the verticle identifier to read the config file for
     * @param correlationId An ID to correlate log messages
     * @param defaultConfig the default configuration when reading this verticle
     * @return a new deployment options instance either with the configuration, or with
     */
    // Visible For Testing
    static Future<DeploymentOptions> readVerticleConfig(Vertx vertx, String identifier, String correlationId,
            JsonObject defaultConfig) {
        return Future.<JsonObject>future(configHandler -> readConfig(vertx, identifier, configHandler))
                // merge the config into the default config two levels deep (verticle settings & config first level)
                .map(config -> new DeploymentOptions(
                        Optional.ofNullable(defaultConfig).orElseGet(JsonObject::new).mergeIn(config, 2)))
                .recover(throwable -> {
                    if (throwable.getCause() instanceof NoSuchFileException) {
                        return succeededFuture(
                                new DeploymentOptions(defaultConfig != null ? defaultConfig : new JsonObject()));
                    } else {
                        LOGGER.correlateWith(correlationId).warn("Could not read deployment options for deployable {}",
                                identifier, throwable);
                        return failedFuture(throwable);
                    }
                });
    }
}
