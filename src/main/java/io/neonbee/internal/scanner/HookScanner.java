package io.neonbee.internal.scanner;

import static io.neonbee.internal.scanner.ClassPathScanner.getClassLoader;

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Streams;

import io.neonbee.hook.Hook;
import io.neonbee.hook.Hooks;
import io.neonbee.logging.LoggingFacade;

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
     * This method will scan for classes containing hook-annotations on the classpath and in jars and will return a
     * collection of classes which contains hook.
     *
     * @return the set of classes, containing hook annotations
     */
    public Collection<Class<?>> scanForHooks() {
        try {
            return scanForClassesContainingHooks();
        } catch (IOException | URISyntaxException e) {
            LOGGER.warn("An error has occurred when trying to scan for hook.", e);
            return Set.of();
        }
    }

    @VisibleForTesting
    Set<Class<?>> scanForClassesContainingHooks() throws IOException, URISyntaxException {
        ClassPathScanner scanner = new ClassPathScanner(classLoader);

        Collection<String> hookClassesWithAnnotations =
                scanner.scanForAnnotation(List.of(Hook.class, Hooks.class), ElementType.METHOD);
        LOGGER.info("Annotated hook classes on classpath {}.", String.join(",", hookClassesWithAnnotations));

        // add .class, because Hooks are only the FQN
        Collection<String> hooksFromManifest = scanner.scanManifestFiles("NeonBee-Hooks").stream()
                .map(name -> name + ".class").collect(Collectors.toList());
        LOGGER.info("Hook classes from manifest files on classpath {}.", String.join(", ", hooksFromManifest));

        // Use distinct because the hook mentioned in the manifest could also exist as file.
        return Streams.concat(hookClassesWithAnnotations.stream()).filter(Objects::nonNull).distinct()
                .map(className -> {
                    try {
                        return (Class<?>) classLoader.loadClass(className);
                    } catch (ClassNotFoundException e) {
                        // Should never happen, since the class name is read from the class path
                        throw new IllegalStateException(e);
                    }
                }).collect(Collectors.toSet());
    }
}
