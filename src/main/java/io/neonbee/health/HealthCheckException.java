package io.neonbee.health;

public class HealthCheckException extends Exception {
    private static final long serialVersionUID = 1L;

    /**
     * Creates a new instance of {@link HealthCheckException}.
     *
     * @param message the message
     */
    public HealthCheckException(String message) {
        super(message);
    }

    /**
     * Creates a new instance of {@link HealthCheckException}.
     *
     * @param cause the cause
     */
    @SuppressWarnings("unused")
    public HealthCheckException(Exception cause) {
        super(cause.getMessage(), cause);
    }
}
