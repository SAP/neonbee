package io.neonbee.test.helper;

import static java.lang.StackWalker.Option.RETAIN_CLASS_REFERENCE;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import io.vertx.core.buffer.Buffer;

public final class ResourceHelper {
    /**
     * The ResourceHelper related to the test resources src/test/resources
     */
    public static final ResourceHelper TEST_RESOURCES = new ResourceHelper(Paths.get("src", "test", "resources"));

    /**
     * The ResourceHelper related to the main resources src/main/resources
     */
    public static final ResourceHelper MAIN_RESOURCES = new ResourceHelper(Paths.get("src", "main", "resources"));

    private final Path resourcePath;

    private ResourceHelper(Path resourcePath) {
        this.resourcePath = resourcePath;
    }

    /**
     * Detect and retrieve the package name of the calling class. This package name is then transformed into a path and
     * resolved against the related resource folder. This path construct is called <i>related resources path</i>,
     * because the path is related to the parent class which is calling this method.
     * <p>
     * Related Resources Path: &gt;resources folder&lt;/&gt;package as path&lt;
     *
     * The passed filename gets resolved against the related resources path.
     *
     * @param filename The filename to resolve
     * @return The passed filename resolved against the related resources folder
     */
    public Path resolveRelated(String filename) {
        StackWalker walker = StackWalker.getInstance(RETAIN_CLASS_REFERENCE);
        Class<?> callerClass = walker.walk(stackStream -> stackStream
                .filter(stackframe -> !ResourceHelper.class.equals(stackframe.getDeclaringClass())).findFirst().get()
                .getDeclaringClass());

        Path packagePath = Path.of(callerClass.getPackage().getName().replace(".", "/"));
        return resourcePath.resolve(packagePath).resolve(filename);
    }

    /**
     * Similar to {@link #resolveRelated(String)}, but returns the content of the file, which was found by resolving the
     * passed filename.
     *
     * @param filename The filename to resolve
     * @return The content of the passed filename resolved against the related resources folder
     * @throws IOException If reading the file is not successful
     */
    public Buffer getRelated(String filename) throws IOException {
        return Buffer.buffer(Files.readAllBytes(resolveRelated(filename)));
    }

    /**
     * Resolves the passed path against the related resource folder.
     *
     * @param relativePath The relative path to resolve against the related resources folder
     * @return The passed path resolved against the related resource folder.
     */
    public Path resolve(String relativePath) {
        return resolve(Path.of(relativePath));
    }

    /**
     * Resolves the passed path against the related resource folder.
     *
     * @param relativePath The relative path to resolve against the related resources folder
     * @return The passed path resolved against the related resource folder.
     */
    public Path resolve(Path relativePath) {
        return resourcePath.resolve(relativePath);
    }

    /**
     * Similar to {@link #resolve(String)}, but returns the content of the file, which was found by resolving the passed
     * filename.
     *
     * @param relativePath The relative path to resolve against the related resources folder
     * @return The content of the file representing the passed path resolved against the related resources folder
     * @throws IOException If reading the file is not successful
     */
    public Buffer get(String relativePath) throws IOException {
        return get(Path.of(relativePath));
    }

    /**
     * Similar to {@link #resolve(Path)}, but returns the content of the file, which was found by resolving the passed
     * filename.
     *
     * @param relativePath The relative path to resolve against the related resources folder
     * @return The content of the file representing the passed path resolved against the related resources folder
     * @throws IOException If reading the file is not successful
     */
    public Buffer get(Path relativePath) throws IOException {
        return Buffer.buffer(Files.readAllBytes(resourcePath.resolve(relativePath)));
    }

    /**
     * A path to the test resources folder
     */
    @Deprecated(forRemoval = true)
    public static final Path TEST_RESOURCES_PATH = Paths.get("src", "test", "resources");

    /**
     * This method behaves like {@link #resolveTestResource(Path)}, but accepts a String as file.
     *
     * @param file The file to resolve
     * @return The passed file resolved against the test resources folder
     */
    @Deprecated(forRemoval = true)
    public static Path resolveTestResource(String file) {
        return resolveTestResource(Path.of(file));
    }

    /**
     * This method accepts a relative {@link Path} of a file inside the resources folder and resolves this path against
     * the test resources folder.
     *
     * @param file The file to resolve
     * @return The passed file resolved against the test resources folder
     */
    @Deprecated(forRemoval = true)
    public static Path resolveTestResource(Path file) {
        return TEST_RESOURCES_PATH.resolve(file);
    }

    /**
     * This method transforms a package name into a path.
     * <p>
     * Example: com.example.foo.bar results into com/example/foo/bar
     *
     * @param p The name providing package to transform into a path
     * @return A {@link Path} that represent the package name
     */
    @Deprecated(forRemoval = true)
    public static Path getPackageAsPath(Package p) {
        return Path.of(p.getName().replace(".", "/"));
    }

    /**
     * This method behaves like {@link #getPackageAsPath(Package)} and uses the package of the passed class.
     *
     * @param clazz The package providing class
     * @return A {@link Path} that represent the package name of the passed class
     */
    @Deprecated(forRemoval = true)
    public static Path getPackageAsPath(Class<?> clazz) {
        return getPackageAsPath(clazz.getPackage());
    }

    /**
     * This method behaves like {@link #getRelatedTestResource(Path)}, but accepts a String as file.
     *
     * @param filename The file to resolve
     * @return The passed file resolved against the related test resources folder
     */
    @Deprecated(forRemoval = true)
    public static Path getRelatedTestResource(String filename) {
        return getRelatedTestResource(Path.of(filename));
    }

    /**
     * This method detect the class from which the method is called and retrieves the package name of the calling class.
     * This package name is then transformed into a path and resolved against the test resource folder. This path
     * construct is the so called related test resource path. Because the path is related to the test class which is
     * calling this method.
     * <p>
     * Related Test Resources Path: &gt;test resources folder&lt;/&gt;package as path&lt;
     *
     * The passed file path gets resolved against the related test resources path.
     *
     * @param file The file to resolve
     * @return The passed file resolved against the related test resources folder
     */
    @Deprecated(forRemoval = true)
    public static Path getRelatedTestResource(Path file) {
        StackWalker walker = StackWalker.getInstance(RETAIN_CLASS_REFERENCE);
        Class<?> callerClass = walker.walk(stackStream -> {
            return stackStream.filter(stackframe -> !ResourceHelper.class.equals(stackframe.getDeclaringClass()))
                    .findFirst().get().getDeclaringClass();
        });

        return TEST_RESOURCES_PATH.resolve(getPackageAsPath(callerClass)).resolve(file);
    }

    /**
     * This method reads the passed resource and returns it as a Buffer.
     *
     * @param path The path to the resource to read in
     * @return A Buffer with the content of the passed resource.
     * @throws IOException Resource cannot be read
     */
    @Deprecated(forRemoval = true)
    public static Buffer getResourceAsBuffer(Path path) throws IOException {
        return Buffer.buffer(Files.readAllBytes(path));
    }

    /**
     * This method reads the passed resource and returns it as an UTF-8 encoded String.
     *
     * @param path The path to the resource to read in
     * @return A String with the UTF-8 encoded content of the passed resource.
     * @throws IOException Resource cannot be read
     */
    @Deprecated(forRemoval = true)
    public static String getResourceAsString(Path path) throws IOException {
        return getResourceAsBuffer(path).toString(StandardCharsets.UTF_8);
    }
}
