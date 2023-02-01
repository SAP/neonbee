package io.neonbee.test.base;

import io.vertx.core.buffer.Buffer;

import java.util.Map;

/**
 * Represents parts of a response following an OData batch request.
 */
public class ODataBatchResponsePart {
    private int statusCode;
    private Map<String, String> headers;
    private Buffer payload;

    public int getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public Buffer getPayload() {
        return payload;
    }

    public void setPayload(Buffer payload) {
        this.payload = payload;
    }
}
