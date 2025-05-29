package io.neonbee.internal.cluster;

import java.util.Optional;
import java.util.Set;

import org.infinispan.manager.EmbeddedCacheManager;

import com.hazelcast.cluster.Member;
import com.retailsvc.vertx.spi.cluster.redis.RedisClusterManager;

import io.vertx.core.Vertx;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.ext.cluster.infinispan.InfinispanClusterManager;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;

public final class ClusterHelper {
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
    public static Optional<HazelcastClusterManager> getHazelcastClusterManager(Vertx vertx) {
        return getSpecificClusterManager(vertx, HazelcastClusterManager.class);
    }

    /**
     * Returns an optinal with {@link InfinispanClusterManager} if NeonBee was started in cluster mode and
     * {@link InfinispanClusterManager} is used as {@link ClusterManager}, otherwise it returns an empty Optional.
     *
     * @param vertx {@link Vertx} instance
     * @return null or the {@link InfinispanClusterManager} instance
     */
    public static Optional<InfinispanClusterManager> getInfinispanClusterManager(Vertx vertx) {
        return getSpecificClusterManager(vertx, InfinispanClusterManager.class);
    }

    /**
     * Returns an optinal with {@link RedisClusterManager} if NeonBee was started in cluster mode and
     * {@link RedisClusterManager} is used as {@link ClusterManager}, otherwise it returns an empty Optional.
     *
     * @param vertx {@link Vertx} instance
     * @return null or the {@link RedisClusterManager} instance
     */
    public static Optional<RedisClusterManager> getRedisClusterManager(Vertx vertx) {
        return getSpecificClusterManager(vertx, RedisClusterManager.class);
    }

    /**
     * Returns an optinal with the provided {@link ClusterManager} class if NeonBee was started in cluster mode and the
     * {@link ClusterManager} is used as {@link ClusterManager}, otherwise it returns an empty Optional.
     *
     * @param vertx {@link Vertx} instance
     * @return null or the {@link InfinispanClusterManager} instance
     */
    private static <T extends ClusterManager> Optional<T> getSpecificClusterManager(Vertx vertx, Class<T> cmClass) {
        return getClusterManager(vertx).map(cm -> cmClass.isInstance(cm) ? cmClass.cast(cm) : null);
    }

    /**
     * Get the cluster node ID.
     *
     * @param vertx {@link Vertx} instance
     * @return the cluster node ID
     */
    public static String getClusterNodeId(Vertx vertx) {
        return getClusterManager(vertx).map(ClusterManager::getNodeId).orElseThrow(() -> new IllegalStateException(
                "Can not retrieve the ClusterManager. Is vert.x running in a cluster?"));
    }

    /**
     * Is this cluster node the leader?
     *
     * @param vertx {@link Vertx} instance
     * @return true if this node is the leader, otherwise false
     */
    public static boolean isLeader(Vertx vertx) {
        if (!vertx.isClustered()) {
            return true;
        }

        return getHazelcastClusterManager(vertx).map(hcm -> isLeader(hcm))
                .or(() -> getInfinispanClusterManager(vertx).map(icm -> isLeader(icm)))
                .orElseThrow(() -> new IllegalStateException(
                        "Can not find the cluster leader. Is vert.x running in a cluster?"));
    }

    private static Boolean isLeader(InfinispanClusterManager icm) {
        EmbeddedCacheManager cacheContainer = (EmbeddedCacheManager) icm.getCacheContainer();
        return cacheContainer.isCoordinator();
    }

    private static boolean isLeader(HazelcastClusterManager hcm) {
        // getMembers() returns a set. The first element is the leader.
        // The underlying set is a kind of linked hashset and In this case the order is kept.
        // see https://github.com/hazelcast/hazelcast/issues/3760
        Set<Member> members = hcm.getHazelcastInstance().getCluster().getMembers();
        Member oldestMember = members.iterator().next();
        return oldestMember.localMember();
    }
}
