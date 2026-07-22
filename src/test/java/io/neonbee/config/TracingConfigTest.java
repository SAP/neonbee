package io.neonbee.config;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.config.TracingConfig.DEFAULT_EXPORT_INTERVAL_SECONDS;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonObject;

class TracingConfigTest {

    @Test
    @DisplayName("should have correct default values")
    void testDefaultValues() {
        TracingConfig config = new TracingConfig();
        assertThat(config.isEnabled()).isFalse();
        assertThat(config.getOtlpEndpoint()).isNull();
        assertThat(config.getOtlpApiToken()).isNull();
        assertThat(config.getExportIntervalSeconds()).isEqualTo(DEFAULT_EXPORT_INTERVAL_SECONDS);
    }

    @Test
    @DisplayName("should round-trip all fields through JSON")
    void testJsonRoundTrip() {
        TracingConfig original = new TracingConfig()
                .setEnabled(true)
                .setOtlpEndpoint("http://localhost:4317")
                .setOtlpApiToken("dt0c01.secret")
                .setExportIntervalSeconds(30);

        JsonObject json = original.toJson();
        TracingConfig restored = new TracingConfig(json);

        assertThat(restored.isEnabled()).isTrue();
        assertThat(restored.getOtlpEndpoint()).isEqualTo("http://localhost:4317");
        assertThat(restored.getOtlpApiToken()).isEqualTo("dt0c01.secret");
        assertThat(restored.getExportIntervalSeconds()).isEqualTo(30);
    }

    @Test
    @DisplayName("should parse enabled from JSON")
    void testFromJsonEnabled() {
        TracingConfig config = new TracingConfig(new JsonObject().put("enabled", true));
        assertThat(config.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("should parse disabled from JSON")
    void testFromJsonDisabled() {
        TracingConfig config = new TracingConfig(new JsonObject().put("enabled", false));
        assertThat(config.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("should parse otlpEndpoint from JSON")
    void testFromJsonOtlpEndpoint() {
        TracingConfig config =
                new TracingConfig(new JsonObject().put("otlpEndpoint", "https://example.dynatrace.com/api/v2/otlp"));
        assertThat(config.getOtlpEndpoint()).isEqualTo("https://example.dynatrace.com/api/v2/otlp");
    }

    @Test
    @DisplayName("should omit null fields in toJson")
    void testToJsonOmitsNulls() {
        JsonObject json = new TracingConfig().toJson();
        assertThat(json.containsKey("otlpEndpoint")).isFalse();
        assertThat(json.containsKey("otlpApiToken")).isFalse();
    }

    @Test
    @DisplayName("should serialize enabled and exportIntervalSeconds always")
    void testToJsonAlwaysPresent() {
        JsonObject json = new TracingConfig().toJson();
        assertThat(json.containsKey("enabled")).isTrue();
        assertThat(json.containsKey("exportIntervalSeconds")).isTrue();
    }

    @Test
    @DisplayName("fluent setters should return same instance")
    void testFluentSetters() {
        TracingConfig config = new TracingConfig();
        assertThat(config.setEnabled(true)).isSameInstanceAs(config);
        assertThat(config.setOtlpEndpoint("http://localhost:4317")).isSameInstanceAs(config);
        assertThat(config.setOtlpApiToken("token")).isSameInstanceAs(config);
        assertThat(config.setExportIntervalSeconds(30)).isSameInstanceAs(config);
    }
}
