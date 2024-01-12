package io.neonbee.internal.helper;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.file.FileProps;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.json.JsonObject;

public final class FileSystemHelper {
    private static final ObjectMapper YAML_MAPPER = new YAMLMapper();

    /**
     * This helper class cannot be instantiated.
     */
    private FileSystemHelper() {}

    /**
     * This method checks if the file is a directory.
     *
     * @param vertx The related Vert.x instance
     * @param path  The Path of the file
     * @return Future of {@link Boolean}
     */
    @SuppressWarnings("PMD.LinguisticNaming")
    public static Future<Boolean> isDirectory(Vertx vertx, Path path) {
        return getProperties(vertx, path).compose(properties -> Future.succeededFuture(properties.isDirectory()));
    }

    /**
     * This method invokes {@link #readDir(Vertx, Path, String)} and reads the content of a passed directory,
     * asynchronously.
     *
     * @param vertx The related Vert.x instance
     * @param path  The Path of the directory
     * @return Future of {@link List} of {@link String}s
     */
    public static Future<List<Path>> readDir(Vertx vertx, Path path) {
        return vertx.fileSystem().readDir(path.toString())
                .map(files -> files.stream().map(Path::of).toList());
    }

    /**
     * This method reads the content of a passed directory. The {@code filter} parameter is a regular expression for
     * filtering by file name, asynchronously.
     *
     * @param vertx  The related Vert.x instance
     * @param filter The filter expression.
     * @param path   The Path of the directory
     * @return Future of {@link List} of {@link String}s
     */
    public static Future<List<Path>> readDir(Vertx vertx, Path path, String filter) {
        return vertx.fileSystem().readDir(path.toString(), filter)
                .map(files -> files.stream().map(Path::of).toList());
    }

    /**
     * Open the file represented by {@code path}, asynchronously.
     * <p>
     * The file is opened for both reading and writing. If the file does not already exist it will be created.
     *
     * @param vertx   The related Vert.x instance
     * @param options options describing how the file should be opened
     * @param path    path to the file
     * @return Future of {@link AsyncFile}
     */
    public static Future<AsyncFile> openFile(Vertx vertx, OpenOptions options, Path path) {
        return vertx.fileSystem().open(path.toString(), options);
    }

    /**
     * This method reads the entire file as a {@link Buffer}, asynchronously.
     *
     * @param vertx The related Vert.x instance
     * @param path  The path of the file
     * @return Future of {@link Buffer}
     */
    public static Future<Buffer> readFile(Vertx vertx, Path path) {
        return vertx.fileSystem().readFile(path.toString());
    }

    /**
     * This method reads the entire file in JSON format asynchronously and converts it to a {@link JsonObject}.
     *
     * @param vertx The related Vert.x instance
     * @param path  The path of the file
     * @return a future to the converted {@link JsonObject}
     */
    public static Future<JsonObject> readJSON(Vertx vertx, Path path) {
        return readFile(vertx, path).map(Buffer::toJsonObject);
    }

    /**
     * This method reads the entire file in YAML format asynchronously and converts it to a {@link JsonObject}.
     *
     * @param vertx The related Vert.x instance
     * @param path  The path of the file
     * @return a future to the converted {@link JsonObject}
     */
    public static Future<JsonObject> readYAML(Vertx vertx, Path path) {
        return readFile(vertx, path).compose(buffer -> parseYAML(vertx, buffer));
    }

    private static Future<JsonObject> parseYAML(Vertx vertx, Buffer buffer) {
        return vertx.executeBlocking(() -> {
            JsonNode node = YAML_MAPPER.readTree(buffer.getBytes());
            return new JsonObject(node.toString());
        });
    }

    /**
     * This method creates a file and writes the {@code buffer} to the file, asynchronously.
     *
     * @param vertx  The related Vert.x instance
     * @param path   The path of the file
     * @param buffer The content to write
     * @return Future of {@link Void}
     */
    public static Future<Void> writeFile(Vertx vertx, Path path, Buffer buffer) {
        return vertx.fileSystem().writeFile(path.toString(), buffer);
    }

    /**
     * This method deletes recursively the files represented by the specified path, asynchronously.
     *
     * @param vertx The related Vert.x instance
     * @param path  The path of the file
     * @return Future of {@link Void}
     */
    public static Future<Void> deleteRecursive(Vertx vertx, Path path) {
        return vertx.fileSystem().deleteRecursive(path.toString(), true);
    }

    /**
     * This method checks if the file represented as a path exists, asynchronously.
     *
     * @param vertx The related Vert.x instance
     * @param path  The path of the file
     * @return Future of {@link Boolean}
     */
    public static Future<Boolean> exists(Vertx vertx, Path path) {
        return vertx.fileSystem().exists(path.toString());
    }

    /**
     * This method creates the directory represented by path and any non existent parents, asynchronously.
     *
     * @param vertx The related Vert.x instance
     * @param path  The path of the file
     * @return Future of {@link Void}
     */
    public static Future<Void> createDirs(Vertx vertx, Path path) {
        return vertx.fileSystem().mkdirs(path.toString());
    }

    /**
     * This method obtains properties for the file represented by path, asynchronously.
     *
     * @param vertx The related Vert.x instance
     * @param path  The path of the file
     * @return Future of {@link FileProps}
     */
    public static Future<FileProps> getProperties(Vertx vertx, Path path) {
        return vertx.fileSystem().props(path.toString());
    }

    /**
     * Parses file system path(s) into {@link Path}, by separating each path by the {@link File#pathSeparatorChar}
     * before mapping it into a {@link Path}.
     *
     * @param paths any number of strings, that may contain one or multiple paths separated by the OS specific
     *              {@link File#pathSeparatorChar}
     * @return a list of parsed {@link Path}
     */
    public static Collection<Path> parsePaths(String... paths) {
        return Arrays.stream(paths).flatMap(Pattern.compile(File.pathSeparator)::splitAsStream).map(Path::of)
                .toList();
    }

    /**
     * Get a path from a map, ignoring path separators of different platforms.
     *
     * @param <V>  value type of map
     * @param map  any map that maps from string paths to values
     * @param path the path to get from the map
     * @return the value associated to the path or null, in case no value is mapped to the path
     */
    public static <V> V getPathFromMap(Map<String, V> map, String path) {
        return map.containsKey(path) ? map.get(path)
                : map.get(path.replace(File.separatorChar, File.separatorChar == '/' ? '\\' : '/'));
    }
}
