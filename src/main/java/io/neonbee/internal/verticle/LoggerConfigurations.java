package io.neonbee.internal.verticle;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * A data structure for reading and setting log level for a specific logger.
 *
 */
public class LoggerConfigurations {

    private final List<LoggerConfiguration> configurations;

    /**
     * Creates a new empty container for logger configurations.
     */
    public LoggerConfigurations() {
        this(new ArrayList<LoggerConfiguration>());
    }

    /**
     * Creates a new container for logger configurations with the passed configurations.
     *
     * @param configurations the configurations to fill in
     */
    public LoggerConfigurations(List<LoggerConfiguration> configurations) {
        this.configurations = configurations;
    }

    /**
     * Returns the list of logger configurations.
     *
     * @return the logger configurations
     */
    public List<LoggerConfiguration> getConfigurations() {
        return configurations;
    }

    /**
     * Creates a copy of the object.
     *
     * @return A new object of type {@link LoggerConfigurations}
     */
    public LoggerConfigurations copy() {
        return new LoggerConfigurations(
                configurations.stream().map(LoggerConfiguration::copy).collect(Collectors.toList()));
    }

    /**
     * Convert to {@link JsonArray}.
     *
     * @return A {@link JsonArray} which contains the log level information of different loggers
     */
    public JsonArray toJson() {
        JsonArray array = new JsonArray();
        configurations.stream().map(LoggerConfiguration::toJson).forEach(json -> array.add(json));
        return array;
    }

    /**
     * Convert from a passed {@link JsonArray}.
     *
     * @param jsonArray The {@link JsonArray} to be converted
     * @return A new {@link LoggerConfigurations} which contains the logging configuration of different loggers.
     */
    public static LoggerConfigurations fromJson(JsonArray jsonArray) {
        return new LoggerConfigurations(jsonArray.stream().map(json -> LoggerConfiguration.fromJson((JsonObject) json))
                .collect(Collectors.toList()));
    }
}
