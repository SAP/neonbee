package io.neonbee.internal.scanner;

import static io.neonbee.internal.helper.StringHelper.EMPTY;
import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.jar.Manifest;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.objectweb.asm.ClassReader;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;

import io.neonbee.internal.helper.FileSystemHelper;
import io.neonbee.internal.helper.JarHelper;
import io.neonbee.internal.helper.ThreadHelper;
import io.neonbee.logging.LoggingFacade;
import io.vertx.core.Future;
import io.vertx.core.Vertx;

/**
 * The possible entries for the class path are defined here [1]:
 *
 * <pre>
 * - For a .jar or .zip file that contains .class files, the class path ends with the name of the .zip or .jar file.
 * - For .class files in an unnamed package, the class path ends with the directory that contains the .class files.
 * - For .class files in a named package, the class path ends with the directory that contains the "root" package
 *   (the first package in the full package name).
 * </pre>
 *
 * This means in short, that there are only entries that represents a directory, a .zip file or a .jar file. For now the
 * {@link ClassPathScanner} only supports scanning directories with the method
 * {@link #scanWithPredicate(Vertx, Predicate)} and .jar files with the method
 * {@link #scanJarFilesWithPredicate(Vertx, Predicate)}. It also offers the method
 * {@link ClassPathScanner#scanManifestFiles(Vertx, String)} to extract the values of a given manifest attribute from
 * every jar file on the class path.
 * <p>
 * [1] https://docs.oracle.com/javase/7/docs/technotes/tools/windows/classpath.html
 *
 */
public class ClassPathScanner {
    /**
     * The pattern which is used to separate values in the manifest.ml.
     */
    public static final Pattern SEPARATOR_PATTERN = Pattern.compile(";");

    private final ClassLoader classLoader;

    /**
     * Creates a new ClassPathScanner with the current context class loader.
     */
    public ClassPathScanner() {
        this(ThreadHelper.getClassLoader()); // NOPMD false positive getClassLoader
    }

    /**
     * Creates a new ClassPathScanner with the passed class loader.
     *
     * @param classLoader the class loader to use
     */
    public ClassPathScanner(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    /**
     * Get the class loader this scanner is using.
     *
     * @return the class loader this class path scanner is scanning from.
     */
    public ClassLoader getClassLoader() {
        return classLoader;
    }

    /**
     * Creates a new closable instance of a {@link ClassPathScanner}, that scans for the contents of a JAR file.
     *
     * This initializes a new {@link URLClassLoader} without any parent, to search only inside the JAR. Calling
     * {@link CloseableClassPathScanner#close} on the returned instance, will close the underlying
     * {@link URLClassLoader}.
     *
     * @param vertx   the Vert.x instance used to check the JAR files existence
     * @param jarPath the path to the JAR file
     * @return a closable {@link ClassPathScanner}
     */
    public static Future<CloseableClassPathScanner> forJarFile(Vertx vertx, Path jarPath) {
        return FileSystemHelper.exists(vertx, jarPath).compose(jarExists -> {
            if (!jarExists) {
                return failedFuture(new NoSuchFileException("JAR path does not exist: " + jarPath.toString()));
            }

            URL jarUrl;
            try {
                jarUrl = jarPath.toUri().toURL();
            } catch (MalformedURLException e) {
                return failedFuture(e);
            }

            return succeededFuture(new CloseableClassPathScanner(new URLClassLoader(new URL[] { jarUrl }, null)));
        });
    }

    /**
     * Scans the class path for MANIFEST.MF files (in JARs), in case the manifest file contains the given attribute,
     * parses the attribute and returns the values as a list of string.
     *
     * This allows to e.g. denote certain classes or resources of the JAR files to be exposed.
     *
     * @param vertx         the Vert.x instance
     * @param attributeName The name of the attribute in the MANIFEST.MF to search for.
     * @return a future to a list of strings (separated list of manifest attribute values)
     */
    public Future<List<String>> scanManifestFiles(Vertx vertx, String attributeName) {
        return vertx.executeBlocking(() -> {
            List<String> resources = new ArrayList<>();
            for (URL manifestResources : getManifestResourceURLs()) {
                try (InputStream inputStream = manifestResources.openStream()) {
                    Manifest manifest = new Manifest(inputStream);
                    // Attribute looks like: Attribute-Name: package.Resource1; package.Resource2
                    String attributeValue = manifest.getMainAttributes().getValue(attributeName);
                    if (!Strings.isNullOrEmpty(attributeValue)) {
                        SEPARATOR_PATTERN.splitAsStream(attributeValue).map(String::trim).forEach(resources::add);
                    }
                }
            }
            return resources;
        });
    }

    /**
     * Scans the whole class path for annotations on the whole class, shorthand for: <br>
     * {@code scanForAnnotation(Class, ElementType.TYPE)}.
     *
     * @see #scanForAnnotation(Vertx, Class, ElementType...)
     * @param vertx           the Vert.x instance
     * @param annotationClass the annotation to check for
     * @return a future to a list of resources on the class path
     */
    public Future<List<String>> scanForAnnotation(Vertx vertx, Class<? extends Annotation> annotationClass) {
        return scanForAnnotation(vertx, annotationClass, ElementType.TYPE);
    }

    /**
     * Scans the whole class path (does also recursively dig into JAR files!) for class files which are annotated with a
     * given annotation (either the whole class, methods or fields might be annotated and specified in elementTypes).
     *
     * @param vertx           the Vert.x instance
     * @param annotationClass the annotation to check for
     * @param elementTypes    the types of annotation to check for (supports TYPE, FIELD and METHOD)
     * @return a future to a list of resources on the class path
     */
    public Future<List<String>> scanForAnnotation(Vertx vertx, Class<? extends Annotation> annotationClass,
            ElementType... elementTypes) {
        return scanForAnnotation(vertx, List.of(annotationClass), elementTypes);

    }

    /**
     * Scans the whole class path (does also recursively dig into JAR files!) for class files which are annotated with a
     * given annotation (either the whole class, methods or fields might be annotated and specified in elementTypes).
     *
     * @param vertx             the Vert.x instance
     * @param annotationClasses A List of annotations to check for
     * @param elementTypes      the types of annotation to check for (supports TYPE, FIELD and METHOD)
     * @return a future to a list of resources on the class path
     */
    @SuppressWarnings("PMD.EmptyCatchBlock")
    public Future<List<String>> scanForAnnotation(Vertx vertx,
            List<Class<? extends Annotation>> annotationClasses,
            ElementType... elementTypes) {

        Future<List<String>> classesFromDirectories = scanWithPredicate(vertx, ClassPathScanner::isClassFile);
        Future<List<String>> classesFromJars = scanJarFilesWithPredicate(vertx, ClassPathScanner::isClassFile)
                .map(uris -> {
                    List<String> result = new ArrayList<>(uris.size());
                    for (URI uri : uris) {
                        result.add(JarHelper.extractFilePath(uri));
                    }
                    return result;
                });

        return Future.all(classesFromDirectories, classesFromJars).compose(v -> vertx.executeBlocking(() -> {
            List<AnnotationClassVisitor> classVisitors = new ArrayList<>(annotationClasses.size());
            for (Class<? extends Annotation> annotationClass : annotationClasses) {
                classVisitors.add(new AnnotationClassVisitor(annotationClass, elementTypes));
            }

            processClasses(classesFromDirectories.result(), classVisitors);
            processClasses(classesFromJars.result(), classVisitors);

            Set<String> matchedClassNames = new HashSet<>();
            for (AnnotationClassVisitor visitor : classVisitors) {
                matchedClassNames.addAll(visitor.getClassNames());
            }

            return new ArrayList<>(matchedClassNames);
        }));
    }

    private void processClasses(List<String> classNames, List<AnnotationClassVisitor> classVisitors) {
        for (String name : classNames) {
            String resourcePath = name.replace('.', '/').replaceFirst("/class$", ".class");
            try (InputStream input = classLoader.getResourceAsStream(resourcePath)) {
                if (input != null) {
                    ClassReader reader = new ClassReader(input);
                    for (AnnotationClassVisitor visitor : classVisitors) {
                        reader.accept(visitor, 0);
                    }
                }
            } catch (IOException e) {
                // NOPMD Optionally log or collect failed paths for debugging
            }
        }
    }

    /**
     * Scans all directories in the class path for files whose file name matches a certain predicate.
     *
     * @param vertx     the Vert.x instance
     * @param predicate The predicate to test if a found file matches.
     * @return a future to a list of names of the resources on the class path.
     */
    @SuppressWarnings("PMD.EmptyCatchBlock")
    public Future<List<String>> scanWithPredicate(Vertx vertx, Predicate<String> predicate) {
        return vertx.executeBlocking(() -> {
            List<String> resources = new ArrayList<>();
            Enumeration<URL> rootResources = classLoader.getResources(EMPTY);
            while (rootResources.hasMoreElements()) {
                URL resource = rootResources.nextElement();
                if (!"file".equals(resource.getProtocol())) {
                    continue; // ignore non-files on root (we don't care for bundled JARs or ZIPs)

                    // .class files must be handled here
                }
                try {
                    Path resourcePath = Paths.get(resource.toURI());
                    // The file must be a directory, because the class path does only contains JARs, ZIPs and
                    // directories.
                    if (Files.isDirectory(resourcePath)) {
                        scanDirectoryWithPredicateRecursive(resourcePath, predicate)
                                .forEach(path -> resources.add(resourcePath.relativize(path).toString()));
                    }
                } catch (URISyntaxException e) {
                    /* nothing to do here, just continue searching */
                }
            }
            return resources;
        });
    }

    /**
     * Scans all files inside a jar file of all jar files on the class path and test if the file name match the given
     * predicate.
     *
     * @param vertx     the Vert.x instance
     * @param predicate The predicate to test if a found file matches.
     * @return a future to a list of URIs representing the files which matches the given predicate
     */
    public Future<List<URI>> scanJarFilesWithPredicate(Vertx vertx, Predicate<String> predicate) {
        return vertx.executeBlocking(() -> {
            List<URI> resources = new ArrayList<>();
            for (URL manifestResource : getManifestResourceURLs()) {
                URI uri = manifestResource.toURI();
                // filter for manifest files inside of jar files
                if ("jar".equals(uri.getScheme())) {
                    try (FileSystem fileSystem = FileSystems.newFileSystem(uri, Map.of())) {
                        Path rootPath = fileSystem.getPath("/");
                        scanDirectoryWithPredicateRecursive(rootPath, predicate)
                                .forEach(path -> resources.add(path.toUri()));
                    }
                }
            }
            return resources;
        });
    }

    /**
     * Blocking method to get all resource URLs from the MANIFEST.MF file(s) on the class loader
     *
     * Attention: Blocking! Must only be called inside a executeBlocking block!
     *
     * @return a List of URLs of the resources in the MANIFEST.MF file
     * @throws IOException an I/O error occurs while scanning the class paths
     */
    private List<URL> getManifestResourceURLs() throws IOException {
        return ImmutableList.copyOf(Iterators.forEnumeration(classLoader.getResources("META-INF/MANIFEST.MF")));
    }

    /**
     * Blocking inner method that scans a directory and checks file paths against string predicates
     *
     * Attention: Blocking! Must only be called inside a executeBlocking block!
     *
     * @param basePath  The path to scan
     * @param predicate the predicate to check against
     * @return a List of Paths of resources that match the predicate
     * @throws IOException an I/O error in case the walking fails
     */
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "RCN_REDUNDANT_NULLCHECK_WOULD_HAVE_BEEN_A_NPE",
            justification = "Spotbugs isn't telling the truth there is no null check in here")
    private List<Path> scanDirectoryWithPredicateRecursive(Path basePath, Predicate<String> predicate)
            throws IOException {
        try (Stream<Path> walk = Files.walk(basePath)) {
            return walk.filter(path -> predicate.test(basePath.relativize(path).toString()))
                    .toList();
        }
    }

    private static boolean isClassFile(String name) {
        return name.endsWith(".class");
    }

    public static class CloseableClassPathScanner extends ClassPathScanner implements Closeable {
        private static final LoggingFacade LOGGER = LoggingFacade.create();

        /**
         * Creates a new {@link CloseableClassPathScanner} with the passed class loader.
         *
         * @param urlClassLoader the URL class loader to use
         */
        public CloseableClassPathScanner(URLClassLoader urlClassLoader) {
            super(urlClassLoader);
        }

        @Override
        public URLClassLoader getClassLoader() {
            return (URLClassLoader) super.getClassLoader(); // NOPMD false positive getClassLoader
        }

        @Override
        public void close() throws IOException {
            getClassLoader().close();
        }

        /**
         * Returns a handler that if called, closes this {@link ClassPathScanner}. Useful in the context of class-path
         * scanning to call {@code classPathScanner.scan...().eventually(classPathScanner.close(vertx))}
         *
         * @param vertx the Vert.x instance on which to execute the closing operation on
         * @param <U>   an empty future of a given type
         * @return returns a mapper to a supplier that closes the underlying class loader of this
         *         {@link ClassPathScanner} when called
         */
        public <U> Future<U> close(Vertx vertx) {
            return vertx.executeBlocking(() -> {
                try {
                    close();
                } catch (IOException e) {
                    LOGGER.error("Failed to close {}", this, e);
                }
                return null;
            }).mapEmpty();
        }
    }
}
