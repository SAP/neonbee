package io.neonbee.internal.cluster.hazelcast;

import com.hazelcast.partition.ReplicaMigrationEvent;

/**
 * HazelcastReplicaMigrationException is thrown when the Hazelcast migration fails.
 */
public class HazelcastReplicaMigrationException extends RuntimeException {

    private static final long serialVersionUID = 4354100497375749139L;

    private final ReplicaMigrationEvent replicaMigrationEvent;

    /**
     * Constructs a new HazelcastReplicaMigrationException exception with the specified detail message and event.
     *
     * @param message the detail message (which is saved for later retrieval by the {@link #getMessage()} method).
     * @param event   the ReplicaMigrationEvent
     */
    public HazelcastReplicaMigrationException(String message, ReplicaMigrationEvent event) {
        super(message);
        this.replicaMigrationEvent = event;
    }

    /**
     * Constructs a new HazelcastReplicaMigrationException exception with the specified detail message, cause and event.
     *
     * @param message the detail message (which is saved for later retrieval by the {@link #getMessage()} method).
     * @param cause   the cause (which is saved for later retrieval by the {@link #getCause()} method). (A {@code null}
     *                value is permitted, and indicates that the cause is nonexistent or unknown.)
     * @param event   the ReplicaMigrationEvent
     */
    public HazelcastReplicaMigrationException(String message, Throwable cause, ReplicaMigrationEvent event) {
        super(message, cause);
        this.replicaMigrationEvent = event;
    }

    /**
     * Get the ReplicaMigrationEvent from the failed replica migration.
     *
     * @return the ReplicaMigrationEvent
     */
    public ReplicaMigrationEvent getReplicaMigrationEvent() {
        return replicaMigrationEvent;
    }
}
