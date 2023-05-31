package io.neonbee.data;

import static io.neonbee.internal.helper.StringHelper.EMPTY;
import static java.util.Objects.requireNonNull;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

@SuppressWarnings({ "OverrideThrowableToString", "checkstyle:JavadocVariable" })
public class DataException extends RuntimeException {
    public static final int FAILURE_CODE_UNKNOWN_STRATEGY = 1000;

    public static final int FAILURE_CODE_MISSING_MESSAGE_CODEC = 1001;

    public static final int FAILURE_CODE_NO_HANDLERS = 1010;

    public static final int FAILURE_CODE_TIMEOUT = 1020;

    public static final int FAILURE_CODE_PROCESSING_FAILED = 1030;

    public static final int FAILURE_CODE_DECODE_EXCEPTION = 1040;

    private static final long serialVersionUID = 1L;

    private final int failureCode;

    private final Map<String, Object> failureDetail;

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
        this(failureCode, message, Map.of());
    }

    /**
     * Create a DataException.
     *
     * @param failureCode   the failure code
     * @param message       the failure message
     * @param failureDetail the failure detail message to be propagated to the invoker, but should not be exposed to the
     *                      consumer. Must be compatible to {@link JsonObject#JsonObject(Map)}. The type of the passed
     *                      object must be either: {@link String} or {@link JsonObject} or {@link JsonArray}
     */
    public DataException(int failureCode, String message, Map<String, Object> failureDetail) {
        super(message);
        this.failureCode = failureCode;
        this.failureDetail = requireNonNull(failureDetail);
    }

    /**
     * Get the failure code for the message.
     *
     * @return the failure code
     */
    public int failureCode() {
        return failureCode;
    }

    /**
     * Get the failure detail message.
     *
     * @return the failure detail
     */
    public Map<String, Object> failureDetail() {
        return Collections.unmodifiableMap(failureDetail);
    }

    @Override
    public int hashCode() {
        return Objects.hash(failureCode, getMessage(), failureDetail);
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
        return failureCode == other.failureCode
                && Optional.ofNullable(getMessage()).orElse(EMPTY)
                        .equals(Optional.ofNullable(other.getMessage()).orElse(EMPTY))
                && failureDetail().equals(other.failureDetail());
    }

    @Override
    public String toString() {
        return "(" + failureCode + ")" + Optional.ofNullable(getMessage()).map(msg -> " " + msg).orElse(EMPTY);
    }
}
