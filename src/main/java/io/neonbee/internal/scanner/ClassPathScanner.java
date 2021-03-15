package io.neonbee.internal.scanner;

import static io.neonbee.internal.Helper.EMPTY;
import static io.neonbee.internal.Helper.getClassLoader;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.jar.Manifest;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.objectweb.asm.ClassReader;

import com.google.common.base.Strings;
import com.google.common.collect.Streams;
import io.neonbee.internal.JarHelper;

/**
 * The possible entries for the classpath are defined here [1]:
 *
 * <pre>
 * - For a .jar or .zip file that contains .class files, the class path ends with the name of the .zip or .jar file.
 * - For .class files in an unnamed package, the class path ends with the directory that contains the .class files.
 * - For .class files in a named package, the class path ends with the directory that contains the "root" package
 *   (the first package in the full package name).
 * </pre>
 *
 * This means in short, that there are only entries that represents a directory, a .zip file or a .jar file. For now the
 * {@link ClassPathScanner} only supports scanning directories with the method {@link #scanWithPredicate(Predicate)} and
 * .jar files with the method {@link #scanJarFilesWithPredicate(Predicate)}. It also offers the method
 * {@link ClassPathScanner#scanManifestFiles(String)} to extract the values of a given manifest attribute from every jar
 * file on the classpath.
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
        this(getClassLoader());
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
     * Scans the class path for MANIFEST.MF files (in JARs), in case the manifest file contains the given attribute,
     * returns the value.
     *
     * This method should be invoked on a {@link ClassLoader} with a single jat file only.
     *
     * @param attributeName The name of the attribute in the MANIFEST.MF to search for.
     * @return value of the attribute
     *
     * @throws IOException exception
     */
    public String retrieveManifestAttribute(String attributeName) throws IOException {
        List<URL> manifestResourceURLs = getManifestResourceURLs();
        if (manifestResourceURLs.size() != 1) {
            throw new IllegalStateException("Only one MANIFEST.MF expected, but found " + manifestResourceURLs.size());
        }
        try (InputStream inputStream = manifestResourceURLs.get(0).openStream()) {
            // Attribute looks like: Attribute-Name: package.Resource1; package.Resource2
            return new Manifest(inputStream).getMainAttributes().getValue(attributeName);
        }
    }

    /**
     * Scans the class path for MANIFEST.MF files (in JARs), in case the manifest file contains the given attribute,
     * parses the attribute and returns the values as a list of string.
     *
     * This allows to e.g. denote certain classes or resources of the JAR files to be exposed.
     *
     * @param attributeName The name of the attribute in the MANIFEST.MF to search for.
     * @return a list of strings (separated list of manifest attribute values)
     * @throws IOException If an I/O error occurs while scanning the class paths
     */
    public List<String> scanManifestFiles(String attributeName) throws IOException {
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
    }

    /**
     * Scans the whole class path for annotations on the whole class, shorthand for: <br>
     * {@code scanForAnnotation(Class, ElementType.TYPE)}.
     *
     * @see #scanForAnnotation(Class, ElementType...)
     * @param annotationClass the annotation to check for
     * @return a List of resources on the class path
     * @throws IOException        If operations on the filesystem fail
     * @throws URISyntaxException If parsing the URI fails
     */
    public List<String> scanForAnnotation(Class<? extends Annotation> annotationClass)
            throws IOException, URISyntaxException {
        return scanForAnnotation(annotationClass, ElementType.TYPE);
    }

    /**
     * Scans the whole class path (does also recursively dig into JAR files!) for class files which are annotated with a
     * given annotation (either the whole class, methods or fields might be annotated and specified in elementTypes).
     *
     * @param annotationClass the annotation to check for
     * @param elementTypes    the types of annotation to check for (supports TYPE, FIELD and METHOD)
     * @return a List of resources on the class path
     * @throws IOException        If operations on the filesystem fail
     * @throws URISyntaxException If parsing the URI fails
     */
    public List<String> scanForAnnotation(Class<? extends Annotation> annotationClass, ElementType... elementTypes)
            throws IOException, URISyntaxException {
        return scanForAnnotation(List.of(annotationClass), elementTypes);

    }

    /**
     * Scans the whole class path (does also recursively dig into JAR files!) for class files which are annotated with a
     * given annotation (either the whole class, methods or fields might be annotated and specified in elementTypes).
     *
     * @param annotationClasses A List of annotations to check for
     * @param elementTypes      the types of annotation to check for (supports TYPE, FIELD and METHOD)
     * @return a List of resources on the class path
     * @throws IOException        If operations on the filesystem fail
     * @throws URISyntaxException If parsing the URI fails
     */
    @SuppressWarnings("PMD.EmptyCatchBlock")
    public List<String> scanForAnnotation(List<Class<? extends Annotation>> annotationClasses,
            ElementType... elementTypes) throws IOException, URISyntaxException {
        List<AnnotationClassVisitor> classVisitors = annotationClasses.stream()
                .map(annotationClass -> new AnnotationClassVisitor(annotationClass, elementTypes))
                .collect(Collectors.toList());
        List<String> classesFromDirectories = scanWithPredicate(ClassPathScanner::isClassFile);
        List<String> classesFromJars = scanJarFilesWithPredicate(ClassPathScanner::isClassFile).stream()
                .map(JarHelper::extractFilePath).collect(Collectors.toList());

        Streams.concat(classesFromDirectories.stream(), classesFromJars.stream()).forEach(name -> {
            try {
                String resourceName = name.replace(".", "/").replaceFirst("/class$", ".class");
                for (AnnotationClassVisitor acv : classVisitors) {
                    ClassReader classReader = new ClassReader(classLoader.getResourceAsStream(resourceName));
                    classReader.accept(acv, 0);
                }
            } catch (IOException e) {
                /*
                 * nothing to do here, depending on which part of the reading it failed, deployable could be set or not
                 */
            }
        });

        return classVisitors.stream().flatMap(acv -> acv.getClassNames().stream()).distinct()
                .collect(Collectors.toList());
    }

    /**
     * Scans all directories in the classpath for files whose file name matches a certain predicate.
     *
     * @param predicate The predicate to test if a found file matches.
     * @return the name of the resources on the class path.
     *
     * @throws IOException If operations on the filesystem fail
     */
    @SuppressWarnings("PMD.EmptyCatchBlock")
    public List<String> scanWithPredicate(Predicate<String> predicate) throws IOException {
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
                // The file must be a directory, because the class path does only contains JARs, ZIPs and directories.
                if (Files.isDirectory(resourcePath)) {
                    scanDirectoryWithPredicateRecursive(resourcePath, predicate)
                            .forEach(path -> resources.add(resourcePath.relativize(path).toString()));
                }
            } catch (URISyntaxException e) {
                /* nothing to do here, just continue searching */
            }
        }
        return resources;
    }

    /**
     * Scans all files inside a jar file of all jar files on the class path and test if the file name match the given
     * predicate.
     *
     * @param predicate The predicate to test if a found file matches.
     * @return A list of URIs representing the files which matches the given predicate
     *
     * @throws IOException        If operations on the filesystem fail
     * @throws URISyntaxException If parsing the URI fails
     */
    public List<URI> scanJarFilesWithPredicate(Predicate<String> predicate) throws IOException, URISyntaxException {
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
    }

    private List<URL> getManifestResourceURLs() throws IOException {
        Enumeration<URL> manifestResources = classLoader.getResources("META-INF/MANIFEST.MF");
        List<URL> urls = new ArrayList<>();
        manifestResources.asIterator().forEachRemaining(urls::add);
        return urls;
    }

    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "RCN_REDUNDANT_NULLCHECK_WOULD_HAVE_BEEN_A_NPE",
            justification = "Spotbugs isn't telling the truth there is no null check in here")
    private List<Path> scanDirectoryWithPredicateRecursive(Path basePath, Predicate<String> predicate)
            throws IOException {
        try (Stream<Path> walk = Files.walk(basePath)) {
            return walk.filter(path -> predicate.test(basePath.relativize(path).toString()))
                    .collect(Collectors.toList());
        }
    }

    private static boolean isClassFile(String name) {
        return name.endsWith(".class");
    }
}
