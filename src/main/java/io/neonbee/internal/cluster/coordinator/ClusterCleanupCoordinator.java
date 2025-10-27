package io.neonbee.internal.cluster.coordinator;

import static io.vertx.core.Future.succeededFuture;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.neonbee.NeonBee;
import io.neonbee.internal.Registry;
import io.neonbee.internal.cluster.entity.ClusterEntityRegistry;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.shareddata.Lock;
import io.vertx.core.spi.cluster.ClusterManager;

/**
 * Coordinates the cleanup of stale cluster nodes in a distributed environment.
 * <p>
 * This coordinator uses a reconciliation-only approach:
 * <ul>
 * <li>No persistent "pending removals" map is maintained</li>
 * <li>Each cleanup cycle: acquires a distributed lock, computes stale nodes, and cleans them up in batches</li>
 * <li>Uses an adaptive scheduling mechanism that backs off on errors and speeds up on success</li>
 * <li>Applies jitter to avoid synchronization between multiple coordinators</li>
 * </ul>
 */
public class ClusterCleanupCoordinator {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            ClusterCleanupCoordinator.class);

    /** The name of the distributed lock used for coordinating cleanup operations. */
    private static final String LOCK_NAME = "cluster:cleanup-lock";

    /** Minimum interval between cleanup cycles in milliseconds. */
    private static final long MIN_INTERVAL = 5_000;

    /** Maximum interval between cleanup cycles in milliseconds. */
    private static final long MAX_INTERVAL = 60_000;

    /** Default interval between cleanup cycles in milliseconds. */
    private static final long DEFAULT_INTERVAL = 10_000;

    /** Timeout for acquiring the cleanup lock in milliseconds. */
    private static final long LOCK_TIMEOUT = 2_000;

    /** Maximum number of nodes to clean up in a single batch. */
    private static final int MAX_BATCH = 100;

    /** Minimum jitter value in milliseconds to add to the cleanup interval. */
    private static final int JITTER_MIN = 1_000;

    /** Maximum jitter value in milliseconds to add to the cleanup interval. */
    private static final int JITTER_MAX = 3_000;

    /** The Vert.x instance used for scheduling cleanup cycles. */
    private final Vertx vertx;

    /** The cluster manager used to get active cluster nodes. */
    private final ClusterManager clusterManager;

    /** Supplier for obtaining the NeonBee instance. */
    private final Supplier<NeonBee> neonBeeSupplier;

    /** Timeout for acquiring the cleanup lock in milliseconds. */
    private final long lockTimeoutMs;

    /** The ID of the currently scheduled cleanup timer. */
    private Long timerId;

    /** Current interval between cleanup cycles in milliseconds. */
    private long currentIntervalMs;

    /**
     * Creates a new ClusterCleanupCoordinator with default configuration.
     *
     * @param vertx          the Vert.x instance
     * @param clusterManager the cluster manager to use for node detection
     */
    public ClusterCleanupCoordinator(
            Vertx vertx,
            ClusterManager clusterManager) {
        this(
                vertx,
                clusterManager,
                DEFAULT_INTERVAL,
                LOCK_TIMEOUT,
                () -> NeonBee.get(vertx));
    }

    /**
     * Creates a new ClusterCleanupCoordinator with custom configuration.
     *
     * @param vertx           the Vert.x instance
     * @param clusterManager  the cluster manager to use for node detection
     * @param initialInterval the initial interval between cleanup cycles in milliseconds
     * @param lockTimeoutMs   the timeout for acquiring the cleanup lock in milliseconds
     * @param neonBeeSupplier supplier for obtaining the NeonBee instance
     */
    public ClusterCleanupCoordinator(
            Vertx vertx,
            ClusterManager clusterManager,
            long initialInterval,
            long lockTimeoutMs,
            Supplier<NeonBee> neonBeeSupplier) {
        this.vertx = vertx;
        this.clusterManager = clusterManager;
        this.lockTimeoutMs = lockTimeoutMs;
        this.neonBeeSupplier =
                neonBeeSupplier != null
                        ? neonBeeSupplier
                        : () -> NeonBee.get(vertx);
        this.currentIntervalMs = Math.max(MIN_INTERVAL, initialInterval);
    }

    /**
     * Starts the cluster cleanup coordinator.
     * <p>
     * This begins the periodic cleanup cycles with adaptive scheduling.
     * </p>
     *
     * @return a Future that completes when the coordinator has started
     */
    public Future<Void> start() {
        scheduleNext(true);
        LOGGER.info("ClusterCleanupCoordinator started (reconciliation-only).");
        return succeededFuture();
    }

    /**
     * Stops the cluster cleanup coordinator.
     * <p>
     * Cancels any scheduled cleanup cycles and stops the coordinator.
     * </p>
     *
     * @return a Future that completes when the coordinator has stopped
     */
    public Future<Void> stop() {
        if (timerId != null) {
            vertx.cancelTimer(timerId);
        }
        LOGGER.info("ClusterCleanupCoordinator stopped.");
        return succeededFuture();
    }

    /**
     * Main cleanup routine.
     * <p>
     * Acquires a distributed lock, performs reconciliation and cleanup, then reschedules the next cycle. If the lock
     * cannot be acquired, the operation fails gracefully and reschedules.
     * </p>
     *
     * @return a Future that completes when the cleanup cycle is finished
     */
    private Future<Void> runCleanupCycle() {
        Promise<Lock> lockPromise = Promise.promise();
        clusterManager.getLockWithTimeout(
                LOCK_NAME,
                lockTimeoutMs,
                lockPromise);

        return lockPromise
                .future()
                .compose(lock -> performCleanup()
                        // release lock regardless of cleanup result
                        .eventually(() -> {
                            try {
                                lock.release();
                            } catch (Exception e) {
                                LOGGER.warn("Failed to release cleanup lock", e);
                            }
                            return succeededFuture();
                        }))
                .onComplete(ar -> scheduleNext(ar.succeeded()))
                .recover(err -> {
                    // failed to get lock or other non-fatal error
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug(
                                "Cleanup lock not acquired or failed: {}",
                                err.getMessage());
                    }
                    scheduleNext(false);
                    return succeededFuture();
                });
    }

    /**
     * Performs one cleanup cycle.
     * <p>
     * Reconciles the registry with the current cluster state and removes stale nodes in batches. Returns immediately if
     * no stale nodes are found.
     * </p>
     *
     * @return a Future that completes when the cleanup operation is finished
     */
    private Future<Void> performCleanup() {
        Promise<Void> promise = Promise.promise();
        reconcileRegistryWithCluster()
                .compose(staleNodes -> {
                    if (staleNodes.isEmpty()) {
                        LOGGER.debug("No stale nodes found during reconciliation.");
                        return succeededFuture();
                    }

                    List<String> batch = staleNodes
                            .stream()
                            .limit(MAX_BATCH)
                            .toList();
                    if (LOGGER.isInfoEnabled()) {
                        LOGGER.info(
                                "Found {} stale nodes, cleaning up {} in this batch.",
                                staleNodes.size(),
                                batch.size());
                    }

                    return Future
                            .all(batch.stream().map(this::cleanupNode).toList())
                            .mapEmpty();
                })
                .onSuccess(v -> promise.complete())
                .onFailure(err -> {
                    LOGGER.error("Cleanup cycle failed", err);
                    promise.fail(err);
                });
        return promise.future();
    }

    /**
     * Cleans up resources for a single stale node.
     * <p>
     * Unregisters the node from the cluster entity registry. If the registry is not a ClusterEntityRegistry, the
     * operation is skipped.
     * </p>
     *
     * @param nodeId the ID of the node to clean up
     * @return a Future that completes when the node cleanup is finished
     */
    private Future<Void> cleanupNode(String nodeId) {
        Registry<String> registry = neonBeeSupplier.get().getEntityRegistry();

        if (!(registry instanceof ClusterEntityRegistry clusterRegistry)) {
            LOGGER.warn(
                    "No ClusterEntityRegistry found, skipping cleanup for {}",
                    nodeId);
            return succeededFuture();
        }

        LOGGER.info("Cleaning up resources for stale node: {}", nodeId);
        return clusterRegistry
                .unregisterNode(nodeId)
                .onSuccess(v -> LOGGER.debug("Successfully cleaned up node {}", nodeId))
                .onFailure(err -> LOGGER.error("Failed to clean up node {}", nodeId, err));
    }

    /**
     * Computes the list of stale node IDs.
     * <p>
     * A node is considered stale if it exists in the registry but is not present in the active cluster. Formula: stale
     * nodes = registryNodes - activeClusterNodes
     * </p>
     *
     * @return a Future containing the set of stale node IDs
     */
    Future<Set<String>> reconcileRegistryWithCluster() {
        Registry<String> registry = neonBeeSupplier.get().getEntityRegistry();

        if (!(registry instanceof ClusterEntityRegistry clusterRegistry)) {
            LOGGER.debug(
                    "Entity registry is not ClusterEntityRegistry, skipping reconciliation.");
            return succeededFuture(Set.of());
        }

        Set<String> activeNodes = new HashSet<>(clusterManager.getNodes());
        return clusterRegistry
                .getAllNodeIds()
                .map(registryNodeIds -> {
                    registryNodeIds.removeAll(activeNodes);
                    return registryNodeIds;
                });
    }

    /**
     * Schedules the next cleanup cycle with adaptive interval adjustment.
     * <p>
     * The interval is adjusted based on the success or failure of the previous cycle:
     * <ul>
     * <li>On success: interval is halved (with a minimum bound)</li>
     * <li>On failure: interval is doubled (with a maximum bound)</li>
     * </ul>
     * A random jitter is added to prevent synchronization between multiple coordinators.
     * </p>
     *
     * @param success indicates whether the previous cleanup cycle was successful
     */
    private void scheduleNext(boolean success) {
        currentIntervalMs =
                success
                        ? Math.max(currentIntervalMs / 2, MIN_INTERVAL)
                        : Math.min(currentIntervalMs * 2, MAX_INTERVAL);

        if (timerId != null) {
            vertx.cancelTimer(timerId);
        }

        long jitter = ThreadLocalRandom
                .current()
                .nextLong(JITTER_MIN, JITTER_MAX);
        long delay = currentIntervalMs + jitter;

        timerId = vertx.setTimer(delay, id -> runCleanupCycle());
        LOGGER.debug(
                "Next cleanup scheduled in {} ms (success={})",
                delay,
                success);
    }
}
