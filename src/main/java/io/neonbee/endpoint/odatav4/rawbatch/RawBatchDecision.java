package io.neonbee.endpoint.odatav4.rawbatch;

/**
 * Decision returned by raw batch processing verticles.
 */
public enum RawBatchDecision {
    /**
     * Raw path handled without falling back to default ODataProxy batch handling.
     */
    HANDLED_RAW,

    /**
     * Raw path requests the endpoint handler to continue with default ODataProxy batch handling.
     */
    DELEGATE_TO_DEFAULT
}
