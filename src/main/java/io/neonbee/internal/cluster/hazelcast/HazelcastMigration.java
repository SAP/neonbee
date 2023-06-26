package io.neonbee.internal.cluster.hazelcast;

import java.util.UUID;

import com.hazelcast.partition.MigrationListener;
import com.hazelcast.partition.MigrationState;
import com.hazelcast.partition.PartitionService;
import com.hazelcast.partition.ReplicaMigrationEvent;

import io.neonbee.logging.LoggingFacade;
import io.vertx.core.Future;
import io.vertx.core.Promise;

public class HazelcastMigration {

    private final LoggingFacade logger = LoggingFacade.create();

    private final PartitionService partitionService;

    /**
     * Create a new HazelcastMigration instance.
     *
     * @param partitionService the Hazelcast PartitionService instance
     */
    public HazelcastMigration(PartitionService partitionService) {
        this.partitionService = partitionService;
    }

    /**
     * This method returns a future that completes when the Hazelcast replication migration is completed or failed.
     * <p>
     *
     * @param description a descriptive text that is appended to the log messages
     * @return Future object that is completed when the ReplicaMigration is finished. When the cluster is in a safe
     *         state this function returns a succeeded future.
     */
    public Future<Void> onReplicaMigrationFinished(String description) {
        if (partitionService.isClusterSafe()) {
            logger.info("Executing \"{}\"", description);
            return Future.succeededFuture();
        }

        return onReplicaMigrationFinished()
                .onSuccess(event -> logger.info(
                        "execute delayed execution for: \"{}\". The replica migration {} and took {} ms.", description,
                        event.isSuccess() ? "completed" : "failed", event.getElapsedTime()))
                .mapEmpty();
    }

    private Future<ReplicaMigrationEvent> onReplicaMigrationFinished() {
        // FIXME: test if the event is directly completed if no migration is in progress

        Promise<ReplicaMigrationEvent> promise = Promise.promise();
        onReplicaMigrationFinished(promise);
        return promise.future();
    }

    /**
     * This method completes the promise when the Hazelcast replication migration is completed or fails the promise if
     * the Hazelcast replication migration failed.
     * <p>
     *
     * @param promise {@link Promise} to completed the function.
     */
    private void onReplicaMigrationFinished(Promise<ReplicaMigrationEvent> promise) {
        UUID migrationListenerUuid = partitionService.addMigrationListener(new MigrationListener() {
            @Override
            public void migrationStarted(MigrationState state) {
                // not interested in
            }

            @Override
            public void migrationFinished(MigrationState state) {
                // not interested in
            }

            @Override
            public void replicaMigrationCompleted(ReplicaMigrationEvent event) {
                promise.complete(event);
            }

            @Override
            public void replicaMigrationFailed(ReplicaMigrationEvent event) {
                promise.fail(new HazelcastReplicaMigrationException("Hazelcast replica migration failed", event));
            }
        });

        logger.info("Added migration listener with UUID: {}", migrationListenerUuid);
        promise.future().onComplete(event -> {
            if (partitionService.removeMigrationListener(migrationListenerUuid)) {
                logger.info("Removed migration listener with UUID: {}", migrationListenerUuid);
            } else {
                logger.error("Failed to remove migration listener with UUID: {}", migrationListenerUuid);
            }
        });
    }
}
