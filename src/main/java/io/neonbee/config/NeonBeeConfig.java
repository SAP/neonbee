package io.neonbee.config;

import static io.neonbee.internal.helper.ConfigHelper.notFound;
import static io.neonbee.internal.helper.ConfigHelper.readConfig;
import static io.neonbee.internal.helper.ConfigHelper.rephraseConfigNames;
import static io.vertx.core.Future.future;
import static io.vertx.core.Future.succeededFuture;

import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.google.common.collect.ImmutableBiMap;

import io.micrometer.core.instrument.MeterRegistry;
import io.neonbee.NeonBee;
import io.neonbee.NeonBeeOptions;
import io.neonbee.config.metrics.MicrometerRegistryLoader;
import io.neonbee.internal.tracking.TrackingDataLoggingStrategy;
import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.annotations.Fluent;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.metrics.MetricsOptions;

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

    /**
     * The default tracking data handling strategy.
     */
    public static final String DEFAULT_TRACKING_DATA_HANDLING_STRATEGY = TrackingDataLoggingStrategy.class.getName();

    /**
     * The default timezone to use e.g. for logging. Defaults to UTC.
     */
    public static final String DEFAULT_TIME_ZONE = "UTC";

    private static final ImmutableBiMap<String, String> REPHRASE_MAP =
            ImmutableBiMap.of("healthConfig", "health", "metricsConfig", "metrics");

    private int eventBusTimeout = DEFAULT_EVENT_BUS_TIMEOUT;

    private Map<String, String> eventBusCodecs = Map.of();

    private String trackingDataHandlingStrategy = DEFAULT_TRACKING_DATA_HANDLING_STRATEGY;

    private List<String> platformClasses = List.of("io.vertx.*", "io.neonbee.*", "org.slf4j.*", "org.apache.olingo.*");

    private String timeZone = DEFAULT_TIME_ZONE;

    private List<MicrometerRegistryConfig> micrometerRegistries = List.of();

    private HealthConfig healthConfig = new HealthConfig();

    private MetricsConfig metricsConfig = new MetricsConfig();

    /**
     * Are the metrics enabled?
     *
     * @return true if the metrics are enabled, otherwise false.
     */
    public MetricsConfig getMetricsConfig() {
        return metricsConfig;
    }

    /**
     * Sets the value to enable, disable metrics.
     *
     * @param metricsConfig true if the metrics should be enabled, false otherwise.
     * @return the {@linkplain NeonBeeConfig} for fluent use
     */
    @Fluent
    public NeonBeeConfig setMetricsConfig(MetricsConfig metricsConfig) {
        this.metricsConfig = metricsConfig;
        return this;
    }

    /**
     * Loads the NeonBee configuration from the config directory and converts it to a {@link NeonBeeConfig}.
     *
     * This method does not require that NeonBee is started.
     *
     * @param vertx The Vert.x instance used to load the config file
     * @param path  the configuration directory path
     * @return a future to a {@link NeonBeeConfig}
     */
    public static Future<NeonBeeConfig> load(Vertx vertx, Path path) {
        return readConfig(vertx, NeonBee.class.getName(), path)
                .recover(notFound(() -> succeededFuture(new JsonObject()))).map(NeonBeeConfig::new);
    }

    /**
     * Loads the NeonBee configuration from the NeonBee config directory and converts it to a {@link NeonBeeConfig}.
     *
     * This method requires that NeonBee is started.
     *
     * @param vertx The Vert.x instance used to load the config file
     * @return a future to a {@link NeonBeeConfig}
     */
    public static Future<NeonBeeConfig> load(Vertx vertx) {
        return readConfig(vertx, NeonBee.class.getName(), new JsonObject()).map(NeonBeeConfig::new);
    }

    /**
     * Creates an initial NeonBee configuration object.
     */
    public NeonBeeConfig() {}

    /**
     * Creates a {@linkplain NeonBeeConfig} parsing a given JSON object.
     *
     * @param json the JSON object to parse
     */
    public NeonBeeConfig(JsonObject json) {
        this();

        JsonObject newJson = rephraseConfigNames(json.copy(), REPHRASE_MAP, true);
        NeonBeeConfigConverter.fromJson(newJson, this);
    }

    /**
     * Gets the health config.
     *
     * @return the {@link HealthConfig}
     */
    public HealthConfig getHealthConfig() {
        return healthConfig;
    }

    /**
     * Sets the health config.
     *
     * @param healthConfig the health config to set
     * @return the {@linkplain NeonBeeConfig} for fluent use
     */
    @Fluent
    public NeonBeeConfig setHealthConfig(HealthConfig healthConfig) {
        this.healthConfig = healthConfig;
        return this;
    }

    /**
     * Try to load all {@link MicrometerRegistryLoader}s which are configured in the {@link NeonBeeConfig}.
     *
     * @param vertx the {@link Vertx} instance
     * @return MicrometerMetricsOptions which contains the loaded registries
     */
    public Stream<Future<MeterRegistry>> createMicrometerRegistries(Vertx vertx) {
        return this.micrometerRegistries.stream()
                .filter(micrometerRegistryConfig -> micrometerRegistryConfig.getClassName() != null)
                .filter(micrometerRegistryConfig -> !micrometerRegistryConfig.getClassName().isBlank())
                .map(micrometerRegistryConfig -> instantiateLoader(micrometerRegistryConfig.getClassName()).compose(
                        micrometerRegistryLoader -> Future.<MeterRegistry>future(promise -> micrometerRegistryLoader
                                .load(vertx, micrometerRegistryConfig.getConfig(), promise))));
    }

    /**
     * Try to load all {@link MicrometerRegistryLoader}s which are configured in the {@link NeonBeeConfig}.
     *
     * @return MicrometerMetricsOptions which contains the loaded registries
     * @throws ClassNotFoundException    if the class cannot be located
     * @throws NoSuchMethodException     if a matching method is not found.
     * @throws InvocationTargetException if the underlying constructor throws an exception.
     * @throws InstantiationException    if the class that declares the underlying constructor represents an abstract
     *                                   class.
     * @throws IllegalAccessException    if this Constructor object is enforcing Java language access control and the
     *                                   underlying constructor is inaccessible.
     */
    @Deprecated(forRemoval = true)
    public Collection<MeterRegistry> createMicrometerRegistries() throws ClassNotFoundException, NoSuchMethodException,
            InvocationTargetException, InstantiationException, IllegalAccessException {

        List<MeterRegistry> registries = new ArrayList<>(this.micrometerRegistries.size());
        for (MicrometerRegistryConfig micrometerRegistryConfig : this.micrometerRegistries) {
            String className = micrometerRegistryConfig.getClassName();
            if (className == null || className.isBlank()) {
                continue;
            }
            Class<?> classObject = Class.forName(className);
            if (!MicrometerRegistryLoader.class.isAssignableFrom(classObject)) {
                throw new IllegalArgumentException(
                        classObject.getName() + " must implement " + MicrometerRegistryLoader.class.getName());
            }
            MicrometerRegistryLoader loader = (MicrometerRegistryLoader) classObject.getConstructor().newInstance();
            registries.add(loader.load(micrometerRegistryConfig.getConfig()));
        }
        return registries;
    }

    /**
     * Load and instantiate the {@link MicrometerRegistryLoader}.
     *
     * @param className the name of the class to load
     * @return future with the {@link MicrometerRegistryLoader}
     */
    private Future<MicrometerRegistryLoader> instantiateLoader(String className) {
        return future(promise -> {
            try {
                Class<?> classObject = Class.forName(className);
                if (!MicrometerRegistryLoader.class.isAssignableFrom(classObject)) {
                    promise.fail(new IllegalArgumentException(
                            classObject.getName() + " must implement " + MicrometerRegistryLoader.class.getName()));
                }
                promise.complete((MicrometerRegistryLoader) classObject.getConstructor().newInstance());
            } catch (ClassNotFoundException | InstantiationException | IllegalAccessException
                    | InvocationTargetException | NoSuchMethodException e) {
                promise.fail(e);
            }
        });
    }

    /**
     * Transforms this configuration object into JSON.
     *
     * @return a JSON representation of this configuration
     */
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        NeonBeeConfigConverter.toJson(this, json);
        rephraseConfigNames(json, REPHRASE_MAP, false);
        return json;
    }

    /**
     * Gets the event bus timeout in seconds.
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
     * Gets a list of default codecs to register on the event bus.
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
     * Sets the class to load for the tracking data handling.
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
     * Platform classes are classes to be considered "provided" by the system class loader. NeonBee modules will attempt
     * to find platform classes in the system class loader first, before loading them (self-first) from their own (so
     * called) module class-loader. This way, you can prevent incompatibility issues across modules and also have
     * modules with a much smaller runtime footprint.
     *
     * Example values: io.vertx.core.json.JsonObject, io.neonbee.data.*, com.foo.bar.*
     *
     * @return a list of strings that either contains a full qualified class names, or class names with * als a wildcard
     *         character sequence. By default all NeonBee and Vert.x classes are considered platform classes.
     */
    public List<String> getPlatformClasses() {
        return platformClasses;
    }

    /**
     * Sets classes available by the platform.
     *
     * @see #getPlatformClasses()
     * @param platformClasses a list of class names
     * @return the {@linkplain NeonBeeConfig} for fluent use
     */
    @Fluent
    public NeonBeeConfig setPlatformClasses(List<String> platformClasses) {
        this.platformClasses = platformClasses;
        return this;
    }

    /**
     * Gets the timezone to be used as default in NeonBee. Either an abbreviation such as "PST", a full name such as
     * "America/Los_Angeles".
     *
     * @return A string with the timezone
     */
    public String getTimeZone() {
        return timeZone;
    }

    /**
     * Sets the timezone used in NeonBee, e.g. when date / timestamps are returned via the web interface.
     *
     * @param timeZone The time zone to set
     * @return the {@linkplain NeonBeeConfig} for fluent use
     */
    @Fluent
    public NeonBeeConfig setTimeZone(String timeZone) {
        this.timeZone = timeZone;
        return this;
    }

    /**
     * Adds the passed list of {@link String}s containing the full qualified name of classes which implement the
     * {@link MicrometerRegistryLoader}.
     *
     * @param micrometerRegistries A list of Strings containing classes which implement the
     *                             {@link MicrometerRegistryLoader}
     * @return a reference to this, so the API can be used fluently
     */
    @Fluent
    public NeonBeeConfig setMicrometerRegistries(List<MicrometerRegistryConfig> micrometerRegistries) {
        this.micrometerRegistries = micrometerRegistries;
        return this;
    }

    /**
     * Get the {@link MetricsOptions}.
     *
     * @return the {@link MetricsOptions}
     */
    public List<MicrometerRegistryConfig> getMicrometerRegistries() {
        return this.micrometerRegistries;
    }
}
