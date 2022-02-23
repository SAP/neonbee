package io.neonbee.endpoint.health.checks;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.partition.PartitionService;

import io.neonbee.NeonBee;
import io.neonbee.endpoint.health.NeonBeeHealth;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.healthchecks.HealthChecks;
import io.vertx.ext.healthchecks.Status;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;

public final class ClusterHealthChecks {

    @VisibleForTesting
    static final String CLUSTER_PROCEDURE_NAME = "cluster";

    @VisibleForTesting
    static final String NODE_PROCEDURE_NAME = "node";

    private final long timeout;

    private final HealthChecks checks;

    private final HazelcastClusterManager clusterManager;

    private ClusterHealthChecks(NeonBeeHealth health) {
        this.timeout = health.timeout;
        this.checks = health.healthChecks;
        this.clusterManager = health.clusterManager;
    }

    /**
     * Registers all cluster health-checks to the {@link HealthChecks} instance of {@link NeonBeeHealth}.
     *
     * @param health the {@link NeonBeeHealth}
     * @return A succeeded Future if registering succeeds, a failed Future otherwise.
     */
    public static Future<Void> register(NeonBeeHealth health) {
        Vertx vertx = requireNonNull(NeonBee.get()).getVertx();
        return new ClusterHealthChecks(health).registerAll(vertx).mapEmpty();
    }

    private Future<HealthChecks> registerAll(Vertx vertx) {
        return Future.future(promise -> {
            checks.register(CLUSTER_PROCEDURE_NAME, timeout, createClusterCheck(clusterManager, vertx));
            checks.register(NODE_PROCEDURE_NAME, timeout, createLocalMemberCheck(clusterManager, vertx));
            promise.complete(checks);
        });
    }

    /**
     * Health-check procedure which checks whether the Hazelcast cluster is in a safe state. The cluster health status
     * is aggregated from each Hazelcast node. It reports a status {@link Status#OK()}, if there are no active partition
     * migrations and all backups are in sync for each partition. Status {@link Status#KO()}, otherwise.
     *
     * @param clusterManager the {@link HazelcastClusterManager}
     * @param vertx          the current Vert.x instance
     * @return a result handler which sets the {@link Status} depending on the cluster state.
     */
    @VisibleForTesting
    static Handler<Promise<Status>> createClusterCheck(HazelcastClusterManager clusterManager, Vertx vertx) {
        return healthCheckPromise -> {
            requireNonNull(vertx);
            vertx.executeBlocking(promise -> {
                HazelcastInstance instance = clusterManager.getHazelcastInstance();
                boolean clusterSafe = instance.getPartitionService().isClusterSafe();
                boolean lifecycleServiceRunning = instance.getLifecycleService().isRunning();

                promise.complete(new Status().setOk(clusterSafe && lifecycleServiceRunning)
                        .setData(new JsonObject().put("clusterState", instance.getCluster().getClusterState())
                                .put("clusterSize", instance.getCluster().getMembers().size())
                                .put("lifecycleServiceState", lifecycleServiceRunning ? "ACTIVE" : "INACTIVE")));
            }, false, healthCheckPromise);
        };
    }

    /**
     * Health-check procedure which checks the status of the local members in the Hazelcast cluster.
     *
     * @param clusterManager the {@link HazelcastClusterManager}
     * @param vertx          the current Vert.x instance
     * @return a result handler which sets the {@link Status} depending on the local member's state.
     */
    @VisibleForTesting
    static Handler<Promise<Status>> createLocalMemberCheck(HazelcastClusterManager clusterManager, Vertx vertx) {
        return healthCheckPromise -> {
            requireNonNull(vertx);
            vertx.executeBlocking(promise -> {
                PartitionService partitionService = clusterManager.getHazelcastInstance().getPartitionService();
                promise.complete(new Status().setOk(partitionService.isLocalMemberSafe()));
            }, false, healthCheckPromise);
        };
    }
}
