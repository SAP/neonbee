package io.neonbee.internal.scanner;

import static io.neonbee.internal.scanner.ClassPathScanner.getClassLoader;

import java.util.List;
import java.util.stream.Collectors;

import com.google.common.collect.Streams;

import io.neonbee.NeonBeeDeployable;
import io.neonbee.internal.deploy.NeonBeeModule;
import io.neonbee.logging.LoggingFacade;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;

public class DeployableScanner {
    private static final LoggingFacade LOGGER = LoggingFacade.create();

    /**
     * Scan for classes that are potential NeonBeeDeployables on the class path of the current context class loader.
     *
     * Jars on the class path will be ignored, except they are NeonBeeModules.
     *
     * @param vertx the Vert.x instance
     * @return a future to a list with the classes
     */
    public static Future<List<Class<? extends Verticle>>> scanForDeployableClasses(Vertx vertx) {
        return scanForDeployableClasses(vertx, getClassLoader());
    }

    private static Future<List<Class<? extends Verticle>>> scanForDeployableClasses(Vertx vertx,
            ClassLoader classLoader) {
        ClassPathScanner scanner = new ClassPathScanner();
        Future<List<String>> deployablesFromClassPath = scanner.scanForAnnotation(vertx, NeonBeeDeployable.class);
        Future<List<String>> deployablesFromManifest =
                scanner.scanManifestFiles(vertx, NeonBeeModule.NEONBEE_DEPLOYABLES);

        return CompositeFuture.all(deployablesFromClassPath, deployablesFromManifest)
                .compose(compositeResult -> vertx.executeBlocking(promise -> {
                    // Use distinct because the Deployables mentioned in the manifest could also exists as file.
                    List<String> deployableFQNs = Streams.concat(deployablesFromClassPath.result().stream(),
                            deployablesFromManifest.result().stream()).distinct().collect(Collectors.toList());

                    LOGGER.info("Found Deployables {}.", deployableFQNs.stream().collect(Collectors.joining(",")));
                    promise.complete(deployableFQNs.stream().map(className -> {
                        try {
                            return (Class<? extends Verticle>) classLoader.loadClass(className)
                                    .asSubclass(Verticle.class);
                        } catch (ClassCastException e) {
                            throw new IllegalStateException("Deployables must subclass " + Verticle.class.getName(), e);
                        } catch (ClassNotFoundException e) {
                            // Should never happen, since the class name is read from the class path
                            throw new IllegalStateException(e);
                        }
                    }).collect(Collectors.toList()));
                }));
    }
}
