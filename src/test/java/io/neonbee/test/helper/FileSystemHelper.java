package io.neonbee.test.helper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.file.FileSystemException;
import io.vertx.core.file.impl.FileSystemImpl;

public final class FileSystemHelper {
    /**
     * This method behaves like {@link #deleteRecursive(Vertx, Path)}, but instantiate it's own Vert.x instance.
     *
     * @deprecated use {@link #deleteRecursive(Vertx, Path)} instead, assuming that if you need a {@link Future} you
     *             have a Vert.x at hand anyways
     * @param path The path to delete
     * @return A future to resolve when the delete operation finishes
     */
    @Deprecated(forRemoval = true)
    public static Future<Void> deleteRecursive(Path path) {
        Vertx vertx = Vertx.vertx();
        return deleteRecursive(vertx, path).eventually(throwable -> {
            // we wait for Vert.x to close, before we propagate the reason why deleting failed
            return vertx.close();
        });
    }

    /**
     * This method deletes the file represented by the specified path, synchronously. If the path represents a directory
     * then the directory and its contents will be deleted recursively.
     *
     * @param vertx The related Vert.x instance
     * @param path  The path to delete
     * @return A future to resolve when the delete operation finishes
     */
    public static Future<Void> deleteRecursive(Vertx vertx, Path path) {
        return vertx.fileSystem().deleteRecursive(path.toString(), true);
    }

    /**
     * This method behaves similar to {@link #deleteRecursiveBlocking(Vertx, Path)}, but does not require a Vert.x
     * instance.
     *
     * @param path The path to delete
     */
    public static void deleteRecursiveBlocking(Path path) {
        try (Stream<Path> pathStream = Files.walk(path)) {
            FileSystemImpl.delete(path, true);
        } catch (IOException e) {
            throw new FileSystemException(e);
        }
    }

    /**
     * This method deletes the file represented by the specified path, synchronously. If the path represents a directory
     * then the directory and its contents will be deleted recursively.
     *
     * @param vertx The related Vert.x instance
     * @param path  The path to delete
     */
    public static void deleteRecursiveBlocking(Vertx vertx, Path path) {
        vertx.fileSystem().deleteRecursiveBlocking(path.toString(), true);
    }

    /**
     * This method copies a directory recursively and replaces existing files.
     *
     * @param srcDirPath  The path of the source directory
     * @param destDirPath The path of the destination directory
     * @throws IOException If copying the files is not successful
     */
    public static void copyDirectory(Path srcDirPath, Path destDirPath) throws IOException {
        try (Stream<Path> stream = Files.walk(srcDirPath)) {
            List<Path> orderedPaths = new ArrayList<>();
            stream.forEachOrdered(orderedPaths::add);
            for (Path path : orderedPaths) {
                Files.copy(path, destDirPath.resolve(srcDirPath.relativize(path)), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    /**
     * This method extracts a zip file recursively into the passed directory and replaces existing files.
     *
     * @param zipFile     The path of the zip file
     * @param destDirPath The path of the destination directory
     * @throws IOException If creating the destination folder or copying the files is not successful
     */
    public static void extractZipFile(Path zipFile, Path destDirPath) throws IOException {
        try (ZipInputStream zipInput = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry zipEntry;
            while ((zipEntry = zipInput.getNextEntry()) != null) {
                Path destPath = destDirPath.resolve(zipEntry.getName());
                if (zipEntry.isDirectory()) {
                    Files.createDirectories(destPath);
                } else {
                    Files.copy(zipInput, destPath);
                }
            }
            zipInput.closeEntry();
        }
    }

    /**
     * Creates a temporary directory.
     *
     * @return The path of the temporary created directory.
     * @throws IOException If creating the temporary directory is not successful
     */
    public static Path createTempDirectory() throws IOException {
        return Files.createTempDirectory(FileSystemHelper.class.getName());
    }

    private FileSystemHelper() {
        // helper class no need to instantiate
    }
}
