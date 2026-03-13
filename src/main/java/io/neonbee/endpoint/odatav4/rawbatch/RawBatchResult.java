package io.neonbee.endpoint.odatav4.rawbatch;

import io.vertx.core.buffer.Buffer;

/**
 * Result of raw batch processing: either a response buffer or a decision to delegate / signal handled.
 */
public final class RawBatchResult {

    private final Buffer buffer;

    private final RawBatchDecision decision;

    private RawBatchResult(Buffer buffer, RawBatchDecision decision) {
        this.buffer = buffer;
        this.decision = decision;
    }

    /**
     * Creates a result that carries a response buffer (raw handling).
     *
     * @param buffer the response body
     * @return a RawBatchResult with the buffer
     */
    public static RawBatchResult buffer(Buffer buffer) {
        return new RawBatchResult(buffer, null);
    }

    /**
     * The response buffer, or null if this result carries a decision.
     *
     * @return the buffer or null
     */
    public Buffer buffer() {
        return buffer;
    }

    /**
     * Creates a result that carries a decision (e.g. delegate to default or handled raw).
     *
     * @param decision the decision
     * @return a RawBatchResult with the decision
     */
    public static RawBatchResult decision(RawBatchDecision decision) {
        return new RawBatchResult(null, decision);
    }

    /**
     * The decision, or null if this result carries a buffer.
     *
     * @return the decision or null
     */
    public RawBatchDecision decision() {
        return decision;
    }

    /**
     * Whether this result has a buffer (raw handling).
     *
     * @return true if this result has a buffer
     */
    public boolean hasBuffer() {
        return buffer != null;
    }
}
