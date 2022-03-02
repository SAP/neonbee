package io.neonbee.config.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
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

    /**
     * Executes the launcher pre-processor.
     *
     * @param vertx   {@link Vertx} instance
     * @param config  the configuration as a {@link JsonObject}. The config object can be null.
     * @param promise a promise which should be called when the MicrometerRegistryLoader is complete.
     */
    default void load(Vertx vertx, JsonObject config, Promise<MeterRegistry> promise) {
        promise.complete(load(config));
    }
}
