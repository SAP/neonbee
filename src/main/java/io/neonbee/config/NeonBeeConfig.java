package io.neonbee.config;

import static io.neonbee.internal.helper.ConfigHelper.readConfig;

import java.util.List;
import java.util.Map;

import io.neonbee.NeonBee;
import io.neonbee.NeonBeeOptions;
import io.neonbee.internal.tracking.TrackingDataLoggingStrategy;
import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.annotations.Fluent;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

/**
 * In contrast to the {@link NeonBeeOptions} the {@link NeonBeeConfig} is persistent configuration in a file.
 *
 * Whilst the {@link NeonBeeOptions} contain information which is to specify when NeonBee starts, such as the port of
 * the server to start on and the cluster to connect to, which potentially could be different across cluster nodes, the
 * {@link NeonBeeConfig} contains information which is mostly shared across different cluster nodes or you would like to
 * decide for and set before starting up NeonBee.
 */
@DataObject(generateConverter = true, publicConverter = false)
public class NeonBeeConfig {
    /**
     * The default timeout for an event bus request.
     */
    public static final int DEFAULT_EVENT_BUS_TIMEOUT = 30;

    public static final String DEFAULT_TRACKING_DATA_HANDLING_STRATEGY = TrackingDataLoggingStrategy.class.getName();

    private int eventBusTimeout = DEFAULT_EVENT_BUS_TIMEOUT;

    private Map<String, String> eventBusCodecs;

    private String trackingDataHandlingStrategy = DEFAULT_TRACKING_DATA_HANDLING_STRATEGY;

    private List<String> platformClasses;

    public static Future<NeonBeeConfig> load(Vertx vertx) {
        return readConfig(vertx, NeonBee.class.getName(), new JsonObject()).map(NeonBeeConfig::new);
    }

    /**
     * Creates an initial NeonBee configuration object
     */
    public NeonBeeConfig() {}

    /**
     * Creates a {@linkplain NeonBeeConfig} parsing a given JSON object
     *
     * @param json the JSON object to parse
     */
    public NeonBeeConfig(JsonObject json) {
        this();

        NeonBeeConfigConverter.fromJson(json, this);
    }

    /**
     * Transforms this configuration object into JSON
     *
     * @return a JSON representation of this configuration
     */
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        NeonBeeConfigConverter.toJson(this, json);
        return json;
    }

    /**
     * Get the event bus timeout in seconds.
     * <p>
     * When a message is sent via the event bus (e.g. when calling data verticle) a timeout is applied if not specified
     * when sending the message this configuration will apply
     *
     * @return the value of send timeout
     */
    public int getEventBusTimeout() {
        return eventBusTimeout;
    }

    /**
     * Sets the event bus timeout in seconds.
     *
     * @param eventBusTimeout the event bus timeout in seconds
     * @return the {@linkplain NeonBeeConfig} for fluent use
     */
    @Fluent
    public NeonBeeConfig setEventBusTimeout(int eventBusTimeout) {
        this.eventBusTimeout = eventBusTimeout;
        return this;
    }

    /**
     * Get a list of default codecs to register on the event bus.
     * <p>
     * When a message is sent via the event bus (e.g. when calling data verticle) codecs will be used to transform the
     * object returned by the verticle to the event bus. Data verticle offer a codec mechanism for them to register /
     * announce new codecs to be used. This configuration can be used to register default codecs which will apply for
     * every object of a certain type.
     *
     * @return the map of default codecs to use
     */
    public Map<String, String> getEventBusCodecs() {
        return eventBusCodecs;
    }

    /**
     * Sets the event bus codecs to be loaded with NeonBee.
     *
     * @param eventBusCodecs a map of default codes to use
     * @return the {@linkplain NeonBeeConfig} for fluent use
     */
    @Fluent
    public NeonBeeConfig setEventBusCodecs(Map<String, String> eventBusCodecs) {
        this.eventBusCodecs = eventBusCodecs;
        return this;
    }

    /**
     * Returns the implementation class name of the tracking data handling strategy.
     *
     * @return the implementation class name
     */
    public String getTrackingDataHandlingStrategy() {
        return trackingDataHandlingStrategy;
    }

    /**
     * Sets the class to load for the tracking data handling
     *
     * @param trackingDataHandlingStrategy a class name of a tracking data handling class
     * @return the {@linkplain NeonBeeConfig} for fluent use
     */
    @Fluent
    public NeonBeeConfig setTrackingDataHandlingStrategy(String trackingDataHandlingStrategy) {
        this.trackingDataHandlingStrategy = trackingDataHandlingStrategy;
        return this;
    }

    /**
     * The idea of this method is to define, which classes are considered as platform classes. This is important to know
     * to avoid potential class loading issues during the load of a NeonBee Module. Because generally a class which is
     * already loaded by the platform shouldn't also be loaded by the class loader which loads the classes for the
     * NeonBee Module.<br>
     * <br>
     *
     * Example values: [io.vertx.core.json.JsonObject, io.neonbee.data*, com.foo.bar*]
     *
     * @return a list of Strings that could contain full qualified class names, or prefixes marked with a *;
     */
    public List<String> getPlatformClasses() {
        return platformClasses;
    }

    /**
     * Sets classes available by the platform
     *
     * @param platformClasses a list of class names
     * @return the {@linkplain NeonBeeConfig} for fluent use
     */
    @Fluent
    public NeonBeeConfig setPlatformClasses(List<String> platformClasses) {
        this.platformClasses = platformClasses;
        return this;
    }
}
