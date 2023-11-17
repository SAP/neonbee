package io.neonbee.internal.deploy;

import static io.vertx.core.Future.failedFuture;
import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import io.neonbee.NeonBee;
import io.neonbee.internal.SelfFirstClassLoader;
import io.neonbee.internal.scanner.ClassPathScanner;
import io.neonbee.logging.LoggingFacade;
import io.vertx.core.Future;
import io.vertx.core.Vertx;

public class DeployableModule extends Deployables {
    /**
     * The JAR file MANIFEST.MF descriptor for modules.
     */
    public static final String NEONBEE_MODULE = "NeonBee-Module";

    private static final LoggingFacade LOGGER = LoggingFacade.create();

    @VisibleForTesting
    final String moduleName;

    @VisibleForTesting
    final URLClassLoader moduleClassLoader;

    /**
     * This method parses a JAR from a given path and returns a NeonBeeModule representing the passed JAR. <br>
     * This method is composed of the following logical steps:
     * <ol>
     * <li>Extract all classes referenced in manifest attribute <i>NeonBee-Deployables</i>
     * <li>Extract all EDMX models referenced in manifest attribute <i>NeonBee-Models</i>
     * <li>Transforms extracted classes into {@link DeployableVerticle}s
     * <li>Create and returns new NeonBeeModule containing the extracted EDMX models and Deployable
     * </ol>
     *
     * @param vertx   The Vert.x instance
     * @param jarPath The {@link Path} to the jar file.
     * @return a future to the NeonBeeModule
     */
    // passing a null moduleClassLoader to the NeonBeeModule constructor is fine as it is checked inside the constructor
    @SuppressWarnings("PMD.NullAssignment")
    public static Future<DeployableModule> fromJar(Vertx vertx, Path jarPath) {
        LOGGER.debug("Loading {} from JAR {}", DeployableModule.class, jarPath);
        return ClassPathScanner.forJarFile(vertx, jarPath).compose(classPathScanner -> {
            return classPathScanner.scanManifestFiles(vertx, NEONBEE_MODULE).compose(moduleNames -> {
                if (moduleNames.isEmpty()) {
                    return failedFuture("No " + NEONBEE_MODULE + " attribute found");
                } else if (moduleNames.size() > 1) {
                    return failedFuture("Too many " + NEONBEE_MODULE + " attributes found");
                }

                String moduleName = moduleNames.get(0);
                LOGGER.debug("Found module with name {}", moduleName);

                // load the classes to deploy using a new self-first class loader. note that the SelfFirstClassLoader
                // *stays* open, for however long this module stays either not deployed yet, or is deployed. the class
                // loader will get closed as soon as the module is undeployed, as we don't know whether classes loaded
                // with this class loader might load further classes at any point in time.
                AtomicReference<SelfFirstClassLoader> moduleClassLoader =
                        new AtomicReference<>(new SelfFirstClassLoader(classPathScanner.getClassLoader().getURLs(), // NOPMD
                                ClassLoader.getSystemClassLoader(),
                                NeonBee.get(vertx).getConfig().getPlatformClasses()));

                // note how DeployableVerticle returns a collection of single deployable verticles, while
                // DeployableModels is a single object. this decision was made on purpose to indicate how all models of
                // one module must be always deployed as a whole (because they likely belong to one joined data model),
                // whereas verticles are independent on each other (linked by the event-bus) and could thus be deployed
                // separately (even if it is not done in the module case). later on this might be useful if we only
                // deploy verticles that fit active profiles
                Future<Collection<DeployableVerticle>> deployableVerticles =
                        DeployableVerticle.scanClassPath(vertx, classPathScanner, moduleClassLoader.get());
                Future<DeployableModels> deployableModels = DeployableModels.scanClassPath(vertx, classPathScanner);

                return Future.all(deployableVerticles, deployableModels).onComplete(scanResult -> {
                    // in case scanning failed, or there are no verticles, immediately close the module class loader
                    if (scanResult.failed() || deployableVerticles.result().isEmpty()) {
                        try {
                            moduleClassLoader.getAndSet(null).close();
                        } catch (IOException e) {
                            LOGGER.error("Cloud not close the moduleClassLoader, "
                                    + "after no verticles have been found to deploy", e);
                        }
                    }
                }).map(result -> {
                    return new DeployableModule(moduleName, moduleClassLoader.get(), Lists.newArrayList(
                            Iterables.concat(List.of(deployableModels.result()), deployableVerticles.result())));
                });
            }).eventually(() -> classPathScanner.close(vertx));
        });
    }

    @VisibleForTesting
    DeployableModule(String moduleName, URLClassLoader moduleClassLoader, List<Deployable> deployables) {
        super(deployables);
        this.moduleName = requireNonNull(moduleName, "moduleName may not be null");
        this.moduleClassLoader = moduleClassLoader;
        // module class loader is fine to be null, as long as there are no verticles to deploy
        if (moduleClassLoader == null && deployables.stream().anyMatch(DeployableVerticle.class::isInstance)) {
            throw new IllegalStateException("Missing module class loader for provided deployable verticle(s)");
        }
    }

    @Override
    public String getIdentifier() {
        return moduleName;
    }

    @Override
    public List<Deployable> getDeployables() {
        // in contrast to the deployables, module contents are not modifiable
        return Collections.unmodifiableList(super.getDeployables());
    }

    @Override
    public PendingDeployment deploy(NeonBee neonBee) {
        return super.deploy(neonBee, undeployResult -> neonBee.getVertx().executeBlocking(() -> {
            // if there is a module class loader, it came from our class, because we have a package private
            // constructor, thus we also need to close it when the module is undeployed!
            if (moduleClassLoader != null) {
                moduleClassLoader.close();
            }
            return null;
        }));
    }
}
