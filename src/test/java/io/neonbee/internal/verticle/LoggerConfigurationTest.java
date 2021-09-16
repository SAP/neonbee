package io.neonbee.internal.verticle;

import static ch.qos.logback.classic.Level.DEBUG;
import static ch.qos.logback.classic.Level.ERROR;
import static ch.qos.logback.classic.Level.INFO;
import static ch.qos.logback.classic.Level.TRACE;
import static ch.qos.logback.classic.Level.WARN;
import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.internal.verticle.LoggerConfiguration.CONFIGURED_LEVEL;
import static io.neonbee.internal.verticle.LoggerConfiguration.NAME;

import java.util.Arrays;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import io.neonbee.logging.LoggingFacade;
import io.vertx.core.json.JsonObject;

class LoggerConfigurationTest {
    public static final LoggerConfiguration ROOT = new LoggerConfiguration(Logger.ROOT_LOGGER_NAME, INFO);

    private static int logInstanceCount;

    private final LoggerConfiguration logger1 = new LoggerConfiguration(null, WARN);

    private final LoggerConfiguration logger2 = new LoggerConfiguration(null, DEBUG);

    @BeforeEach
    void setUpLoggers() {
        // there is no way to "delete" log instances, thus create new log instances for each run

        String name1 = LoggerConfigurationTest.class.getSimpleName() + ++logInstanceCount;
        LoggingFacade.create(name1);
        logger1.setName(name1);

        String name2 = LoggerConfigurationTest.class.getSimpleName() + ++logInstanceCount;
        LoggingFacade.create(name2);
        logger2.setName(name2);
    }

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
        assertThat(json).isEqualTo(new JsonObject().put(NAME, "ROOT").put(CONFIGURED_LEVEL, "INFO"));
    }

    @Test
    void testFromJson() {
        JsonObject json = new JsonObject().put(NAME, "logger1").put(CONFIGURED_LEVEL, ERROR.levelStr);
        LoggerConfiguration configuration = LoggerConfiguration.fromJson(json);
        assertThat(configuration).isEqualTo(new LoggerConfiguration("logger1", ERROR));
    }

    @Test
    void testCompare() {
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
