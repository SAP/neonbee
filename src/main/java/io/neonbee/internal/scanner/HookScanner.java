package io.neonbee.internal.scanner;

import static io.neonbee.internal.deploy.NeonBeeModule.NEONBEE_HOOKS;
import static io.neonbee.internal.scanner.ClassPathScanner.getClassLoader;

import java.lang.annotation.ElementType;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Streams;

import io.neonbee.hook.Hook;
import io.neonbee.hook.Hooks;
import io.neonbee.logging.LoggingFacade;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;

public class HookScanner {
    private static final LoggingFacade LOGGER = LoggingFacade.create();

    private final ClassLoader classLoader;

    /**
     * Creates a new HookScanner with the current context class loader.
     */
    public HookScanner() {
        this(getClassLoader());
    }

    @VisibleForTesting
    HookScanner(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    /**
     * This method will scan for classes containing hook-annotations on the classpath and in jars and will return a set
     * of classes which contains hook.
     *
     * @param vertx the Vert.x instance
     * @return a future to a set of classes, containing hook annotations
     */
    public Future<Set<Class<?>>> scanForHooks(Vertx vertx) {
        return scanForClassesContainingHooks(vertx).otherwise(throwable -> {
            LOGGER.warn("An error has occurred when trying to scan for hook.", throwable);
            return Set.of();
        });
    }

    @VisibleForTesting
    Future<Set<Class<?>>> scanForClassesContainingHooks(Vertx vertx) {
        ClassPathScanner scanner = new ClassPathScanner(classLoader);

        Future<List<String>> hookClassesWithAnnotations =
                scanner.scanForAnnotation(vertx, List.of(Hook.class, Hooks.class), ElementType.METHOD);
        // add .class, because Hooks are only the FQN
        Future<List<String>> hooksFromManifest = scanner.scanManifestFiles(vertx, NEONBEE_HOOKS)
                .map(names -> names.stream().map(name -> name + ".class").collect(Collectors.toList()));

        return CompositeFuture.all(hookClassesWithAnnotations, hooksFromManifest)
                .compose(compositeResult -> vertx.executeBlocking(promise -> {
                    LOGGER.info("Annotated hook classes on classpath {}.",
                            String.join(",", hookClassesWithAnnotations.result()));
                    LOGGER.info("Hook classes from manifest files on classpath {}.",
                            String.join(", ", hooksFromManifest.result()));

                    // Use distinct because the hook mentioned in the manifest could also exist as file.
                    promise.complete(Streams
                            .concat(hookClassesWithAnnotations.result().stream(), hooksFromManifest.result().stream())
                            .filter(Objects::nonNull).distinct().map(className -> {
                                try {
                                    return (Class<?>) classLoader.loadClass(className);
                                } catch (ClassNotFoundException e) {
                                    // Should never happen, since the class name is read from the class path
                                    throw new IllegalStateException(e);
                                }
                            }).collect(Collectors.toSet()));
                }));
    }
}
