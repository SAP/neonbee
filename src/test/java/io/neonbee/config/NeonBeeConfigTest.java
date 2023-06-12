package io.neonbee.config;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.config.NeonBeeConfig.DEFAULT_EVENT_BUS_TIMEOUT;
import static io.neonbee.config.NeonBeeConfig.DEFAULT_TIME_ZONE;
import static io.neonbee.config.NeonBeeConfig.DEFAULT_TRACKING_DATA_HANDLING_STRATEGY;
import static io.vertx.core.CompositeFuture.all;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.neonbee.config.metrics.MicrometerRegistryLoader;
import io.neonbee.test.base.NeonBeeTestBase;
import io.neonbee.test.helper.WorkingDirectoryBuilder;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxTestContext;

class NeonBeeConfigTest extends NeonBeeTestBase {
    private static final int DUMMY_EVENT_BUS_TIMEOUT = 1337;

    private static final String DUMMY_TRACKING_DATA_HANDLING_STRATEGY = "Hodor";

    private static final String DUMMY_TIME_ZONE = "Hammer Time";

    private static final Map<String, String> DUMMY_EVENT_BUS_CODECS = Map.of("Random", "Codec");

    private static final List<String> DUMMY_PLATFORM_CLASSES = List.of("Hodor");

    private static final List<MicrometerRegistryConfig> DUMMY_MICROMETER_REGISTRIES = List.of();

    private static final NeonBeeConfig DUMMY_NEONBEE_CONFIG =
            new NeonBeeConfig().setEventBusTimeout(DUMMY_EVENT_BUS_TIMEOUT)
                    .setTrackingDataHandlingStrategy(DUMMY_TRACKING_DATA_HANDLING_STRATEGY).setTimeZone(DUMMY_TIME_ZONE)
                    .setEventBusCodecs(DUMMY_EVENT_BUS_CODECS).setPlatformClasses(DUMMY_PLATFORM_CLASSES)
                    .setMicrometerRegistries(DUMMY_MICROMETER_REGISTRIES);

    @Override
    protected WorkingDirectoryBuilder provideWorkingDirectoryBuilder(TestInfo testInfo, VertxTestContext testContext) {
        if ("testLoad".equals(testInfo.getTestMethod().map(Method::getName).orElse(null))) {
            return WorkingDirectoryBuilder.standard().setNeonBeeConfig(DUMMY_NEONBEE_CONFIG);
        } else {
            return super.provideWorkingDirectoryBuilder(testInfo, testContext);
        }
    }

    @Test
    @DisplayName("Test loading the MeterRegistry")
    void testLoadingMeterRegistry(Vertx vertx, VertxTestContext context) throws Exception {
        NeonBeeConfig config = new NeonBeeConfig();
        CompositeMeterRegistry registry = new CompositeMeterRegistry();
        config.setMicrometerRegistries(List.of(new MicrometerRegistryConfig()
                .setClassName(TestMicrometerRegistryLoaderImpl.class.getName()).setConfig(new JsonObject())));

        all(config.createMicrometerRegistries(vertx).collect(Collectors.toList()))
                .onSuccess(h -> h.<MeterRegistry>list().forEach(registry::add)).onComplete(context.succeeding(event -> {
                    Set<MeterRegistry> registries = registry.getRegistries();
                    assertThat(registries).hasSize(1);
                    assertThat(registries.stream().anyMatch(PrometheusMeterRegistry.class::isInstance)).isTrue();
                    context.completeNow();
                }));
    }

    static Stream<Arguments> testNotImplementingMicrometerRegistryLoaderArguments() {
        return Stream.of(
                Arguments.of("java.lang.String",
                        "java.lang.String must implement io.neonbee.config.metrics.MicrometerRegistryLoader",
                        IllegalArgumentException.class),
                Arguments.of("doesn't exist", "doesn't exist", ClassNotFoundException.class),
                Arguments.of("io.neonbee.config.NeonBeeConfigTest$TestFaultyMicrometerRegistryLoaderImpl",
                        "io.neonbee.config.NeonBeeConfigTest$TestFaultyMicrometerRegistryLoaderImpl.<init>()",
                        NoSuchMethodException.class));
    }

    @ParameterizedTest(name = "{index}: {0} expected exception message: {1}")
    @MethodSource("testNotImplementingMicrometerRegistryLoaderArguments")
    @DisplayName("Test MicrometerRegistryLoader with incorrect configuration")
    void testNotImplementingMicrometerRegistryLoader(String className, String exceptionMessage,
            Class<? extends Throwable> expectedException, Vertx vertx, VertxTestContext context) {
        NeonBeeConfig config = new NeonBeeConfig();
        config.setMicrometerRegistries(List.of(new MicrometerRegistryConfig().setClassName(className)));

        all(config.createMicrometerRegistries(vertx).collect(Collectors.toList()))
                .onComplete(context.failing(throwable -> {
                    assertThat(throwable).isInstanceOf(expectedException);
                    assertThat(throwable).hasMessageThat().isEqualTo(exceptionMessage);
                    context.completeNow();
                }));
    }

    @Test
    @DisplayName("should load NeonBeeConfig correctly from working dir")
    void testLoad(Vertx vertx, VertxTestContext testContext) {

        NeonBeeConfig.load(vertx, getNeonBee().getOptions().getConfigDirectory())
                .onComplete(testContext.succeeding(nbc -> {
                    testContext.verify(() -> isEqualToDummyConfig(nbc));
                    testContext.completeNow();
                }));
    }

    @Test
    @DisplayName("should read the trackingDataHandlingStrategy correctly")
    void readtrackingDataHandlingStrategy() {
        NeonBeeConfig config = new NeonBeeConfig(new JsonObject().put("trackingDataHandlingStrategy", "ABC"));
        assertThat(config.getTrackingDataHandlingStrategy()).isEqualTo("ABC");
    }

    @Test
    @DisplayName("should read the metrics configuration correctly")
    void readMetricsConfig() {
        NeonBeeConfig config =
                new NeonBeeConfig(new JsonObject().put("metrics", new JsonObject().put("enabled", true)));
        assertThat(config.getMetricsConfig().isEnabled()).isTrue();
    }

    @Test
    @DisplayName("should create the metrics configuration JSON correctly")
    void metricsConfigToJson() {
        NeonBeeConfig config = new NeonBeeConfig();
        config.getMetricsConfig().setEnabled(true);
        JsonObject actual = config.toJson();
        assertThat(actual.getJsonObject("metrics")).isEqualTo(new JsonObject().put("enabled", true));
    }

    @Test
    @DisplayName("should read the platform classes correctly")
    void testGetPlatformClasses() {
        List<String> validListOfPlatformClasses = List.of("hodor");
        List<Object> nonValidListOfPlatformClasses = List.of("hodor", 3);

        Function<List<?>, JsonObject> createConfig =
                list -> new JsonObject().put("platformClasses", new JsonArray(list));

        NeonBeeConfig config = new NeonBeeConfig(createConfig.apply(validListOfPlatformClasses));
        assertThat(config.getPlatformClasses()).containsExactlyElementsIn(validListOfPlatformClasses);

        config = new NeonBeeConfig(createConfig.apply(nonValidListOfPlatformClasses));
        assertThat(config.getPlatformClasses()).containsExactlyElementsIn(validListOfPlatformClasses);
    }

    @Test
    @DisplayName("should read the health config correctly")
    void testReadHealthConfig() {
        JsonObject settings = new JsonObject();
        Function<Boolean, JsonObject> buildConfig =
                enabled -> settings.put("health", new JsonObject().put("enabled", enabled));

        NeonBeeConfig neonBeeConfig = new NeonBeeConfig(buildConfig.apply(true));
        assertThat(neonBeeConfig.getHealthConfig().isEnabled()).isTrue();

        neonBeeConfig = new NeonBeeConfig(buildConfig.apply(false));
        assertThat(neonBeeConfig.getHealthConfig().isEnabled()).isFalse();
    }

    @Test
    @DisplayName("should create the metrics config correctly")
    void testHealthConfigToJson() {
        NeonBeeConfig config = new NeonBeeConfig();
        config.getHealthConfig().setEnabled(false);
        JsonObject actual = config.toJson();

        assertThat(actual.getJsonObject("health")).isEqualTo(new JsonObject().put("enabled", false).put("timeout", 1));
    }

    @Test
    @DisplayName("should have the correct default values")
    void testDefaultValues() {
        NeonBeeConfig defaultConfig = new NeonBeeConfig();
        assertThat(defaultConfig.getEventBusTimeout()).isEqualTo(DEFAULT_EVENT_BUS_TIMEOUT);
        assertThat(defaultConfig.getTrackingDataHandlingStrategy()).isEqualTo(DEFAULT_TRACKING_DATA_HANDLING_STRATEGY);
        assertThat(defaultConfig.getTimeZone()).isEqualTo(DEFAULT_TIME_ZONE);
        assertThat(defaultConfig.getEventBusCodecs()).isEmpty();
        assertThat(defaultConfig.getPlatformClasses()).containsExactly("io.vertx.*", "io.neonbee.*", "org.slf4j.*",
                "org.apache.olingo.*");
        assertThat(defaultConfig.getHealthConfig().isEnabled()).isTrue();
        assertThat(defaultConfig.getHealthConfig().getTimeout()).isEqualTo(1);
    }

    @Test
    @DisplayName("setters should work as expected")
    void testSetters() {
        isEqualToDummyConfig(DUMMY_NEONBEE_CONFIG);
    }

    private void isEqualToDummyConfig(NeonBeeConfig nbc) {
        assertThat(nbc.getEventBusTimeout()).isEqualTo(DUMMY_EVENT_BUS_TIMEOUT);
        assertThat(nbc.getTrackingDataHandlingStrategy()).isEqualTo(DUMMY_TRACKING_DATA_HANDLING_STRATEGY);
        assertThat(nbc.getTimeZone()).isEqualTo(DUMMY_TIME_ZONE);
        assertThat(nbc.getEventBusCodecs()).isEqualTo(DUMMY_EVENT_BUS_CODECS);
        assertThat(nbc.getPlatformClasses()).isEqualTo(DUMMY_PLATFORM_CLASSES);
        assertThat(nbc.getMicrometerRegistries()).isEqualTo(DUMMY_MICROMETER_REGISTRIES);
    }

    public static class TestMicrometerRegistryLoaderImpl implements MicrometerRegistryLoader {

        @Override
        public MeterRegistry load(JsonObject config) {
            return new PrometheusMeterRegistry(config::getString);
        }
    }

    public static class TestFaultyMicrometerRegistryLoaderImpl implements MicrometerRegistryLoader {

        @SuppressWarnings("PMD.UnusedFormalParameter")
        TestFaultyMicrometerRegistryLoaderImpl(String required) {}

        @Override
        public MeterRegistry load(JsonObject config) {
            return new PrometheusMeterRegistry(config::getString);
        }
    }
}
