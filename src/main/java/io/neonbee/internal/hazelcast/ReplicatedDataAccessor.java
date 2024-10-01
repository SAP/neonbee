package io.neonbee.internal.hazelcast;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.replicatedmap.ReplicatedMap;

import io.neonbee.internal.SharedDataAccessor;
import io.neonbee.internal.cluster.ClusterHelper;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;

/**
 * A SharedData implementation that uses Hazelcast ReplicatedMap for distributed maps.
 */
public class ReplicatedDataAccessor extends SharedDataAccessor {

    private final Vertx vertx;

    private final HazelcastInstance hazelcast;

    /**
     * Constructs a new instance of {@link ReplicatedDataAccessor}.
     *
     * @param vertx       the Vert.x instance
     * @param accessClass the class of the shared data accessor
     */
    public ReplicatedDataAccessor(Vertx vertx, Class<?> accessClass) {
        super(vertx, accessClass);
        this.vertx = vertx;
        this.hazelcast = ClusterHelper.getHazelcastClusterManager(vertx)
                .map(HazelcastClusterManager::getHazelcastInstance)
                .orElse(null);
    }

    @Override
    public <K, V> void getClusterWideMap(String name, Handler<AsyncResult<AsyncMap<K, V>>> asyncResultHandler) {
        this.<K, V>getClusterWideMap(name).onComplete(asyncResultHandler);
    }

    @Override
    public <K, V> Future<AsyncMap<K, V>> getClusterWideMap(String name) {
        return getAsyncMap(name);
    }

    @Override
    public <K, V> void getAsyncMap(String name, Handler<AsyncResult<AsyncMap<K, V>>> asyncResultHandler) {
        this.<K, V>getAsyncMap(name).onComplete(asyncResultHandler);
    }

    @Override
    public <K, V> Future<AsyncMap<K, V>> getAsyncMap(String name) {
        if (hazelcast == null) {
            return super.getAsyncMap(name);
        } else {
            return vertx.executeBlocking(() -> {
                ReplicatedMap<K, V> map = hazelcast.getReplicatedMap(name);
                return new ReplicatedAsyncMap<>(vertx, map);
            }, false);
        }
    }
}
