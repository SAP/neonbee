package io.neonbee.internal.verticle;

import java.util.Objects;

import org.slf4j.Logger;

import ch.qos.logback.classic.Level;

import com.google.common.annotations.VisibleForTesting;
import io.vertx.core.json.JsonObject;

/**
 * A data structure for reading and setting log level for a specific logger. This data structure supports following
 * attributes: name:name of the logger configuredLevel: current log level in configuration. effectiveLevel: currently
 * effective log level. This can be different than the configured log level: e.g. for a specific logger, there might be
 * no explicit configuration, so that the configuredLevel is null. Nevertheless, the logger will inherit the setting
 * from parent logger, so that it has a different effective level.
 *
 */
public class LoggerConfiguration implements Comparable<LoggerConfiguration> {
    @VisibleForTesting
    static final String EFFECTIVE_LEVEL = "effectiveLevel";

    @VisibleForTesting
    static final String CONFIGURED_LEVEL = "configuredLevel";

    @VisibleForTesting
    static final String NAME = "name";

    private String name;

    private String configuredLevel;

    private String effectiveLevel;

    /**
     * Creates a new empty LoggerConfiguration.
     */
    public LoggerConfiguration() {}

    /**
     * Creates a new LoggerConfiguration.
     *
     * @param name            the name
     * @param configuredLevel the configured level
     */
    public LoggerConfiguration(String name, Level configuredLevel) {
        this(name, configuredLevel, null);
    }

    /**
     * Creates a new LoggerConfiguration.
     *
     * @param name            the name
     * @param configuredLevel a string representing the configured level
     */
    public LoggerConfiguration(String name, String configuredLevel) {
        this(name, configuredLevel, null);
    }

    /**
     * Creates a new LoggerConfiguration.
     *
     * @param name            the name
     * @param configuredLevel the configured level
     * @param effectiveLevel  the effectiveLevel level
     */
    public LoggerConfiguration(String name, Level configuredLevel, Level effectiveLevel) {
        this(name, configuredLevel != null ? configuredLevel.levelStr : null,
                effectiveLevel != null ? effectiveLevel.levelStr : null);
    }

    /**
     * Creates a new LoggerConfiguration.
     *
     * @param name            the name
     * @param configuredLevel a string representing the configured level
     * @param effectiveLevel  a string representing the effectiveLevel level
     */
    public LoggerConfiguration(String name, String configuredLevel, String effectiveLevel) {
        this.name = name;
        this.configuredLevel = configuredLevel;
        this.effectiveLevel = effectiveLevel;
    }

    /**
     * Returns the configured level of the logger configuration.
     *
     * @return the configured level
     */
    public String getConfiguredLevel() {
        return this.configuredLevel;
    }

    /**
     * Returns the effective level of the logger configuration.
     *
     * @return the effective level
     */
    public String getEffectiveLevel() {
        return this.effectiveLevel;
    }

    /**
     * Returns the name of the logger configuration.
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
     * Sets the configuredLevel of the logger configuration.
     *
     * @param configuredLevel a string representing the configured level
     * @return the LoggerConfiguration for chaining
     */
    public LoggerConfiguration setConfiguredLevel(String configuredLevel) {
        this.configuredLevel = configuredLevel;
        return this;
    }

    /**
     * Sets the effectiveLevel of the logger configuration.
     *
     * @param effectiveLevel a string representing the effectiveLevel level
     * @return the LoggerConfiguration for chaining
     */
    public LoggerConfiguration setEffectiveLevel(String effectiveLevel) {
        this.effectiveLevel = effectiveLevel;
        return this;
    }

    /**
     * Creates a copy of the {@link LoggerConfiguration} object.
     *
     * @return A new object of type {@link LoggerConfiguration}
     */
    public LoggerConfiguration copy() {
        return new LoggerConfiguration(name, configuredLevel, effectiveLevel);
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
            return Objects.equals(this.name, other.name) && Objects.equals(this.configuredLevel, other.configuredLevel)
                    && Objects.equals(this.effectiveLevel, other.effectiveLevel);
        }
        return super.equals(obj);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.name, this.configuredLevel, this.effectiveLevel);
    }

    @Override
    public String toString() {
        return "LoggerConfig [name=" + this.name + ", configuredLevel=" + this.configuredLevel + ", effectiveLevel="
                + this.effectiveLevel + "]";
    }

    @Override
    public int compareTo(LoggerConfiguration o) {
        if (Logger.ROOT_LOGGER_NAME.equals(this.getName())) {
            return -1;
        }
        if (Logger.ROOT_LOGGER_NAME.equals(o.getName())) {
            return 1;
        }
        return this.getName().compareTo(o.getName());
    }

    /**
     * Convert to {@link JsonObject}.
     *
     * @return A {@link JsonObject} which contains the log level information
     */
    public JsonObject toJson() {
        return new JsonObject().put(NAME, this.name)
                .put(CONFIGURED_LEVEL, this.configuredLevel != null ? this.configuredLevel : null)
                .put(EFFECTIVE_LEVEL, this.effectiveLevel != null ? this.effectiveLevel : null);
    }

    /**
     * Convert from a passed {@link JsonObject}.
     *
     * @param json The {@link JsonObject} to be converted
     * @return A new {@link LoggerConfiguration} which contains the log level information from a passed object.
     */
    public static LoggerConfiguration fromJson(JsonObject json) {
        return new LoggerConfiguration(json.getString(NAME), json.getString(CONFIGURED_LEVEL),
                json.getString(EFFECTIVE_LEVEL));
    }
}
