package io.neonbee.internal.cluster;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.infinispan.manager.EmbeddedCacheManager;

import com.hazelcast.cluster.Member;

import io.neonbee.internal.cluster.coordinator.ClusterCleanupCoordinator;
import io.vertx.core.Vertx;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.ext.cluster.infinispan.InfinispanClusterManager;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;

public final class ClusterHelper {

    private static final Map<Vertx, ClusterCleanupCoordinator> COORDINATORS = new ConcurrentHashMap<>();

    private static final AtomicReference<Boolean> LEADER_CACHE = new AtomicReference<>();

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
     * Get or create and start a ClusterCleanupCoordinator for the given Vert.x instance. Returns a completed future
     * with null if not running in clustered mode. This method handles creation, startup, and caching in a single
     * operation.
     *
     * @param vertx the Vert.x instance
     * @return Future that completes with the started ClusterCleanupCoordinator or null
     */
    public static ClusterCleanupCoordinator getOrCreateClusterCleanupCoordinatorImmediate(
            Vertx vertx) {
        if (!vertx.isClustered()) {
            return null;
        }

        // Always return a coordinator instance, even if async map is not ready yet
        return COORDINATORS.computeIfAbsent(
                vertx,
                v -> {
                    ClusterManager clusterManager = getClusterManager(v)
                            .orElse(null);
                    if (clusterManager == null) {
                        return null;
                    }

                    ClusterCleanupCoordinator coordinator = new ClusterCleanupCoordinator(
                            v,
                            clusterManager);
                    coordinator.start(); // async fire-and-forget
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
