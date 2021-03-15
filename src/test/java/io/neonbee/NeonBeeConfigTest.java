package io.neonbee;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.NeonBeeConfig.PLATFORM_CLASSES_KEY;

import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

class NeonBeeConfigTest {

    @Test
    @DisplayName("should read the trackingDataHandlingStrategy correctly")
    void readtrackingDataHandlingStrategy() {
        NeonBeeConfig config = new NeonBeeConfig(new JsonObject().put("trackingDataHandlingStrategy", "ABC"));
        assertThat(config.getTrackingDataHandlingStrategy()).isEqualTo("ABC");
    }

    @Test
    @DisplayName("should read the trackingDataHandlingStrategy correctly")
    void getPlatformClassesTest() {
        List<String> validListOfPlatformClasses = List.of("hodor");
        List<Object> nonValidListOfPlatformClasses = List.of("hodor", 3);

        Function<List<?>, JsonObject> createConfig =
                list -> new JsonObject().put(PLATFORM_CLASSES_KEY, new JsonArray(list));

        NeonBeeConfig config = new NeonBeeConfig(createConfig.apply(validListOfPlatformClasses));
        assertThat(config.getPlatformClasses()).containsExactlyElementsIn(validListOfPlatformClasses);

        config = new NeonBeeConfig(createConfig.apply(nonValidListOfPlatformClasses));
        assertThat(config.getPlatformClasses()).containsExactlyElementsIn(validListOfPlatformClasses);
    }
}
