package io.neonbee.internal.scanner;

import static io.neonbee.internal.Helper.getClassLoader;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.stream.Collectors;

import com.google.common.collect.Streams;

import io.neonbee.NeonBeeDeployable;
import io.neonbee.internal.deploy.NeonBeeModule;
import io.neonbee.logging.LoggingFacade;
import io.vertx.core.Verticle;

public class DeployableScanner {
    private static final LoggingFacade LOGGER = LoggingFacade.create();

    /**
     * Scan for classes that are potential NeonBeeDeployables on the class path of the current context class loader.
     *
     * Jars on the class path will be ignored, except they are NeonBeeModules.
     *
     * @return a list with the classes
     * @throws IOException        in case something went wrong during the manifest file scans
     * @throws URISyntaxException in case something went wrong during the annotation scan
     */
    public static List<Class<? extends Verticle>> scanForDeployableClasses() throws IOException, URISyntaxException {
        return scanForDeployableClasses(getClassLoader());
    }

    private static List<Class<? extends Verticle>> scanForDeployableClasses(ClassLoader classLoader)
            throws IOException, URISyntaxException {
        ClassPathScanner scanner = new ClassPathScanner();
        List<String> deployablesFromClassPath = scanner.scanForAnnotation(NeonBeeDeployable.class);
        List<String> deployablesFromManifest = scanner.scanManifestFiles(NeonBeeModule.NEONBEE_DEPLOYABLES);

        // Use distinct because the Deployables mentioned in the manifest could also exists as file.
        List<String> deployableFQNs =
                Streams.concat(deployablesFromClassPath.stream(), deployablesFromManifest.stream()).distinct()
                        .collect(Collectors.toList());

        LOGGER.info("Found Deployables {}.", deployableFQNs.stream().collect(Collectors.joining(",")));
        return deployableFQNs.stream().map(className -> {
            try {
                return (Class<? extends Verticle>) classLoader.loadClass(className).asSubclass(Verticle.class);
            } catch (ClassCastException e) {
                throw new IllegalStateException("Deployables must subclass " + Verticle.class.getName(), e);
            } catch (ClassNotFoundException e) {
                // Should never happen, since the class name is read from the class path
                throw new IllegalStateException(e);
            }
        }).collect(Collectors.toList());
    }
}
