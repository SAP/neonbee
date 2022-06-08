package io.neonbee.health;

import java.util.function.Function;

import com.google.common.annotations.VisibleForTesting;
import com.hazelcast.core.HazelcastInstance;

import io.neonbee.NeonBee;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.healthchecks.Status;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;

public class HazelcastClusterHealthCheck extends AbstractHealthCheck {
    /**
     * Name of the health check.
     */
    public static final String NAME = "cluster.hazelcast";

    @VisibleForTesting
    static final String EXPECTED_CLUSTER_SIZE_KEY = "expectedClusterSize";

    private final HazelcastClusterManager clusterManager;

    /**
     * Constructs an instance of {@link HazelcastClusterHealthCheck}.
     *
     * @param neonBee        the current NeonBee instance
     * @param clusterManager the cluster manager of Hazelcast
     */
    public HazelcastClusterHealthCheck(NeonBee neonBee, HazelcastClusterManager clusterManager) {
        super(neonBee);
        this.clusterManager = clusterManager;
    }

    @Override
    public String getId() {
        return NAME;
    }

    @Override
    public boolean isGlobal() {
        return true;
    }

    @Override
    Function<NeonBee, Handler<Promise<Status>>> createProcedure() {
        return neonBee -> healthCheckPromise -> neonBee.getVertx().executeBlocking(promise -> {
            HazelcastInstance instance = clusterManager.getHazelcastInstance();
            boolean lifecycleServiceRunning = instance.getLifecycleService().isRunning();
            boolean ok = instance.getPartitionService().isClusterSafe() && lifecycleServiceRunning;

            int clusterSize = instance.getCluster().getMembers().size();
            if (config.containsKey(EXPECTED_CLUSTER_SIZE_KEY)) {
                ok = ok && config.getInteger(EXPECTED_CLUSTER_SIZE_KEY) == clusterSize;
            }

            promise.complete(new Status().setOk(ok)
                    .setData(new JsonObject().put("clusterState", instance.getCluster().getClusterState())
                            .put("clusterSize", clusterSize)
                            .put("lifecycleServiceState", lifecycleServiceRunning ? "ACTIVE" : "INACTIVE")));
        }, false, healthCheckPromise);
    }
}
