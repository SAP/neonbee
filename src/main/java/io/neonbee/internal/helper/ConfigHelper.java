package io.neonbee.internal.helper;

import static io.neonbee.internal.helper.CollectionHelper.identityMapCollector;
import static io.neonbee.internal.helper.FileSystemHelper.readJSON;
import static io.neonbee.internal.helper.FileSystemHelper.readYAML;
import static io.neonbee.internal.helper.FunctionalHelper.entryConsumer;
import static io.neonbee.internal.helper.FunctionalHelper.keyPredicate;
import static io.neonbee.internal.helper.FunctionalHelper.valuePredicate;
import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static java.util.function.Predicate.not;

import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import com.google.common.collect.BiMap;
import com.google.common.collect.Sets;

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
        return readConfig(vertx, identifier, configDirPath);
    }

    /**
     * Read the configuration for a given identifier (file name) from the configDirPath configuration directory.
     * <p>
     * This method will first attempt to read a file with the name of the identifier in the YAML format by appending
     * either a ".yaml" or ".yml" to the identifier, if not present, it'll attempt to read the configuration in JSON
     * format appending a ".json" to the identifier / file name.
     *
     * @param vertx         the Vert.x instance used to read the file
     * @param identifier    the identifier / file name of the config file (without a file extension)
     * @param configDirPath the config directory path
     * @return a future to a JsonObject or a failed future in case reading failed or the config file was not found
     */
    public static Future<JsonObject> readConfig(Vertx vertx, String identifier, Path configDirPath) {
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

    /**
     * Returns the value of the supplier if the exception is an instance of {@link NoSuchFileException}, otherwise
     * return a failed future with the exception.
     *
     * @param whenNotFound supplier of the value
     * @param <T>          Type of the value
     * @return a function which takes the exception and returns a future with the whenNotFound value if the exception is
     *         an instance of a {@link NoSuchFileException}
     */
    public static <T> Function<Throwable, Future<T>> notFound(Supplier<Future<T>> whenNotFound) {
        return throwable -> throwable.getCause() instanceof NoSuchFileException ? whenNotFound.get()
                : failedFuture(throwable);
    }

    /**
     * Used to rephrase certain elements of a config. JsonObject in-place. Useful as in NeonBee the programmatic
     * representation of parameters in a configuration object, sometimes have a different representation when converted
     * from JSON and back. This method can be use in both directions being called with a bi-directional map.
     *
     * @param config  the config {@link JsonObject} to rephrase configurations in
     * @param map     the bi-directional map to use to rephrase the properties
     * @param inverse whether to use the inverse map to transform the config. names back to the second representation
     * @return the config object for chaining
     */
    public static JsonObject rephraseConfigNames(JsonObject config, BiMap<String, String> map, boolean inverse) {
        (!inverse ? map : map.inverse()).entrySet().forEach(entryConsumer((fromName, toName) -> {
            config.put(toName, config.remove(fromName));
        }));
        return config;
    }

    /**
     * Collects the additional configuration out of a configuration object.
     *
     * What this method actually does is to extract all values of a configuration object, which are not part of a given
     * set of keys. The properties are then returned as a new (additional configuration) JSON object.
     *
     * @param config        the configuration object to extract the additional information from
     * @param notAdditional an varargs array of keys, which are not considered additional
     * @return a new JsonObject containing all present additional configurations of the config
     */
    public static JsonObject collectAdditionalConfig(JsonObject config, String... notAdditional) {
        Set<String> notAdditionalSet = Sets.newHashSet(notAdditional);
        notAdditionalSet.add("additionalConfig");
        return new JsonObject(config.stream().filter(valuePredicate(Objects::nonNull))
                .filter(keyPredicate(not(notAdditionalSet::contains))).collect(identityMapCollector()));
    }
}
