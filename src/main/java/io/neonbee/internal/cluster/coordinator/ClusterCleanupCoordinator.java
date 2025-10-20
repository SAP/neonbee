package io.neonbee.internal.cluster.coordinator;

import static io.vertx.core.Future.succeededFuture;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.neonbee.NeonBee;
import io.neonbee.internal.Registry;
import io.neonbee.internal.cluster.entity.ClusterEntityRegistry;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.core.shareddata.Lock;
import io.vertx.core.spi.cluster.ClusterManager;

/**
 * Coordinates cleanup operations for nodes that have left the cluster. This class manages a distributed cleanup process
 * using cluster-wide locks to ensure only one node performs cleanup operations at a time.
 */
public class ClusterCleanupCoordinator {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            ClusterCleanupCoordinator.class);

    private static final String MAP_NAME = "cluster:pending-node-cleanups";

    private static final String LOCK_NAME = "cluster:cleanup-lock";

    private static final long DEFAULT_CLEANUP_INTERVAL_MS = 5000;

    private static final long DEFAULT_LOCK_TIMEOUT_MS = 2000;

    private final ClusterManager clusterManager;

    private final Vertx vertx;

    private AsyncMap<String, Boolean> pendingRemovals;

    private final List<String> preInitBuffer = new CopyOnWriteArrayList<>();

    private Long periodicTimerId;

    private final long cleanupIntervalMs;

    private final long lockTimeoutMs;

    /**
     * Constructs a ClusterCleanupCoordinator with default cleanup and lock timeout.
     *
     * @param vertx          the Vert.x instance
     * @param clusterManager the cluster manager
     */
    public ClusterCleanupCoordinator(
            Vertx vertx,
            ClusterManager clusterManager) {
        this(
                vertx,
                clusterManager,
                DEFAULT_CLEANUP_INTERVAL_MS,
                DEFAULT_LOCK_TIMEOUT_MS);
    }

    /**
     * Constructs a ClusterCleanupCoordinator with specified cleanup interval and lock timeout.
     *
     * @param vertx             the Vert.x instance
     * @param clusterManager    the cluster manager
     * @param cleanupIntervalMs the interval at which cleanup operations are performed, in milliseconds
     * @param lockTimeoutMs     the timeout for acquiring a lock, in milliseconds
     */
    public ClusterCleanupCoordinator(
            Vertx vertx,
            ClusterManager clusterManager,
            long cleanupIntervalMs,
            long lockTimeoutMs) {
        this.vertx = vertx;
        this.clusterManager = clusterManager;
        this.cleanupIntervalMs = cleanupIntervalMs;
        this.lockTimeoutMs = lockTimeoutMs;
    }

    /**
     * Starts the cleanup coordinator by initializing the shared map and starting the periodic cleanup process.
     *
     * @return Future that completes with the initialized AsyncMap when started successfully
     */
    public Future<AsyncMap<String, Boolean>> start() {
        Promise<AsyncMap<String, Boolean>> mapPromise = Promise.promise();
        clusterManager.getAsyncMap(MAP_NAME, mapPromise);

        return mapPromise
                .future()
                .compose(map -> {
                    this.pendingRemovals = map;
                    LOGGER.debug("Successfully initialized pendingRemovals map");

                    // Flush any buffered node-left events that arrived before map was ready
                    flushPreInitBuffer();

                    // Start periodic cleanup loop
                    this.periodicTimerId =
                            vertx.setPeriodic(
                                    cleanupIntervalMs,
                                    id -> tryAcquireLockAndCleanup());

                    return succeededFuture(map);
                })
                .onFailure(err -> LOGGER.error("Failed to initialize pendingRemovals", err));
    }

    private void flushPreInitBuffer() {
        if (pendingRemovals != null && !preInitBuffer.isEmpty()) {
            for (String nodeId : preInitBuffer) {
                pendingRemovals
                        .putIfAbsent(nodeId, true)
                        .onSuccess(v -> LOGGER.debug("Flushed buffered node: {}", nodeId))
                        .onFailure(err -> LOGGER.error(
                                "Failed to flush buffered node: {}",
                                nodeId,
                                err));
            }
            preInitBuffer.clear();
        }
    }

    /**
     * Stops the periodic cleanup process.
     *
     * Cancels the periodic timer if it is active and returns a completed future indicating the stop operation has
     * succeeded.
     *
     * @return a future that completes when the stop operation is finished
     */
    public Future<Void> stop() {
        if (periodicTimerId != null) {
            vertx.cancelTimer(periodicTimerId);
        }
        return succeededFuture();
    }

    /**
     * Called when a node leaves the cluster to schedule cleanup.
     *
     * @param nodeId the ID of the node that left the cluster
     */
    public void addNodeLeft(String nodeId) {
        if (nodeId == null || nodeId.trim().isBlank()) {
            LOGGER.warn("Invalid nodeId provided to onNodeLeft: {}", nodeId);
            return;
        }

        if (pendingRemovals == null) {
            // Buffer node-left events until async map is ready
            preInitBuffer.add(nodeId);
            LOGGER.debug("Buffered node left event for node: {}", nodeId);
            return;
        }

        pendingRemovals
                .putIfAbsent(nodeId, true)
                .onSuccess(v -> LOGGER.debug("Scheduled cleanup for node: {}", nodeId))
                .onFailure(err -> LOGGER.error(
                        "Failed to schedule cleanup for node: {}",
                        nodeId,
                        err));
    }

    private void tryAcquireLockAndCleanup() {
        Promise<Lock> acquireLock = Promise.promise();
        clusterManager.getLockWithTimeout(
                LOCK_NAME,
                lockTimeoutMs,
                acquireLock);

        acquireLock
                .future()
                .onComplete(lockRes -> {
                    if (lockRes.succeeded()) {
                        Lock lock = lockRes.result();
                        cleanupPending().onComplete(done -> lock.release());
                    } else if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug(
                                "Failed to acquire cleanup lock, another node may be processing cleanups: {}",
                                lockRes.cause());
                    }
                });
    }

    private Future<Void> cleanupPending() {
        Promise<Void> promise = Promise.promise();
        reconcileRegistryWithCluster()
                .compose(v -> pendingRemovals.entries())
                .onComplete(ar -> {
                    if (ar.succeeded()) {
                        Map<String, Boolean> entries = ar.result();
                        List<Future<Void>> cleanups = entries
                                .keySet()
                                .stream()
                                .map(this::processNodeCleanup)
                                .collect(Collectors.toList());

                        Future
                                .all(cleanups)
                                .onSuccess(v -> promise.complete())
                                .onFailure(promise::fail);
                    } else {
                        promise.fail(ar.cause());
                    }
                });
        return promise.future();
    }

    private Future<Void> processNodeCleanup(String nodeId) {
        return doActualCleanup(nodeId)
                .onSuccess(v -> pendingRemovals
                        .remove(nodeId)
                        .onSuccess(removed -> LOGGER.debug(
                                "Successfully cleaned up and removed node: {}",
                                nodeId))
                        .onFailure(err -> LOGGER.error(
                                "Failed to remove node {} from pending removals",
                                nodeId,
                                err)))
                .onFailure(err -> LOGGER.error("Failed to cleanup node: {}", nodeId, err));
    }

    private Future<Void> doActualCleanup(String nodeId) {
        LOGGER.info("Cleaning up resources for node: {}", nodeId);
        Registry<String> registry = NeonBee.get(vertx).getEntityRegistry();

        if (!(registry instanceof ClusterEntityRegistry clusterEntityRegistry)) {
            LOGGER.warn(
                    "Running in clustered mode but not using the ClusterEntityRegistry.");
            return succeededFuture();
        }
        return clusterEntityRegistry.unregisterNode(nodeId);
    }

    /** Package-private getter for testing purposes. */
    AsyncMap<String, Boolean> getPendingRemovals() {
        return pendingRemovals;
    }

    /**
     * Reconciles the {@link ClusterEntityRegistry} with the current state of the cluster.
     * <p>
     * This method ensures consistency between the registry and the active cluster members. It retrieves all node IDs
     * currently recorded in the registry and compares them against the active node list provided by the cluster
     * manager. Any node IDs that remain in the registry but are no longer part of the active cluster are considered
     * stale and are scheduled for cleanup by adding them to {@code pendingRemovals}.
     * <p>
     * If the coordinator is not yet initialized or the registry is not a {@link ClusterEntityRegistry}, the
     * reconciliation is skipped safely.
     *
     * @return a {@link Future} that completes when the reconciliation process finishes (immediately successful if no
     *         stale nodes are found or reconciliation is skipped)
     */
    private Future<Void> reconcileRegistryWithCluster() {
        Registry<String> registry = NeonBee.get(vertx).getEntityRegistry();

        if (!(registry instanceof ClusterEntityRegistry clusterEntityRegistry)) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Skipping reconciliation: not a ClusterEntityRegistry");
            }
            return succeededFuture();
        }

        if (pendingRemovals == null) {
            // Not initialized yet
            return succeededFuture();
        }

        // Active nodes according to the cluster manager
        Set<String> activeNodes = new HashSet<>(clusterManager.getNodes());

        // Node IDs currently present in the registry
        return clusterEntityRegistry.getAllNodeIds()
                .compose(registryNodeIds -> {
                    // Determine node IDs present in registry but not in activeNodes
                    registryNodeIds.removeAll(activeNodes);
                    if (registryNodeIds.isEmpty()) {
                        return succeededFuture();
                    }

                    List<Future<Object>> schedule = registryNodeIds.stream()
                            .map(nodeId -> pendingRemovals.putIfAbsent(nodeId, true).mapEmpty()).toList();

                    return Future.all(schedule).mapEmpty();
                });
    }
}
