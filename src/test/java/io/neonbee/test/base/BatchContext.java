package io.neonbee.test.base;

/**
 * Contextual information for OData batch processing.
 */
public class BatchContext {
    private final String boundary;

    public BatchContext(String boundary) {
        this.boundary = boundary;
    }

    public String getBoundary() {
        return boundary;
    }
}
