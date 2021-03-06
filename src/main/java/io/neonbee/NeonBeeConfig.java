package io.neonbee;

import static io.neonbee.internal.Helper.readConfigBlocking;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import com.google.common.annotations.VisibleForTesting;

import io.neonbee.internal.tracking.TrackingDataLoggingStrategy;
import io.neonbee.logging.LoggingFacade;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

public class NeonBeeConfig {
    /**
     * The default timeout for an event bus request.
     */
    public static final int DEFAULT_EVENT_BUS_TIMEOUT = 30;

    @VisibleForTesting
    static final String PLATFORM_CLASSES_KEY = "platformClasses";

    private static final LoggingFacade LOGGER = LoggingFacade.create();

    private static final String DEFAULT_TRACKING_DATA_HANDLING_STRATEGY = TrackingDataLoggingStrategy.class.getName();

    private int eventBusTimeout = DEFAULT_EVENT_BUS_TIMEOUT;

    private final List<String> platformClasses;

    private final String trackingDataHandlingStrategy;

    private final Map<String, String> eventBusCodecs;

    /**
     * Package scoped default constructor.
     * <p>
     * Should never be initialized by anyone, but only retrieved via neonbee.getConfig()
     */
    NeonBeeConfig(Vertx vertx) {
        this(readConfigBlocking(vertx, NeonBee.class.getName(), new JsonObject()));
    }

    /**
     * Create a NeonBee config from JSON.
     *
     * @param json the JSON
     */
    @VisibleForTesting
    NeonBeeConfig(JsonObject json) {
        this.eventBusTimeout = json.getInteger("eventBusTimeout", DEFAULT_EVENT_BUS_TIMEOUT);
        this.eventBusCodecs = json.getJsonObject("eventBusCodecs", new JsonObject()).stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> (String) entry.getValue()));
        this.trackingDataHandlingStrategy =
                json.getString("trackingDataHandlingStrategy", DEFAULT_TRACKING_DATA_HANDLING_STRATEGY);
        this.platformClasses = Optional.ofNullable(json.getJsonArray(PLATFORM_CLASSES_KEY))
                .map(jsonArray -> jsonArray.stream().map(o -> {
                    if (o instanceof String) {
                        return (String) o;
                    } else {
                        LOGGER.warn(
                                "The attribute \"platformClasses\" of the NeonBee config must only contain Strings. Because of this value {} will be ignored.",
                                String.valueOf(o));
                        return null;
                    }
                }).filter(Objects::nonNull).collect(Collectors.toList())).orElse(List.of());
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
     * Returns the implementation class name of the tracking data handling strategy.
     *
     * @return the implementation class name
     */
    public String getTrackingDataHandlingStrategy() {
        return trackingDataHandlingStrategy;
    }

    /**
     * The idea of this method is to define, which classes are considered as platform classes. This is important to know
     * to avoid potential class loading issues during the load of a NeonBee Module. Because generally a class which is
     * already loaded by the platform shouldn't also be loaded by the class loader which loads the classes for the
     * NeonBee Module.<br>
     * <br>
     *
     * Example values: [io.vertx.core.json.JsonObject, io.neonbee.data*, io.neo*]
     *
     * @return a list of Strings that could contain full qualified class names, or prefixes marked with a *;
     */
    public List<String> getPlatformClasses() {
        return platformClasses;
    }
}
