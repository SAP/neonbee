package io.neonbee.test.helper;

import static io.neonbee.internal.helper.ThreadHelper.getCallingClass;

import java.io.IOException;
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
        Path packagePath = Path.of(getCallingClass().getPackage().getName().replace(".", "/"));
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
}
