package io.neonbee.internal.tracing;

import static com.google.common.truth.Truth.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.neonbee.NeonBeeOptions;
import io.neonbee.config.NeonBeeConfig;
import io.neonbee.config.TracingConfig;
import io.opentelemetry.sdk.OpenTelemetrySdk;

class NeonBeeOpenTelemetryTest {

    private static NeonBeeOptions optionsWith(TracingConfig tracingConfig) {
        return new NeonBeeOptions.Mutable().setInstanceName("test-instance").setTracingConfig(tracingConfig);
    }

    @Test
    @DisplayName("buildSdk should be empty when telemetry is disabled")
    void testDisabled() {
        Optional<OpenTelemetrySdk> sdk = NeonBeeOpenTelemetry
                .buildSdk(optionsWith(new TracingConfig().setEnabled(false).setOtlpEndpoint("http://localhost:4318")));
        assertThat(sdk).isEmpty();
    }

    @Test
    @DisplayName("buildSdk should be empty when trace export is disabled")
    void testTraceExportDisabled() {
        Optional<OpenTelemetrySdk> sdk = NeonBeeOpenTelemetry.buildSdk(optionsWith(new TracingConfig().setEnabled(true)
                .setExportTraces(false).setOtlpEndpoint("http://localhost:4318")));
        assertThat(sdk).isEmpty();
    }

    @Test
    @DisplayName("buildSdk should be empty (and warn) when endpoint is missing")
    void testMissingEndpoint() {
        Optional<OpenTelemetrySdk> sdk =
                NeonBeeOpenTelemetry.buildSdk(optionsWith(new TracingConfig().setEnabled(true)));
        assertThat(sdk).isEmpty();
    }

    @Test
    @DisplayName("buildSdk should build an SDK when enabled and configured")
    void testEnabledAndConfigured() {
        Optional<OpenTelemetrySdk> sdk = NeonBeeOpenTelemetry.buildSdk(optionsWith(new TracingConfig().setEnabled(true)
                .setOtlpEndpoint("http://localhost:4318").setOtlpApiToken("dt0c01.secret")));
        assertThat(sdk).isPresent();
        sdk.get().close();
    }

    @Test
    @DisplayName("resolveServiceName should prefer the configured value")
    void testResolveServiceNameConfigured() {
        assertThat(NeonBeeOpenTelemetry.resolveServiceName("configured", "fallback")).isEqualTo("configured");
    }

    @Test
    @DisplayName("resolveServiceName should fall back to the instance name when nothing else is set")
    void testResolveServiceNameFallback() {
        // this only holds when OTEL_SERVICE_NAME is not set in the environment
        if (System.getenv("OTEL_SERVICE_NAME") == null) {
            assertThat(NeonBeeOpenTelemetry.resolveServiceName(null, "fallback")).isEqualTo("fallback");
            assertThat(NeonBeeOpenTelemetry.resolveServiceName("  ", "fallback")).isEqualTo("fallback");
        }
    }

    @Test
    @DisplayName("tracesEndpoint should append /v1/traces only when needed")
    void testTracesEndpoint() {
        assertThat(NeonBeeOpenTelemetry.tracesEndpoint("http://localhost:4318"))
                .isEqualTo("http://localhost:4318/v1/traces");
        assertThat(NeonBeeOpenTelemetry.tracesEndpoint("http://localhost:4318/"))
                .isEqualTo("http://localhost:4318/v1/traces");
        assertThat(NeonBeeOpenTelemetry.tracesEndpoint("http://localhost:4318/v1/traces"))
                .isEqualTo("http://localhost:4318/v1/traces");
    }

    @Test
    @DisplayName("close should be safe when no SDK was registered")
    void testCloseWithoutRegistration() {
        // must not throw
        NeonBeeOpenTelemetry.close(null);
    }

    @Test
    @DisplayName("bridgeConfigToOptions should apply the file config when options carry no endpoint")
    void testBridgeAppliesFileConfig() {
        NeonBeeOptions.Mutable options = new NeonBeeOptions.Mutable().setInstanceName("test-instance");
        NeonBeeConfig config = new NeonBeeConfig().setTracingConfig(new TracingConfig().setEnabled(true)
                .setOtlpEndpoint("http://localhost:4318").setServiceName("svc"));

        NeonBeeOpenTelemetry.bridgeConfigToOptions(options, config);

        assertThat(options.getTracingConfig().isEnabled()).isTrue();
        assertThat(options.getTracingConfig().getOtlpEndpoint()).isEqualTo("http://localhost:4318");
        assertThat(options.getTracingConfig().getServiceName()).isEqualTo("svc");
    }

    @Test
    @DisplayName("bridgeConfigToOptions should not override an endpoint already set on the options")
    void testBridgeKeepsOptionsEndpoint() {
        NeonBeeOptions.Mutable options = new NeonBeeOptions.Mutable().setInstanceName("test-instance")
                .setTracingConfig(new TracingConfig().setEnabled(true).setOtlpEndpoint("http://options:4318"));
        NeonBeeConfig config = new NeonBeeConfig()
                .setTracingConfig(new TracingConfig().setEnabled(true).setOtlpEndpoint("http://file:4318"));

        NeonBeeOpenTelemetry.bridgeConfigToOptions(options, config);

        assertThat(options.getTracingConfig().getOtlpEndpoint()).isEqualTo("http://options:4318");
    }

    @Test
    @DisplayName("bridgeConfigToOptions should preserve a CLI force-enable even if the file config is disabled")
    void testBridgePreservesCliForceEnable() {
        NeonBeeOptions.Mutable options = new NeonBeeOptions.Mutable().setInstanceName("test-instance");
        options.setTracingEnabled(true); // simulates --enable-tracing
        NeonBeeConfig config = new NeonBeeConfig()
                .setTracingConfig(new TracingConfig().setEnabled(false).setOtlpEndpoint("http://localhost:4318"));

        NeonBeeOpenTelemetry.bridgeConfigToOptions(options, config);

        assertThat(options.getTracingConfig().isEnabled()).isTrue();
        assertThat(options.getTracingConfig().getOtlpEndpoint()).isEqualTo("http://localhost:4318");
    }

    @Test
    @DisplayName("bridgeConfigToOptions should be a no-op when config is null")
    void testBridgeNullConfig() {
        NeonBeeOptions.Mutable options = new NeonBeeOptions.Mutable().setInstanceName("test-instance");
        NeonBeeOpenTelemetry.bridgeConfigToOptions(options, null);
        assertThat(options.getTracingConfig().isEnabled()).isFalse();
    }

    @Test
    @DisplayName("ensureContextStorageProvider should set the Vert.x provider when unset")
    void testEnsureContextStorageProvider() {
        String previous = System.getProperty(NeonBeeOpenTelemetry.CONTEXT_STORAGE_PROVIDER_PROPERTY);
        try {
            System.clearProperty(NeonBeeOpenTelemetry.CONTEXT_STORAGE_PROVIDER_PROPERTY);
            NeonBeeOpenTelemetry.ensureContextStorageProvider();
            assertThat(System.getProperty(NeonBeeOpenTelemetry.CONTEXT_STORAGE_PROVIDER_PROPERTY))
                    .isEqualTo(NeonBeeOpenTelemetry.VERTX_CONTEXT_STORAGE_PROVIDER);
        } finally {
            if (previous == null) {
                System.clearProperty(NeonBeeOpenTelemetry.CONTEXT_STORAGE_PROVIDER_PROPERTY);
            } else {
                System.setProperty(NeonBeeOpenTelemetry.CONTEXT_STORAGE_PROVIDER_PROPERTY, previous);
            }
        }
    }
}
