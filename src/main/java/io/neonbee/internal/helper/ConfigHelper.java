package io.neonbee.internal.helper;

import static io.neonbee.internal.helper.FileSystemHelper.readJSON;
import static io.neonbee.internal.helper.FileSystemHelper.readYAML;
import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;

import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.function.Function;
import java.util.function.Supplier;

import io.neonbee.NeonBee;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

public final class ConfigHelper {
    /**
     * This helper class cannot be instantiated.
     */
    private ConfigHelper() {}

    /**
     * Read the configuration for a given identifier (file name) from the NeonBee configuration directory.
     * <p>
     * This method will first attempt to read a file with the name of the identifier in the YAML format by appending
     * either a ".yaml" or ".yml" to the identifier, if not present, it'll attempt to read the configuration in JSON
     * format appending a ".json" to the identifier / file name.
     *
     * @param vertx      the Vert.x instance used to read the file
     * @param identifier the identifier / file name of the config file (without a file extension)
     * @return a future to a JsonObject or a failed future in case reading failed or the config file was not found
     */
    public static Future<JsonObject> readConfig(Vertx vertx, String identifier) {
        Path configDirPath = NeonBee.get(vertx).getOptions().getConfigDirectory();

        return readYAML(vertx, configDirPath.resolve(identifier + ".yaml"))
                .recover(notFound(() -> readYAML(vertx, configDirPath.resolve(identifier + ".yml"))))
                .recover(notFound(() -> readJSON(vertx, configDirPath.resolve(identifier + ".json"))));
    }

    /**
     * Read the configuration for a given identifier (file name) from the NeonBee configuration directory.
     *
     * Similar to {@link #readConfig(Vertx, String)} but with an optional fallback, if the config file could not be
     * found.
     *
     * @see #readConfig(Vertx, String)
     * @param vertx      the Vert.x instance used to read the file
     * @param identifier the identifier / file name of the config file (without a file extension)
     * @param fallback   a fallback configuration {@link JsonObject}
     * @return a future to a JsonObject or a failed future in case reading failed
     */
    public static Future<JsonObject> readConfig(Vertx vertx, String identifier, JsonObject fallback) {
        return readConfig(vertx, identifier).recover(notFound(() -> succeededFuture(fallback)));
    }

    private static <T> Function<Throwable, Future<T>> notFound(Supplier<Future<T>> whenNotFound) {
        return throwable -> throwable.getCause() instanceof NoSuchFileException ? whenNotFound.get()
                : failedFuture(throwable);
    }
}
