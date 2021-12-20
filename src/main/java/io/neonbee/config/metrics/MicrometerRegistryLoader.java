package io.neonbee.config.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.vertx.core.json.JsonObject;
import io.vertx.micrometer.MicrometerMetricsOptions;

/**
 * Interface to implement if you want to add a {@link MeterRegistry} to the {@link MicrometerMetricsOptions}.
 * <p>
 * The implementing class MUST have a constructor without any arguments.
 */
@FunctionalInterface
public interface MicrometerRegistryLoader {

    /**
     * This method is called to load the {@link MeterRegistry} Object.
     *
     * @param config the configuration as a {@link JsonObject}. The config object can be null.
     * @return the {@link MeterRegistry} to add to the {@link MicrometerMetricsOptions}
     */
    MeterRegistry load(JsonObject config);
}
