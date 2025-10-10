package io.neonbee.cluster;

import static io.vertx.core.Future.succeededFuture;

import java.util.List;
import java.util.Map;
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
        ClusterCleanupCoordinator.class
    );

    private static final String MAP_NAME = "cluster:pending-node-cleanups";

    private static final String LOCK_NAME = "cluster:cleanup-lock";

    private static final long DEFAULT_CLEANUP_INTERVAL_MS = 5000;

    private static final long DEFAULT_LOCK_TIMEOUT_MS = 2000;

    private final ClusterManager clusterManager;

    private final Vertx vertx;

    private AsyncMap<String, Boolean> pendingRemovals;

    private Long periodicTimerId;

    private final long cleanupIntervalMs;

    private final long lockTimeoutMs;

    /**
     * Creates a new ClusterCleanupCoordinator with default configuration.
     *
     * @param vertx          the Vert.x instance
     * @param clusterManager the cluster manager for distributed operations
     */
    public ClusterCleanupCoordinator(
        Vertx vertx,
        ClusterManager clusterManager
    ) {
        this(
            vertx,
            clusterManager,
            DEFAULT_CLEANUP_INTERVAL_MS,
            DEFAULT_LOCK_TIMEOUT_MS
        );
    }

    /**
     * Creates a new ClusterCleanupCoordinator with custom configuration.
     *
     * @param vertx             the Vert.x instance
     * @param clusterManager    the cluster manager for distributed operations
     * @param cleanupIntervalMs the interval between cleanup attempts in milliseconds
     * @param lockTimeoutMs     the timeout for acquiring the cleanup lock in milliseconds
     */
    public ClusterCleanupCoordinator(
        Vertx vertx,
        ClusterManager clusterManager,
        long cleanupIntervalMs,
        long lockTimeoutMs
    ) {
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

                // Start periodic cleanup loop
                this.periodicTimerId =
                    vertx.setPeriodic(
                        cleanupIntervalMs,
                        id -> tryAcquireLockAndCleanup()
                    );

                return succeededFuture(map); // success
            })
            .onFailure(err -> {
                LOGGER.error("Failed to initialize pendingRemovals", err);
            });
    }

    /**
     * Stops the cleanup coordinator and releases all resources.
     *
     * @return Future that completes when the coordinator is stopped
     */
    public Future<Void> stop() {
        if (periodicTimerId != null) {
            vertx.cancelTimer(periodicTimerId);
            periodicTimerId = null;
        }
        pendingRemovals = null;
        return succeededFuture();
    }

    /**
     * Called when a node leaves the cluster to schedule cleanup.
     *
     * @param nodeId the ID of the node that left the cluster
     */
    public void addNodeLeft(String nodeId) {
        if (nodeId == null || nodeId.isBlank()) {
            LOGGER.warn("Invalid nodeId provided to onNodeLeft: {}", nodeId);
            return;
        }

        if (pendingRemovals != null) {
            pendingRemovals
                .putIfAbsent(nodeId, true)
                .onSuccess(v ->
                    LOGGER.debug("Scheduled cleanup for node: {}", nodeId)
                )
                .onFailure(err ->
                    LOGGER.error(
                        "Failed to schedule cleanup for node: {}",
                        nodeId,
                        err
                    )
                );
        } else {
            LOGGER.warn(
                "Pending removals map not initialized, cannot schedule cleanup for node: {}",
                nodeId
            );
        }
    }

    private void tryAcquireLockAndCleanup() {
        Promise<Lock> acquireLock = Promise.promise();
        clusterManager.getLockWithTimeout(
            LOCK_NAME,
            lockTimeoutMs,
            acquireLock
        );

        acquireLock
            .future()
            .onComplete(lockRes -> {
                if (lockRes.succeeded()) {
                    Lock lock = lockRes.result();
                    cleanupPending()
                        .onComplete(done -> {
                            lock.release();
                            if (done.failed() && LOGGER.isErrorEnabled()) {
                                LOGGER.error(
                                    "Cleanup operation failed",
                                    done.cause()
                                );
                            }
                        });
                } else if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug(
                        "Failed to acquire cleanup lock, another node may be processing cleanups",
                        lockRes.cause()
                    );
                }
            });
    }

    private Future<Void> cleanupPending() {
        Promise<Void> promise = Promise.promise();

        pendingRemovals
            .entries()
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
            .onSuccess(v -> {
                pendingRemovals
                    .remove(nodeId)
                    .onSuccess(removed ->
                        LOGGER.debug(
                            "Successfully cleaned up and removed node: {}",
                            nodeId
                        )
                    )
                    .onFailure(err ->
                        LOGGER.error(
                            "Failed to remove node {} from pending removals",
                            nodeId,
                            err
                        )
                    );
            })
            .onFailure(err -> {
                LOGGER.error("Failed to cleanup node: {}", nodeId, err);
                // Don't remove from pending removals if cleanup failed - it will be retried
            });
    }

    private Future<Void> doActualCleanup(String nodeId) {
        // custom cleanup per node
        LOGGER.info("Cleaning up resources for node: {}", nodeId);
        Registry<String> registry = NeonBee.get(vertx).getEntityRegistry();

        if (
            !(registry instanceof ClusterEntityRegistry clusterEntityRegistry)
        ) {
            LOGGER.warn(
                "Running in clustered mode but not using the ClusterEntityRegistry."
            );
            return succeededFuture();
        }
        return clusterEntityRegistry.unregisterNode(nodeId);
    }
}
