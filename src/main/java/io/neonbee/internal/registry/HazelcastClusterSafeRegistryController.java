package io.neonbee.internal.registry;

import com.hazelcast.partition.PartitionService;

import io.neonbee.internal.cluster.hazelcast.HazelcastMigration;
import io.vertx.core.Future;
import io.vertx.core.Vertx;

public class HazelcastClusterSafeRegistryController extends SelfCleaningRegistryController {
    private final HazelcastMigration hazelcastMigration;

    /**
     * Creates a SelfCleaningRegistryController that is aware of the Hazelcast cluster.
     * <p>
     * This controller delays the cleanUpAllRegistriesForNode call if the Hazelcast cluster is not in a safe state.
     *
     * @param vertx            the Vertx instance
     * @param partitionService Hazelcast PartitionService instance
     */
    public HazelcastClusterSafeRegistryController(Vertx vertx, PartitionService partitionService) {
        super(vertx);
        this.hazelcastMigration = new HazelcastMigration(partitionService);
    }

    @Override
    public Future<Void> cleanUpAllRegistriesForNode(String nodeId) {
        return hazelcastMigration.onReplicaMigrationFinished("clean up all registries for node with ID: " + nodeId)
                .compose(v -> super.cleanUpAllRegistriesForNode(nodeId));
    }
}
