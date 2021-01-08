package io.neonbee.data;

import static io.neonbee.internal.Helper.EMPTY;

import java.util.Objects;
import java.util.Optional;

@SuppressWarnings({ "OverrideThrowableToString", "checkstyle:JavadocVariable" })
public class DataException extends RuntimeException {
    public static final int FAILURE_CODE_UNKNOWN_STRATEGY = 1000;

    public static final int FAILURE_CODE_MISSING_MESSAGE_CODEC = 1001;

    public static final int FAILURE_CODE_NO_HANDLERS = 1010;

    public static final int FAILURE_CODE_TIMEOUT = 1020;

    public static final int FAILURE_CODE_PROCESSING_FAILED = 1030;

    private static final long serialVersionUID = 1L;

    private final int failureCode;

    /**
     * Create a DataException.
     */
    public DataException() {
        this(null);
    }

    /**
     * Create a DataException.
     *
     * @param message the failure message
     */
    public DataException(String message) {
        this(-1, message);
    }

    /**
     * Create a DataException.
     *
     * @param failureCode the failure code
     */
    public DataException(int failureCode) {
        this(failureCode, null);
    }

    /**
     * Create a DataException.
     *
     * @param failureCode the failure code
     * @param message     the failure message
     */
    public DataException(int failureCode, String message) {
        super(message);
        this.failureCode = failureCode;
    }

    /**
     * Get the failure code for the message.
     *
     * @return the failure code
     */
    public int failureCode() {
        return failureCode;
    }

    @Override
    public int hashCode() {
        return Objects.hash(failureCode, getMessage());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof DataException)) {
            return false;
        }
        DataException other = (DataException) obj;
        return failureCode == other.failureCode && getMessage().equals(other.getMessage());
    }

    @Override
    public String toString() {
        return "(" + failureCode + ") " + Optional.ofNullable(getMessage()).orElse(EMPTY);
    }
}
