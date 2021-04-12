package io.neonbee.internal.helper;

import static io.neonbee.internal.helper.FileSystemHelper.readJSON;
import static io.neonbee.internal.helper.FileSystemHelper.readJSONBlocking;
import static io.neonbee.internal.helper.FileSystemHelper.readYAML;
import static io.neonbee.internal.helper.FileSystemHelper.readYAMLBlocking;
import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;

import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

import io.neonbee.NeonBee;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.file.FileSystemException;
import io.vertx.core.json.JsonObject;

public final class ConfigHelper {
    /**
     * This helper class cannot be instantiated
     */
    private ConfigHelper() {}

    public static Future<JsonObject> readConfig(Vertx vertx, String identifier) {
        Path configDirPath = NeonBee.get(vertx).getOptions().getConfigDirectory();

        return readYAML(vertx, configDirPath.resolve(identifier + ".yaml"))
                .recover(throwable -> throwable.getCause() instanceof NoSuchFileException
                        ? readJSON(vertx, configDirPath.resolve(identifier + ".json"))
                        : failedFuture(throwable));
    }

    public static Future<JsonObject> readConfig(Vertx vertx, String identifier, JsonObject fallback) {
        return readConfig(vertx, identifier)
                .recover(throwable -> throwable.getCause() instanceof NoSuchFileException ? succeededFuture(fallback)
                        : failedFuture(throwable));
    }

    public static JsonObject readConfigBlocking(Vertx vertx, String identifier) {
        Path configDirPath = NeonBee.get(vertx).getOptions().getConfigDirectory();
        try {
            return readYAMLBlocking(vertx, configDirPath.resolve(identifier + ".yaml").toAbsolutePath().toString());
        } catch (FileSystemException e) {
            if (e.getCause() instanceof NoSuchFileException) {
                return readJSONBlocking(vertx, configDirPath.resolve(identifier + ".json").toAbsolutePath().toString());
            } else {
                throw e;
            }
        }
    }

    public static JsonObject readConfigBlocking(Vertx vertx, String identifier, JsonObject fallback) {
        try {
            return readConfigBlocking(vertx, identifier);
        } catch (FileSystemException e) {
            return fallback;
        }
    }
}
