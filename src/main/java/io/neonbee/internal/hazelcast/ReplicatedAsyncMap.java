package io.neonbee.internal.hazelcast;

import static io.vertx.spi.cluster.hazelcast.impl.ConversionUtils.convertParam;
import static io.vertx.spi.cluster.hazelcast.impl.ConversionUtils.convertReturn;
import static io.vertx.spi.cluster.hazelcast.impl.HazelcastServerID.convertServerID;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.hazelcast.replicatedmap.ReplicatedMap;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.shareddata.AsyncMap;

/**
 * An implementation of {@link AsyncMap} which is backed by a Hazelcast ReplicatedMap.
 *
 * @param <K> the key type
 * @param <V> the value type
 */
public class ReplicatedAsyncMap<K, V> implements AsyncMap<K, V> {

    private final Vertx vertx;

    private final ReplicatedMap<K, V> map;

    /**
     * Constructs a new instance of {@link ReplicatedAsyncMap}.
     *
     * @param vertx the Vert.x instance
     * @param map   the Hazelcast ReplicatedMap
     */
    public ReplicatedAsyncMap(Vertx vertx, ReplicatedMap<K, V> map) {
        this.vertx = vertx;
        this.map = map;
    }

    @Override
    public Future<V> get(K k) {
        K kk = convertParam(k);
        return vertx.executeBlocking(() -> {
            V vv = map.get(kk);
            return convertReturn(vv);
        }, false);
    }

    @Override
    public Future<Void> put(K k, V v) {
        K kk = convertParam(k);
        V vv = convertParam(v);
        return vertx.executeBlocking(() -> {
            map.put(kk, convertServerID(vv));
            return null;
        }, false);
    }

    @Override
    public Future<Void> put(K k, V v, long ttl) {
        K kk = convertParam(k);
        V vv = convertParam(v);
        return vertx.executeBlocking(() -> {
            V prev = map.put(kk, convertServerID(vv), ttl, MILLISECONDS);
            return convertReturn(prev);
        }, false);
    }

    @Override
    public Future<V> putIfAbsent(K k, V v) {
        K kk = convertParam(k);
        V vv = convertParam(v);
        return vertx.executeBlocking(() -> {
            V prev = map.putIfAbsent(kk, convertServerID(vv));
            return convertReturn(prev);
        }, false);
    }

    @Override
    public Future<V> putIfAbsent(K k, V v, long ttl) {
        return get(k)
                .compose(vv -> {
                    if (vv == null) {
                        return put(k, v, ttl).map(none -> v);
                    } else {
                        return Future.succeededFuture();
                    }
                });
    }

    @Override
    public Future<V> remove(K k) {
        K kk = convertParam(k);
        return vertx.executeBlocking(() -> {
            V prev = map.remove(kk);
            return convertReturn(prev);
        }, false);
    }

    @Override
    public Future<Boolean> removeIfPresent(K k, V v) {
        K kk = convertParam(k);
        V vv = convertParam(v);
        return vertx.executeBlocking(() -> map.remove(kk, vv), false);
    }

    @Override
    public Future<V> replace(K k, V v) {
        K kk = convertParam(k);
        V vv = convertParam(v);
        return vertx.executeBlocking(() -> {
            V prev = map.replace(kk, vv);
            return convertReturn(prev);
        }, false);
    }

    @Override
    public Future<Boolean> replaceIfPresent(K k, V oldValue, V newValue) {
        K kk = convertParam(k);
        V oldVv = convertParam(oldValue);
        V newVv = convertParam(newValue);
        return vertx.executeBlocking(() -> map.replace(kk, oldVv, newVv), false);
    }

    @Override
    public Future<Void> clear() {
        return vertx.executeBlocking(() -> {
            map.clear();
            return null;
        }, false);
    }

    @Override
    public Future<Integer> size() {
        return vertx.executeBlocking(map::size, false);
    }

    @Override
    public Future<Set<K>> keys() {
        return vertx.executeBlocking(() -> {
            Set<K> set = new HashSet<>();
            for (K kk : map.keySet()) {
                K k = convertReturn(kk);
                set.add(k);
            }
            return set;
        }, false);
    }

    @Override
    public Future<List<V>> values() {
        return vertx.executeBlocking(() -> {
            List<V> list = new ArrayList<>();
            for (V vv : map.values()) {
                V v = convertReturn(vv);
                list.add(v);
            }
            return list;
        }, false);
    }

    @Override
    public Future<Map<K, V>> entries() {
        return vertx.executeBlocking(() -> {
            Map<K, V> result = new HashMap<>();
            for (Entry<K, V> entry : map.entrySet()) {
                K k = convertReturn(entry.getKey());
                V v = convertReturn(entry.getValue());
                result.put(k, v);
            }
            return result;
        }, false);
    }
}
