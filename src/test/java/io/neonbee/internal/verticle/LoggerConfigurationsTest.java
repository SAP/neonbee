package io.neonbee.internal.verticle;

import static ch.qos.logback.classic.Level.ERROR;
import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.internal.verticle.LoggerConfigurationTest.LOGGER1;
import static io.neonbee.internal.verticle.LoggerConfigurationTest.LOGGER2;
import static io.neonbee.internal.verticle.LoggerConfigurationTest.ROOT;

import java.util.List;

import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class LoggerConfigurationsTest {
    private static final String CONFIGURED_LEVEL = "configuredLevel";

    private static final String NAME = "name";

    private final LoggerConfigurations configs = new LoggerConfigurations(List.of(ROOT, LOGGER1, LOGGER2));

    @Test
    public void testBasics() {
        assertThat(configs.copy().getConfigurations()).containsExactlyElementsIn(configs.getConfigurations());
        assertThat(configs.copy().getConfigurations()).isNotSameInstanceAs(configs.getConfigurations());
    }

    @Test
    public void testToJson() throws JSONException {
        JsonArray json = configs.toJson();
        assertThat(json).hasSize(3);
        JSONAssert.assertEquals(json.toString(),
                "[{\"name\":\"ROOT\",\"configuredLevel\":\"INFO\",\"effectiveLevel\":\"INFO\"},{\"name\":\"logger1\",\"configuredLevel\":\"WARN\",\"effectiveLevel\":\"ERROR\"},{\"name\":\"logger2\",\"configuredLevel\":\"WARN\",\"effectiveLevel\":\"ERROR\"}]",
                false);
    }

    @Test
    public void testFromJson() {
        JsonObject json1 = new JsonObject().put(NAME, "logger1").put(CONFIGURED_LEVEL, ERROR.levelStr);
        JsonObject json2 = new JsonObject().put(NAME, "logger2").put(CONFIGURED_LEVEL, ERROR.levelStr);
        LoggerConfigurations configs = LoggerConfigurations.fromJson(new JsonArray(List.of(json1, json2)));
        assertThat(configs.getConfigurations()).hasSize(2);
        configs.getConfigurations().forEach(entry -> {
            assertThat(entry.getName()).isAnyOf("logger1", "logger2");
            assertThat(entry.getConfiguredLevel()).isEqualTo("ERROR");
        });
    }
}
