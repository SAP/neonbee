package io.neonbee.internal;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import io.neonbee.NeonBee;
import io.neonbee.logging.LoggingFacade;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.core.shareddata.SharedData;

/**
 * A registry to manage values in the {@link SharedDataAccessor} shared map.
 * <p>
 * The values under the key are stored in a JsonArray.
 *
 * @param <T> the type of data this registry stores
 */
public class WriteSafeRegistry<T> implements Registry<T> {

    private final LoggingFacade logger = LoggingFacade.create();

    private final SharedData sharedData;

    private final Registry<T> sharedRegistry;

    private final String registryName;

    // Per-key write queue
    private final Map<String, Future<Void>> queues = new ConcurrentHashMap<>();

    /**
     * Create a new {@link WriteSafeRegistry} with a default {@link SharedDataAccessor}.
     *
     * @param vertx        the Vert.x instance used to access shared data
     * @param registryName the name of the map registry
     */
    public WriteSafeRegistry(Vertx vertx, String registryName) {
        this(registryName, new SharedDataAccessor(vertx, WriteSafeRegistry.class));
    }

    /**
     * Create a new {@link WriteSafeRegistry}.
     *
     * @param registryName the name of the map registry
     * @param sharedData   the shared data
     */
    public WriteSafeRegistry(String registryName, SharedData sharedData) {
        this(registryName, sharedData, new SharedRegistry<>(registryName, sharedData));
    }

    /**
     * Create a new {@link WriteSafeRegistry}.
     *
     * @param registryName the name of the map registry
     * @param sharedData   the shared data
     * @param registry     the shared registry
     */
    WriteSafeRegistry(String registryName, SharedData sharedData, Registry<T> registry) {
        this.registryName = registryName;
        this.sharedData = sharedData;
        this.sharedRegistry = registry;
    }

    /**
     * Register a value in the async shared map of {@link NeonBee} by key.
     *
     * @param sharedMapKey the shared map key
     * @param value        the value to register
     * @return the future
     */
    @Override
    public Future<Void> register(String sharedMapKey, T value) {
        logger.info("register value: \"{}\" in shared map: \"{}\"", sharedMapKey, value);
        return lock(sharedMapKey, () -> sharedRegistry.register(sharedMapKey, value));
    }

    @Override
    public Future<Void> unregister(String sharedMapKey, T value) {
        logger.debug("unregister value: \"{}\" from shared map: \"{}\"", sharedMapKey, value);
        return lock(sharedMapKey, () -> sharedRegistry.unregister(sharedMapKey, value));
    }

    @Override
    public Future<JsonArray> get(String sharedMapKey) {
        return sharedRegistry.get(sharedMapKey);
    }

    /**
     * Serializes write operations per key to prevent concurrent map updates.
     *
     * @param key       the key identifying the per-key operation queue and lock
     * @param operation the write operation to run while holding the shared-data lock
     * @return a future that completes when the operation has finished
     */
    protected Future<Void> lock(String key, Supplier<Future<Void>> operation) {

        return queues.compute(key, (k, tail) -> {

            Future<Void> start = tail == null
                    ? Future.succeededFuture()
                    : tail.recover(err -> {
                        logger.warn("Previous operation failed for {}, continuing", key, err);
                        return Future.succeededFuture();
                    });

            Future<Void> next = start
                    .compose(v -> {
                        logger.debug("Acquiring lock for {}", key);
                        return sharedData.getLock(key);
                    })
                    .compose(lock -> operation.get()
                            .onComplete(ar -> {
                                logger.debug("Releasing lock for {}", key);
                                lock.release();
                            }));

            next.onComplete(ar -> queues.computeIfPresent(key, (kk, current) -> current == next ? null : current));

            return next;
        });
    }

    /**
     * Shared map that is used as registry.
     * <p>
     * It is not safe to write to the shared map directly. Use the {@link WriteSafeRegistry#register(String, Object)}
     * and {@link WriteSafeRegistry#unregister(String, Object)} methods.
     *
     * @return Future to the shared map
     */
    public Future<AsyncMap<String, Object>> getSharedMap() {
        return sharedData.getAsyncMap(registryName);
    }
}
