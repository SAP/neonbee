package io.neonbee.internal.tracing;

import static com.google.common.truth.Truth.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.registry.otlp.OtlpMeterRegistry;
import io.neonbee.config.TracingConfig;
import io.vertx.core.json.JsonObject;

class OtlpMeterRegistryLoaderTest {

    @Test
    @DisplayName("metricsEndpoint should append /v1/metrics only when needed")
    void testMetricsEndpoint() {
        assertThat(OtlpMeterRegistryLoader.metricsEndpoint("http://localhost:4318"))
                .isEqualTo("http://localhost:4318/v1/metrics");
        assertThat(OtlpMeterRegistryLoader.metricsEndpoint("http://localhost:4318/"))
                .isEqualTo("http://localhost:4318/v1/metrics");
        assertThat(OtlpMeterRegistryLoader.metricsEndpoint("http://localhost:4318/v1/metrics"))
                .isEqualTo("http://localhost:4318/v1/metrics");
    }

    @Test
    @DisplayName("load should create an OTLP meter registry from a JSON config")
    void testLoadFromJson() {
        MeterRegistry registry = new OtlpMeterRegistryLoader().load(new JsonObject()
                .put("otlpEndpoint", "http://localhost:4318")
                .put("otlpApiToken", "dt0c01.secret")
                .put("exportIntervalSeconds", 30)
                .put("serviceName", "svc"));
        assertThat(registry).isInstanceOf(OtlpMeterRegistry.class);
        registry.close();
    }

    @Test
    @DisplayName("load should tolerate a null config")
    void testLoadNullConfig() {
        MeterRegistry registry = new OtlpMeterRegistryLoader().load(null);
        assertThat(registry).isInstanceOf(OtlpMeterRegistry.class);
        registry.close();
    }

    @Test
    @DisplayName("createRegistry from TracingConfig should build an OTLP meter registry")
    void testCreateRegistryFromConfig() {
        MeterRegistry registry = OtlpMeterRegistryLoader.createRegistry(
                new TracingConfig().setEnabled(true).setOtlpEndpoint("http://localhost:4318"), "fallback-instance");
        assertThat(registry).isInstanceOf(OtlpMeterRegistry.class);
        registry.close();
    }
}
