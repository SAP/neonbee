package io.neonbee.internal.cluster;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.infinispan.manager.EmbeddedCacheManager;

import com.hazelcast.cluster.Member;

import io.neonbee.internal.cluster.coordinator.ClusterCleanupCoordinator;
import io.neonbee.internal.helper.ConfigHelper;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.ext.cluster.infinispan.InfinispanClusterManager;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;

public final class ClusterHelper {

    private static final Map<Vertx, ClusterCleanupCoordinator> COORDINATORS = new ConcurrentHashMap<>();

    private static final AtomicReference<Boolean> LEADER_CACHE = new AtomicReference<>();

    // Default configuration values for ClusterCleanupCoordinator
    private static final long DEFAULT_LOCK_TIMEOUT_MS = 2_000L;

    private static final int DEFAULT_MAX_NODES_PER_BATCH = 10;

    private static final long DEFAULT_INTERVAL_MS = 10_000L;

    /** System property name for enabling/disabling the ClusterCleanupCoordinator. */
    private static final String ENABLED_PROPERTY =
            "NEONBEE_CLUSTER_CLEANUP_ENABLED";

    /**
     * Helper class does not require instantiation.
     */
    private ClusterHelper() {}

    /**
     * Returns the {@link ClusterManager} if NeonBee is started in clustering mode, otherwise it returns null.
     *
     * @param vertx {@link Vertx} instance
     * @return null or the {@link HazelcastClusterManager} instance
     */
    public static Optional<ClusterManager> getClusterManager(Vertx vertx) {
        if (vertx instanceof VertxInternal) {
            VertxInternal vertxInternal = (VertxInternal) vertx;
            return Optional.ofNullable(vertxInternal.getClusterManager());
        }
        return Optional.empty();
    }

    /**
     * Returns an optinal with {@link HazelcastClusterManager} if NeonBee was started in cluster mode and
     * {@link HazelcastClusterManager} is used as {@link ClusterManager}, otherwise it returns an empty Optional.
     *
     * @param vertx {@link Vertx} instance
     * @return null or the {@link HazelcastClusterManager} instance
     */
    public static Optional<HazelcastClusterManager> getHazelcastClusterManager(
            Vertx vertx) {
        return getSpecificClusterManager(vertx, HazelcastClusterManager.class);
    }

    /**
     * Returns an optinal with {@link InfinispanClusterManager} if NeonBee was started in cluster mode and
     * {@link InfinispanClusterManager} is used as {@link ClusterManager}, otherwise it returns an empty Optional.
     *
     * @param vertx {@link Vertx} instance
     * @return null or the {@link InfinispanClusterManager} instance
     */
    public static Optional<InfinispanClusterManager> getInfinispanClusterManager(
            Vertx vertx) {
        return getSpecificClusterManager(vertx, InfinispanClusterManager.class);
    }

    /**
     * Returns an optinal with the provided {@link ClusterManager} class if NeonBee was started in cluster mode and the
     * {@link ClusterManager} is used as {@link ClusterManager}, otherwise it returns an empty Optional.
     *
     * @param vertx {@link Vertx} instance
     * @return null or the {@link InfinispanClusterManager} instance
     */
    private static <T extends ClusterManager> Optional<T> getSpecificClusterManager(Vertx vertx, Class<T> cmClass) {
        return getClusterManager(vertx)
                .map(cm -> cmClass.isInstance(cm) ? cmClass.cast(cm) : null);
    }

    /**
     * Get the cluster node ID.
     *
     * @param vertx {@link Vertx} instance
     * @return the cluster node ID
     */
    public static String getClusterNodeId(Vertx vertx) {
        return getClusterManager(vertx)
                .map(ClusterManager::getNodeId)
                .orElseThrow(() -> new IllegalStateException(
                        "Can not retrieve the ClusterManager. Is vert.x running in a cluster?"));
    }

    /**
     * Is this cluster node the leader?
     *
     * @param vertx {@link Vertx} instance
     * @return true if this node is the leader, otherwise false
     */
    public static boolean isLeader(Vertx vertx) {
        Boolean cached = LEADER_CACHE.get();
        if (cached != null) {
            return cached;
        }
        boolean leader = determineLeader(vertx);
        LEADER_CACHE.set(leader);
        return leader;
    }

    private static boolean determineLeader(Vertx vertx) {
        if (!vertx.isClustered()) {
            return true;
        }

        return getHazelcastClusterManager(vertx)
                .map(ClusterHelper::isLeaderFromHazelcast)
                .or(() -> getInfinispanClusterManager(vertx)
                        .map(ClusterHelper::isLeaderFromInfinispan))
                .or(() -> getClusterManager(vertx).map(ClusterHelper::isLeaderFromConfig))
                .orElse(false);
    }

    private static Boolean isLeaderFromConfig(
            @SuppressWarnings("unused") ClusterManager clm) {
        return Boolean.parseBoolean(
                System.getProperty(
                        "NEONBEE_CLUSTER_LEADER",
                        System.getenv("NEONBEE_CLUSTER_LEADER")));
    }

    private static Boolean isLeaderFromInfinispan(
            InfinispanClusterManager icm) {
        EmbeddedCacheManager cacheContainer = (EmbeddedCacheManager) icm.getCacheContainer();
        return cacheContainer.isCoordinator();
    }

    private static boolean isLeaderFromHazelcast(HazelcastClusterManager hcm) {
        // getMembers() returns a set. The first element is the leader.
        // The underlying set is a kind of linked hashset and In this case the order is kept.
        // see https://github.com/hazelcast/hazelcast/issues/3760
        Set<Member> members = hcm
                .getHazelcastInstance()
                .getCluster()
                .getMembers();
        Member oldestMember = members.iterator().next();
        return oldestMember.localMember();
    }

    /**
     * Get or create and start a ClusterCleanupCoordinator for the given Vert.x instance. This method reads the
     * configuration asynchronously and handles creation, startup, and caching. The coordinator is created even if the
     * call returns null (it will be created asynchronously).
     *
     * @param vertx the Vert.x instance
     * @return the cached ClusterCleanupCoordinator or null if not yet created or not running in clustered mode
     */
    public static Future<ClusterCleanupCoordinator> getOrCreateClusterCleanupCoordinatorImmediate(
            Vertx vertx) {
        if (!vertx.isClustered()) {
            return null;
        }

        // Start async creation
        return getOrCreateClusterCleanupCoordinator(vertx);
    }

    /**
     * Checks if the ClusterCleanupCoordinator is enabled via system property or environment variable. Checks system
     * property first, then environment variable. Defaults to true if not set.
     *
     * @return true if enabled, false otherwise
     */
    private static boolean isCoordinatorEnabled() {
        String enabledStr = System.getProperty(
                ENABLED_PROPERTY,
                System.getenv(ENABLED_PROPERTY));
        if (enabledStr == null) {
            return false; // Default: disabled
        }
        return Boolean.parseBoolean(enabledStr);
    }

    /**
     * Get or create a ClusterCleanupCoordinator for the given Vert.x instance. This method reads configuration
     * asynchronously and handles creation, startup, and caching.
     * <p>
     * The coordinator is always created, but only started if enabled via the {@code NEONBEE_CLUSTER_CLEANUP_ENABLED}
     * system property or environment variable (default: true). When disabled, the coordinator is created but not
     * started, allowing per-node control.
     *
     * @param vertx the Vert.x instance
     * @return Future that completes with the ClusterCleanupCoordinator (started if enabled, otherwise not started) or
     *         null if not clustered
     */
    public static Future<ClusterCleanupCoordinator> getOrCreateClusterCleanupCoordinator(
            Vertx vertx) {
        if (!vertx.isClustered()) {
            return Future.succeededFuture(null);
        }

        // Fast path: already created
        ClusterCleanupCoordinator existing = COORDINATORS.get(vertx);
        if (existing != null) {
            return Future.succeededFuture(existing);
        }

        // Read config and create coordinator
        String configName = ClusterCleanupCoordinator.class.getName();
        return ConfigHelper
                .readConfig(vertx, configName, new JsonObject())
                .map(config -> {
                    ClusterManager clusterManager = getClusterManager(vertx)
                            .orElse(null);
                    if (clusterManager == null) {
                        return null;
                    }

                    // Check if enabled via system property or environment variable (per-node control)
                    boolean enabled = isCoordinatorEnabled();

                    // Extract configuration values with defaults
                    long lockTimeoutMs = config.getLong(
                            "lockTimeoutMs",
                            DEFAULT_LOCK_TIMEOUT_MS);
                    int maxNodesPerBatch = config.getInteger(
                            "maxNodesPerBatch",
                            DEFAULT_MAX_NODES_PER_BATCH);

                    // Always create the coordinator, even if disabled
                    ClusterCleanupCoordinator coordinator = COORDINATORS.computeIfAbsent(
                            vertx,
                            v -> new ClusterCleanupCoordinator(
                                    v,
                                    clusterManager,
                                    DEFAULT_INTERVAL_MS,
                                    lockTimeoutMs,
                                    maxNodesPerBatch,
                                    () -> io.neonbee.NeonBee.get(vertx)));

                    // Only start the coordinator if enabled
                    if (enabled) {
                        coordinator.start(); // async fire-and-forget
                    }
                    return coordinator;
                });
    }

    /**
     * Get the cached ClusterCleanupCoordinator for the given Vert.x instance without creating or starting it.
     *
     * @param vertx the Vert.x instance
     * @return the cached ClusterCleanupCoordinator or null if not cached
     */
    public static ClusterCleanupCoordinator getCachedCoordinator(Vertx vertx) {
        return COORDINATORS.get(vertx);
    }

    /**
     * Remove the cached ClusterCleanupCoordinator for the given Vert.x instance.
     *
     * @param vertx the Vert.x instance
     * @return the removed ClusterCleanupCoordinator or null if not cached
     */
    public static ClusterCleanupCoordinator removeCachedCoordinator(
            Vertx vertx) {
        return COORDINATORS.remove(vertx);
    }
}
