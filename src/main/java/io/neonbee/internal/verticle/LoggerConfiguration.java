package io.neonbee.internal.verticle;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.impl.StaticLoggerBinder;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;

import com.google.common.annotations.VisibleForTesting;

import io.neonbee.logging.LoggingFacade;
import io.vertx.core.json.JsonObject;

/**
 * Management object for reading and setting log levels for a specific logger. The name of the logger and the
 * configuredLevel determine the configuration. effectiveLevel being a volatile attribute, determined by the underlying
 * logger instance. For a specific logger the configuredLevel might be null, while the effective level is always
 * retrieved either from the logger or one of its parents.
 */
public class LoggerConfiguration implements Comparable<LoggerConfiguration> {
    @VisibleForTesting
    static final String NAME_KEY = "name";

    @VisibleForTesting
    static final String CONFIGURED_LEVEL_KEY = "configuredLevel";

    private static final LoggingFacade LOGGER = LoggingFacade.create();

    private static final LoggerContext LOGGER_CONTEXT =
            (LoggerContext) StaticLoggerBinder.getSingleton().getLoggerFactory();

    private String name;

    private Level configuredLevel;

    /**
     * Retrieve all logger configurations.
     *
     * @return A list of all loggers as {@link LoggerConfiguration}
     */
    public static List<LoggerConfiguration> getLoggerConfigurations() {
        return LOGGER_CONTEXT.getLoggerList().stream().map(LoggerConfiguration::getLoggerConfiguration).sorted()
                .collect(Collectors.toList());
    }

    /**
     * Retrieve a logger configuration for a logger with a given name.
     *
     * @param loggerName the name of the logger as string
     * @return the logging configuration as {@link LoggerConfiguration}
     */
    public static LoggerConfiguration getLoggerConfiguration(String loggerName) {
        return getLoggerConfiguration(LOGGER_CONTEXT.getLogger(loggerName));
    }

    /**
     * Retrieves the logger configuration for a given logger.
     *
     * @param logger logger, whose configuration should be returned.
     * @return the logging configuration as {@link LoggerConfiguration}
     */
    public static LoggerConfiguration getLoggerConfiguration(Logger logger) {
        return new LoggerConfiguration(logger.getName(), logger.getLevel());
    }

    /**
     * Creates a new empty LoggerConfiguration.
     */
    public LoggerConfiguration() {
        this(null);
    }

    /**
     * Creates a new LoggerConfiguration for a logger of a given name.
     *
     * @param name the name of a logger
     */
    public LoggerConfiguration(String name) {
        this(name, (Level) null);
    }

    /**
     * Creates a new LoggerConfiguration with a level to configure a specific logger.
     *
     * @param name            the name of a logger
     * @param configuredLevel a string representing the configured level
     */
    public LoggerConfiguration(String name, String configuredLevel) {
        this(name, configuredLevel != null ? Level.toLevel(configuredLevel) : null);
    }

    /**
     * Creates a new LoggerConfiguration with a level to configure a specific logger.
     *
     * @param name            the name
     * @param configuredLevel the configured level
     */
    public LoggerConfiguration(String name, Level configuredLevel) {
        this.name = name;
        this.configuredLevel = configuredLevel;
    }

    /**
     * Returns the name of the logger in this configuration.
     *
     * @return the name
     */
    public String getName() {
        return this.name;
    }

    /**
     * Sets the name of the logger configuration.
     *
     * @param name the name
     * @return the LoggerConfiguration for chaining
     */
    public LoggerConfiguration setName(String name) {
        this.name = name;
        return this;
    }

    /**
     * Returns the underlying logger instance for this configuration.
     *
     * @return the logger for this configuration or null, in case there is no such logger
     */
    public Logger getLogger() {
        return LOGGER_CONTEXT.getLogger(name);
    }

    /**
     * Returns the configured level of this logger configuration.
     *
     * @return the configured level
     */
    public Level getConfiguredLevel() {
        return this.configuredLevel;
    }

    /**
     * Sets the configured level of this logger configuration.
     *
     * @param configuredLevel a string representing the configured level
     * @return the LoggerConfiguration for chaining
     */
    public LoggerConfiguration setConfiguredLevel(String configuredLevel) {
        return setConfiguredLevel(configuredLevel != null ? Level.toLevel(configuredLevel) : null);
    }

    /**
     * Sets the configured level of this logger configuration.
     *
     * @param configuredLevel the configured level
     * @return the LoggerConfiguration for chaining
     */
    public LoggerConfiguration setConfiguredLevel(Level configuredLevel) {
        this.configuredLevel = configuredLevel;
        return this;
    }

    /**
     * Applies the configured level of the logger configuration to the associated logger instance. Equivalent to
     * {@code loggerConfiguration.setEffectiveLevel(loggerConfiguration.getConfiguredLevel())}.
     *
     * @return the LoggerConfiguration for chaining
     */
    public LoggerConfiguration applyConfiguredLevel() {
        this.setEffectiveLevel(getConfiguredLevel());
        return this;
    }

    /**
     * Returns the effective level of the logger specified in this logger configuration.
     *
     * @return the effective level or null, in case there is no such logger
     */
    public Level getEffectiveLevel() {
        return Optional.ofNullable(getLogger()).map(Logger::getEffectiveLevel).orElse(null);
    }

    /**
     * Sets the effective level of the logger specified in this logger configuration.
     *
     * @param effectiveLevel a string representing the effectiveLevel level
     * @return the LoggerConfiguration for chaining
     */
    public LoggerConfiguration setEffectiveLevel(String effectiveLevel) {
        return this.setEffectiveLevel(effectiveLevel != null ? Level.toLevel(effectiveLevel) : null);
    }

    /**
     * Sets the effective level of the logger specified in this logger configuration.
     *
     * @param effectiveLevel the effectiveLevel level
     * @return the LoggerConfiguration for chaining
     */
    public LoggerConfiguration setEffectiveLevel(Level effectiveLevel) {
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Changing log level for {} from {} to {}.", getName(), getEffectiveLevel(), effectiveLevel);
        }
        Optional.ofNullable(getLogger()).ifPresent(logger -> logger.setLevel(effectiveLevel));
        return this;
    }

    /**
     * Creates a copy of the {@link LoggerConfiguration} object.
     *
     * @return A new object of type {@link LoggerConfiguration}
     */
    public LoggerConfiguration copy() {
        return new LoggerConfiguration(name, configuredLevel);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (obj instanceof LoggerConfiguration) {
            LoggerConfiguration other = (LoggerConfiguration) obj;
            return Objects.equals(this.name, other.name) && Objects.equals(this.configuredLevel, other.configuredLevel);
        }
        return super.equals(obj);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.name, this.configuredLevel);
    }

    @Override
    public String toString() {
        return "LoggerConfiguration [name=" + this.name + ", configuredLevel=" + this.configuredLevel + "]";
    }

    @Override
    public int compareTo(LoggerConfiguration other) {
        if (Logger.ROOT_LOGGER_NAME.equals(this.getName())) {
            return -1;
        }
        if (Logger.ROOT_LOGGER_NAME.equals(other.getName()) || this.getName() == null) {
            return 1;
        }
        return this.getName().compareTo(other.getName());
    }

    /**
     * Convert to {@link JsonObject}.
     *
     * @return A {@link JsonObject} which contains the log level information
     */
    public JsonObject toJson() {
        return new JsonObject().put(NAME_KEY, this.name).put(CONFIGURED_LEVEL_KEY,
                this.configuredLevel != null ? this.configuredLevel.levelStr : null);
    }

    /**
     * Convert from a passed {@link JsonObject}.
     *
     * @param json The {@link JsonObject} to be converted
     * @return A new {@link LoggerConfiguration} which contains the log level information from a passed object.
     */
    public static LoggerConfiguration fromJson(JsonObject json) {
        return new LoggerConfiguration(json.getString(NAME_KEY), json.getString(CONFIGURED_LEVEL_KEY));
    }
}
