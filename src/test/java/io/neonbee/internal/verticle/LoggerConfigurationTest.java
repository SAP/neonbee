package io.neonbee.internal.verticle;

import static ch.qos.logback.classic.Level.DEBUG;
import static ch.qos.logback.classic.Level.ERROR;
import static ch.qos.logback.classic.Level.INFO;
import static ch.qos.logback.classic.Level.TRACE;
import static ch.qos.logback.classic.Level.WARN;
import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.internal.verticle.LoggerConfiguration.CONFIGURED_LEVEL_KEY;
import static io.neonbee.internal.verticle.LoggerConfiguration.NAME_KEY;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import io.vertx.core.json.JsonObject;

class LoggerConfigurationTest {
    private static final LoggerConfiguration ROOT = new LoggerConfiguration(Logger.ROOT_LOGGER_NAME, INFO);

    private final LoggerConfiguration logger1 =
            new LoggerConfiguration(LoggerConfigurationTest.class.getSimpleName() + "1", WARN);

    @Test
    void testBasics() {
        assertThat(ROOT.hashCode()).isNotEqualTo(0);
        assertThat(ROOT.toString()).isEqualTo("LoggerConfiguration [name=ROOT, configuredLevel=INFO]");
        assertThat(ROOT.getName()).isEqualTo(Logger.ROOT_LOGGER_NAME);
        assertThat(ROOT.getConfiguredLevel()).isEqualTo(INFO);
    }

    @Test
    void testToJson() {
        JsonObject json = ROOT.toJson();
        assertThat(json).isEqualTo(new JsonObject().put(NAME_KEY, "ROOT").put(CONFIGURED_LEVEL_KEY, "INFO"));
    }

    @Test
    void testFromJson() {
        JsonObject json = new JsonObject().put(NAME_KEY, "logger1").put(CONFIGURED_LEVEL_KEY, ERROR.levelStr);
        LoggerConfiguration configuration = LoggerConfiguration.fromJson(json);
        assertThat(configuration).isEqualTo(new LoggerConfiguration("logger1", ERROR));
    }

    @Test
    void testCompare() {
        LoggerConfiguration logger2 =
                new LoggerConfiguration(LoggerConfigurationTest.class.getSimpleName() + "2", DEBUG);

        assertThat(ROOT.compareTo(logger1)).isEqualTo(-1);
        assertThat(logger2.compareTo(logger1)).isEqualTo(1);
    }

    @Test
    void testCopy() {
        assertThat(ROOT.copy()).isEqualTo(ROOT);
        assertThat(ROOT.copy()).isNotSameInstanceAs(ROOT);
    }

    @Test
    void testToAndFromJson() {
        assertThat(LoggerConfiguration.fromJson(logger1.toJson())).isEqualTo(logger1);
    }

    @Test
    void testApplyingEffectiveLogLevels() {
        logger1.setEffectiveLevel(INFO);
        assertThat(logger1.getEffectiveLevel()).isEqualTo(INFO);

        logger1.setEffectiveLevel("warn");
        assertThat(logger1.getEffectiveLevel()).isEqualTo(WARN);

        Arrays.stream(new Level[] { DEBUG, ERROR, WARN }).forEach(level -> {
            logger1.setEffectiveLevel(level);
            assertThat(logger1.getEffectiveLevel()).isEqualTo(level);

            // test chaining any applying the configuration
            assertThat(logger1.setEffectiveLevel(INFO).setConfiguredLevel(level).applyConfiguredLevel()
                    .getEffectiveLevel()).isEqualTo(level);
        });

        logger1.setEffectiveLevel(TRACE); // trace is never the default level
        logger1.setConfiguredLevel((Level) null); // try to set the default level
        logger1.applyConfiguredLevel();
        assertThat(logger1.getEffectiveLevel()).isNotEqualTo(TRACE);
    }
}
