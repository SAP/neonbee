package io.neonbee.internal.verticle;

import static ch.qos.logback.classic.Level.ERROR;
import static ch.qos.logback.classic.Level.INFO;
import static ch.qos.logback.classic.Level.WARN;
import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.internal.verticle.LoggerConfiguration.CONFIGURED_LEVEL;
import static io.neonbee.internal.verticle.LoggerConfiguration.EFFECTIVE_LEVEL;
import static io.neonbee.internal.verticle.LoggerConfiguration.NAME;

import org.junit.jupiter.api.Test;

import ch.qos.logback.classic.Logger;
import io.vertx.core.json.JsonObject;

public class LoggerConfigurationTest {
    public static final LoggerConfiguration ROOT = new LoggerConfiguration(Logger.ROOT_LOGGER_NAME, INFO, INFO);

    public static final LoggerConfiguration LOGGER1 = new LoggerConfiguration("logger1", WARN, ERROR);

    public static final LoggerConfiguration LOGGER2 = new LoggerConfiguration("logger2", WARN, ERROR);

    @Test
    public void testBasics() {
        ROOT.hashCode();
        assertThat(ROOT.toString()).isEqualTo("LoggerConfig [name=ROOT, configuredLevel=INFO, effectiveLevel=INFO]");
        assertThat(ROOT.getName()).isEqualTo(Logger.ROOT_LOGGER_NAME);
        assertThat(ROOT.getConfiguredLevel()).isEqualTo(INFO.levelStr);
        assertThat(ROOT.getEffectiveLevel()).isEqualTo(INFO.levelStr);
    }

    @Test
    public void testToJson() {
        JsonObject json = ROOT.toJson();
        assertThat(json).isEqualTo(
                new JsonObject().put(NAME, "ROOT").put(CONFIGURED_LEVEL, "INFO").put(EFFECTIVE_LEVEL, "INFO"));
    }

    @Test
    public void testFromJson() {
        JsonObject json = new JsonObject().put(NAME, "logger1").put(CONFIGURED_LEVEL, ERROR.levelStr);
        LoggerConfiguration configuration = LoggerConfiguration.fromJson(json);
        assertThat(configuration).isEqualTo(new LoggerConfiguration("logger1", ERROR));
    }

    @Test
    public void testCompare() {
        assertThat(ROOT.compareTo(LOGGER1)).isEqualTo(-1);
        assertThat(LOGGER2.compareTo(LOGGER1)).isEqualTo(1);
    }

    @Test
    public void testCopy() {
        assertThat(ROOT.copy()).isEqualTo(ROOT);
        assertThat(ROOT.copy()).isNotSameInstanceAs(ROOT);
    }
}
