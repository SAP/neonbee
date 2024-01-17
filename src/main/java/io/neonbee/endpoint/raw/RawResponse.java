package io.neonbee.endpoint.raw;

import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;

/**
 * Raw response object.
 * <p>
 * This object is used to transport the response from the {@link io.neonbee.data.DataVerticle} to the
 * {@link RawEndpoint}.
 */
public class RawResponse {

    private int statusCode;

    private String reasonPhrase;

    private MultiMap headers;

    private Buffer body;

    /**
     * Gets the status code.
     *
     * @return the status code
     */
    public int getStatusCode() {
        return statusCode;
    }

    /**
     * Sets the status code.
     *
     * @param statusCode the status code
     * @return RawResponse to allow chaining
     */
    public RawResponse setStatusCode(int statusCode) {
        this.statusCode = statusCode;
        return this;
    }

    /**
     * Gets the reason phrase.
     *
     * @return the reason phrase
     */
    public String getReasonPhrase() {
        return reasonPhrase;
    }

    /**
     * Sets the reason phrase.
     *
     * @param reasonPhrase the reason phrase
     * @return RawResponse to allow chaining
     */
    public RawResponse setReasonPhrase(String reasonPhrase) {
        this.reasonPhrase = reasonPhrase;
        return this;
    }

    /**
     * Gets the headers.
     *
     * @return the headers
     */
    public MultiMap getHeaders() {
        return headers;
    }

    /**
     * Sets the headers.
     *
     * @param headers the headers
     * @return RawResponse to allow chaining
     */
    public RawResponse setHeaders(MultiMap headers) {
        this.headers = headers;
        return this;
    }

    /**
     * Gets the body.
     *
     * @return the body
     */
    public Buffer getBody() {
        return body;
    }

    /**
     * Sets the body.
     *
     * @param body the body
     * @return RawResponse to allow chaining
     */
    public RawResponse setBody(Buffer body) {
        this.body = body;
        return this;
    }

    /**
     * Creates a new raw response object.
     */
    public RawResponse() {}

    /**
     * Creates a new raw response object.
     *
     * @param statusCode   the status code
     * @param reasonPhrase the reason phrase
     * @param headers      the headers
     * @param body         the body
     */
    public RawResponse(int statusCode, String reasonPhrase, MultiMap headers, Buffer body) {
        this.statusCode = statusCode;
        this.reasonPhrase = reasonPhrase;
        this.headers = headers;
        this.body = body;
    }
}
