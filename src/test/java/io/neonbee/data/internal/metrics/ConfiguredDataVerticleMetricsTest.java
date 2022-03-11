package io.neonbee.data.internal.metrics;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import io.micrometer.core.instrument.ImmutableTag;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.micrometer.backends.BackendRegistries;

class ConfiguredDataVerticleMetricsTest {

    @Test
    @DisplayName("Test null config")
    void nullConfig() {
        DataVerticleMetrics instance = ConfiguredDataVerticleMetrics.configureMetricsReporting(null);
        assertThat(instance).isInstanceOf(NoopDataVerticleMetrics.class);
    }

    @Test
    @DisplayName("Test no config")
    void noConfig() {
        DataVerticleMetrics instance = ConfiguredDataVerticleMetrics.configureMetricsReporting(new JsonObject());
        assertThat(instance).isInstanceOf(NoopDataVerticleMetrics.class);
    }

    @Test
    @DisplayName("Test metrics disabled")
    void disabled() {
        JsonObject config = new JsonObject().put(ConfiguredDataVerticleMetrics.ENABLED, false);
        DataVerticleMetrics instance = ConfiguredDataVerticleMetrics.configureMetricsReporting(config);
        assertThat(instance).isInstanceOf(NoopDataVerticleMetrics.class);
    }

    @Test
    @DisplayName("Test metrics disabled, enabled is null")
    void disabledNull() {
        JsonObject config = new JsonObject().put(ConfiguredDataVerticleMetrics.ENABLED, null);
        DataVerticleMetrics instance = ConfiguredDataVerticleMetrics.configureMetricsReporting(config);
        assertThat(instance).isInstanceOf(NoopDataVerticleMetrics.class);
    }

    @Test
    @DisplayName("backend meter registry is null")
    void backendMeterRegistriesNull() {
        JsonObject config = new JsonObject().put(ConfiguredDataVerticleMetrics.ENABLED, true);

        DataVerticleMetrics instance = ConfiguredDataVerticleMetrics.configureMetricsReporting(config);
        assertThat(instance).isInstanceOf(NoopDataVerticleMetrics.class);
    }

    @Test
    @DisplayName("Test minimal config, all metrics should be reported")
    void minimalEnabled() {
        JsonObject config = new JsonObject().put(ConfiguredDataVerticleMetrics.ENABLED, true);

        MeterRegistry mockRegistry = mock(MeterRegistry.class);
        try (MockedStatic<BackendRegistries> registry = mockStatic(BackendRegistries.class)) {
            registry.when(() -> BackendRegistries.getNow(anyString())).thenReturn(mockRegistry);

            DataVerticleMetrics instance = ConfiguredDataVerticleMetrics.configureMetricsReporting(config);
            assertThat(instance).isInstanceOf(DataVerticleMetricsImpl.class);
        }
    }

    @Test
    @DisplayName("Test config with all values provided")
    void configWithAllValuesEnabled() {
        JsonObject config = new JsonObject().put(ConfiguredDataVerticleMetrics.ENABLED, true)
                .put(ConfiguredDataVerticleMetrics.METER_REGISTRY_NAME, "SomeRegistryName")
                .put(ConfiguredDataVerticleMetrics.NUMBER_OF_REQUESTS, true)
                .put(ConfiguredDataVerticleMetrics.ACTIVE_REQUESTS, true)
                .put(ConfiguredDataVerticleMetrics.STATUS_COUNTER, true)
                .put(ConfiguredDataVerticleMetrics.TIMING, true);

        MeterRegistry mockRegistry = mock(MeterRegistry.class);
        try (MockedStatic<BackendRegistries> registry = mockStatic(BackendRegistries.class)) {
            registry.when(() -> BackendRegistries.getNow(anyString())).thenReturn(mockRegistry);

            DataVerticleMetrics instance = ConfiguredDataVerticleMetrics.configureMetricsReporting(config);
            assertThat(instance).isInstanceOf(ConfiguredDataVerticleMetrics.class);
            ConfiguredDataVerticleMetrics configuredInstance = (ConfiguredDataVerticleMetrics) instance;

            assertThat(configuredInstance.reportNumberOfRequests).isInstanceOf(DataVerticleMetricsImpl.class);
            assertThat(configuredInstance.reportActiveRequestsGauge).isInstanceOf(DataVerticleMetricsImpl.class);
            assertThat(configuredInstance.reportStatusCounter).isInstanceOf(DataVerticleMetricsImpl.class);
            assertThat(configuredInstance.reportTimingMetric).isInstanceOf(DataVerticleMetricsImpl.class);
        }
    }

    @Test
    @DisplayName("Test config with all values provided but disabled")
    void configWithAllValuesDisabled() {
        JsonObject config = new JsonObject().put(ConfiguredDataVerticleMetrics.ENABLED, true)
                .put(ConfiguredDataVerticleMetrics.METER_REGISTRY_NAME, "SomeRegistryName")
                .put(ConfiguredDataVerticleMetrics.NUMBER_OF_REQUESTS, false)
                .put(ConfiguredDataVerticleMetrics.ACTIVE_REQUESTS, false)
                .put(ConfiguredDataVerticleMetrics.STATUS_COUNTER, false)
                .put(ConfiguredDataVerticleMetrics.TIMING, false);

        MeterRegistry mockRegistry = mock(MeterRegistry.class);
        try (MockedStatic<BackendRegistries> registry = mockStatic(BackendRegistries.class)) {
            registry.when(() -> BackendRegistries.getNow(anyString())).thenReturn(mockRegistry);

            DataVerticleMetrics instance = ConfiguredDataVerticleMetrics.configureMetricsReporting(config);
            assertThat(instance).isInstanceOf(ConfiguredDataVerticleMetrics.class);
            ConfiguredDataVerticleMetrics configuredInstance = (ConfiguredDataVerticleMetrics) instance;

            assertThat(configuredInstance.reportNumberOfRequests).isInstanceOf(NoopDataVerticleMetrics.class);
            assertThat(configuredInstance.reportActiveRequestsGauge).isInstanceOf(NoopDataVerticleMetrics.class);
            assertThat(configuredInstance.reportStatusCounter).isInstanceOf(NoopDataVerticleMetrics.class);
            assertThat(configuredInstance.reportTimingMetric).isInstanceOf(NoopDataVerticleMetrics.class);

            List<Tag> tags = List.of(new ImmutableTag("key", "value"));

            configuredInstance.reportNumberOfRequests("name", "description", tags);
            configuredInstance.reportActiveRequestsGauge("name", "description", tags, Future.succeededFuture());
            configuredInstance.reportStatusCounter("name", "description", tags, Future.succeededFuture());
            configuredInstance.reportTimingMetric("name", "description", tags, Future.succeededFuture());
        }
    }

    @Test
    @DisplayName("Test invocations")
    void invocations() {
        NoopDataVerticleMetrics spyReportNumberOfRequests = spy(ConfiguredDataVerticleMetrics.DUMMY_IMPL);
        NoopDataVerticleMetrics spyReportActiveRequestsGauge = spy(ConfiguredDataVerticleMetrics.DUMMY_IMPL);
        NoopDataVerticleMetrics spyReportStatusCounter = spy(ConfiguredDataVerticleMetrics.DUMMY_IMPL);
        NoopDataVerticleMetrics spyReportTimingMetric = spy(ConfiguredDataVerticleMetrics.DUMMY_IMPL);

        ConfiguredDataVerticleMetrics configuredInstance = new ConfiguredDataVerticleMetrics(spyReportNumberOfRequests,
                spyReportActiveRequestsGauge, spyReportStatusCounter, spyReportTimingMetric);

        List<Tag> tags = List.of(new ImmutableTag("key", "value"));

        configuredInstance.reportNumberOfRequests("name", "description", tags);
        configuredInstance.reportActiveRequestsGauge("name", "description", tags, Future.succeededFuture());
        configuredInstance.reportStatusCounter("name", "description", tags, Future.succeededFuture());
        configuredInstance.reportTimingMetric("name", "description", tags, Future.succeededFuture());

        verify(spyReportNumberOfRequests, times(1)).reportNumberOfRequests(eq("name"), eq("description"), eq(tags));
        verify(spyReportNumberOfRequests, never()).reportActiveRequestsGauge(eq("name"), eq("description"), eq(tags),
                any());
        verify(spyReportNumberOfRequests, never()).reportStatusCounter(eq("name"), eq("description"), eq(tags), any());
        verify(spyReportNumberOfRequests, never()).reportTimingMetric(eq("name"), eq("description"), eq(tags), any());

        verify(spyReportActiveRequestsGauge, times(1)).reportActiveRequestsGauge(eq("name"), eq("description"),
                eq(tags), any());
        verify(spyReportActiveRequestsGauge, never()).reportNumberOfRequests(eq("name"), eq("description"), eq(tags));
        verify(spyReportActiveRequestsGauge, never()).reportStatusCounter(eq("name"), eq("description"), eq(tags),
                any());
        verify(spyReportActiveRequestsGauge, never()).reportTimingMetric(eq("name"), eq("description"), eq(tags),
                any());

        verify(spyReportStatusCounter, times(1)).reportStatusCounter(eq("name"), eq("description"), eq(tags), any());
        verify(spyReportStatusCounter, never()).reportNumberOfRequests(eq("name"), eq("description"), eq(tags));
        verify(spyReportStatusCounter, never()).reportActiveRequestsGauge(eq("name"), eq("description"), eq(tags),
                any());
        verify(spyReportStatusCounter, never()).reportTimingMetric(eq("name"), eq("description"), eq(tags), any());

        verify(spyReportTimingMetric, times(1)).reportTimingMetric(eq("name"), eq("description"), eq(tags), any());
        verify(spyReportTimingMetric, never()).reportNumberOfRequests(eq("name"), eq("description"), eq(tags));
        verify(spyReportTimingMetric, never()).reportActiveRequestsGauge(eq("name"), eq("description"), eq(tags),
                any());
        verify(spyReportTimingMetric, never()).reportStatusCounter(eq("name"), eq("description"), eq(tags), any());
    }

}
