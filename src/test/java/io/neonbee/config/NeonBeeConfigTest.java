package io.neonbee.config;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.config.NeonBeeConfig.DEFAULT_EVENT_BUS_TIMEOUT;
import static io.neonbee.config.NeonBeeConfig.DEFAULT_TIME_ZONE;
import static io.neonbee.config.NeonBeeConfig.DEFAULT_TRACKING_DATA_HANDLING_STRATEGY;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import io.neonbee.test.base.NeonBeeTestBase;
import io.neonbee.test.helper.WorkingDirectoryBuilder;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxTestContext;

class NeonBeeConfigTest extends NeonBeeTestBase {
    private static final int DUMMY_EVENT_BUS_TIMEOUT = 1337;

    private static final String DUMMY_TRACKING_DATA_HANDLING_STRATEGY = "Hodor";

    private static final String DUMMY_TIME_ZONE = "Hammer Time";

    private static final Map<String, String> DUMMY_EVENT_BUS_CODECS = Map.of("Random", "Codec");

    private static final List<String> DUMMY_PLATFORM_CLASSES = List.of("Hodor");

    private static final NeonBeeConfig DUMMY_NEONBEE_CONFIG =
            new NeonBeeConfig().setEventBusTimeout(DUMMY_EVENT_BUS_TIMEOUT)
                    .setTrackingDataHandlingStrategy(DUMMY_TRACKING_DATA_HANDLING_STRATEGY).setTimeZone(DUMMY_TIME_ZONE)
                    .setEventBusCodecs(DUMMY_EVENT_BUS_CODECS).setPlatformClasses(DUMMY_PLATFORM_CLASSES);

    @Override
    protected WorkingDirectoryBuilder provideWorkingDirectoryBuilder(TestInfo testInfo, VertxTestContext testContext) {
        if ("testLoad".equals(testInfo.getTestMethod().map(Method::getName).orElse(null))) {
            return WorkingDirectoryBuilder.standard().setNeonBeeConfig(DUMMY_NEONBEE_CONFIG);
        } else {
            return super.provideWorkingDirectoryBuilder(testInfo, testContext);
        }
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("should load NeonBeeConfig correctly from working dir")
    void testLoad(Vertx vertx, VertxTestContext testContext) {
        NeonBeeConfig.load(vertx).onComplete(testContext.succeeding(nbc -> {
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
    @DisplayName("should read the trackingDataHandlingStrategy correctly")
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
    @DisplayName("should have the correct default values")
    void testDefaultValues() {
        NeonBeeConfig defaultConfig = new NeonBeeConfig();
        assertThat(defaultConfig.getEventBusTimeout()).isEqualTo(DEFAULT_EVENT_BUS_TIMEOUT);
        assertThat(defaultConfig.getTrackingDataHandlingStrategy()).isEqualTo(DEFAULT_TRACKING_DATA_HANDLING_STRATEGY);
        assertThat(defaultConfig.getTimeZone()).isEqualTo(DEFAULT_TIME_ZONE);
        assertThat(defaultConfig.getEventBusCodecs()).isEmpty();
        assertThat(defaultConfig.getPlatformClasses()).isEmpty();
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
    }
}
