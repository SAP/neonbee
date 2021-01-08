package io.neonbee.internal.helper;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.file.FileProps;
import io.vertx.core.file.OpenOptions;

public final class FileSystemHelper {

    /**
     * This method checks if the file is a directory.
     *
     * @param vertx The related Vert.x instance
     * @param path  The Path of the file
     * @return Future of {@link Boolean}
     */
    @SuppressWarnings("PMD.LinguisticNaming")
    public static Future<Boolean> isDirectory(Vertx vertx, Path path) {
        return props(vertx, path).compose(properties -> Future.succeededFuture(properties.isDirectory()));
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
        return Future.<List<String>>future(handler -> vertx.fileSystem().readDir(path.toString(), handler))
                .map(files -> files.stream().map(Path::of).collect(Collectors.toList()));
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
        return Future.<List<String>>future(handler -> vertx.fileSystem().readDir(path.toString(), filter, handler))
                .map(files -> files.stream().map(Path::of).collect(Collectors.toList()));
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
        return Future.<AsyncFile>future(promise -> vertx.fileSystem().open(path.toString(), options, promise));
    }

    /**
     * This method reads the entire file as a {@link Buffer}, asynchronously.
     *
     * @param vertx The related Vert.x instance
     * @param path  The path of the file
     * @return Future of {@link Buffer}
     */
    public static Future<Buffer> readFile(Vertx vertx, Path path) {
        return Future.<Buffer>future(readFilePromise -> vertx.fileSystem().readFile(path.toString(), readFilePromise));
    }

    /**
     * This method creates a file and writes the {@code buffered} {@code model} data to the file, asynchronously.
     *
     * @param vertx  The related Vert.x instance
     * @param path   The path of the file
     * @param buffer The content to write
     * @return Future of {@link Void}
     */
    public static Future<Void> writeFile(Vertx vertx, Path path, Buffer buffer) {
        return Future.<Void>future(promise -> vertx.fileSystem().writeFile(path.toString(), buffer, promise));
    }

    /**
     * This method deletes recursively the files represented by the specified path, asynchronously.
     *
     * @param vertx The related Vert.x instance
     * @param path  The path of the file
     * @return Future of {@link Void}
     */
    public static Future<Void> deleteRecursive(Vertx vertx, Path path) {
        return Future.<Void>future(promise -> vertx.fileSystem().deleteRecursive(path.toString(), true, promise));
    }

    /**
     * This method checks if the file represented as a path exists, asynchronously.
     *
     * @param vertx The related Vert.x instance
     * @param path  The path of the file
     * @return Future of {@link Boolean}
     */
    public static Future<Boolean> exists(Vertx vertx, Path path) {
        return Future.<Boolean>future(promise -> vertx.fileSystem().exists(path.toString(), promise));
    }

    /**
     * This method creates the directory represented by path and any non existent parents, asynchronously.
     *
     * @param vertx The related Vert.x instance
     * @param path  The path of the file
     * @return Future of {@link Void}
     */
    public static Future<Void> createDirs(Vertx vertx, Path path) {
        return Future.<Void>future(promise -> vertx.fileSystem().mkdirs(path.toString(), promise));
    }

    /**
     * This method obtains properties for the file represented by path, asynchronously.
     *
     * @param vertx The related Vert.x instance
     * @param path  The path of the file
     * @return Future of {@link FileProps}
     */
    public static Future<FileProps> props(Vertx vertx, Path path) {
        return Future.<FileProps>future(promise -> vertx.fileSystem().props(path.toString(), promise));
    }

    private FileSystemHelper() {
        /* nothing to do here */
    }

}
