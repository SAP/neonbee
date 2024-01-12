package io.neonbee.test.helper;

import static com.google.common.base.Splitter.onPattern;
import static io.vertx.core.MultiMap.caseInsensitiveMultiMap;
import static io.vertx.core.buffer.Buffer.buffer;
import static io.vertx.core.http.HttpHeaders.CONTENT_TYPE;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;

import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;

/**
 * Represents parts of an <a href=
 * "http://docs.oasis-open.org/odata/odata/v4.01/odata-v4.01-part1-protocol.html#sec_MultipartBatchResponse">OData
 * Multipart Batch Response</a>.
 */
public final class MultipartResponse {
    private final HttpResponse<Buffer> httpResponse;

    private final List<Part> parts;

    private MultipartResponse(HttpResponse<Buffer> httpResponse, List<Part> parts) {
        this.httpResponse = httpResponse;
        this.parts = parts;
    }

    public HttpResponse<Buffer> getHttpResponse() {
        return httpResponse;
    }

    public List<Part> getParts() {
        return parts;
    }

    /**
     * Extracts boundary and parts from an {@link HttpResponse} expected to be an <a href=
     * "http://docs.oasis-open.org/odata/odata/v4.01/odata-v4.01-part1-protocol.html#sec_MultipartBatchResponse">OData
     * Multipart Batch Response</a>
     *
     * @param batchResponse the HTTP response
     * @return the multipart response information extracted from given HTTP response
     */
    @VisibleForTesting
    static MultipartResponse of(HttpResponse<Buffer> batchResponse) {
        String boundary = extractBoundary(batchResponse);
        String delimiter = "--" + boundary;
        String body = batchResponse.body().toString();
        ArrayList<Part> responseParts = new ArrayList<>();

        List<String> rawParts =
                Splitter.on(Pattern.compile("(\\r\\n)?" + Pattern.quote(delimiter) + "(\\r\\n|--)")).splitToList(body);
        // omit first part and last part
        for (int x = 1; x < rawParts.size() - 1; x++) {
            responseParts.add(parseAsPart(rawParts.get(x)));
        }

        return new MultipartResponse(batchResponse, responseParts);
    }

    @VisibleForTesting
    static String extractBoundary(HttpResponse<Buffer> batchResponse) {
        String contentType = Optional.ofNullable(batchResponse.getHeader(CONTENT_TYPE.toString())).orElseThrow(
                () -> new IllegalArgumentException("batchResponse does not contain mandatory header " + CONTENT_TYPE));
        List<String> parts = Splitter.on("boundary=").limit(2).splitToList(contentType);
        if (parts.size() == 1) {
            throw new IllegalArgumentException("batchResponse header " + CONTENT_TYPE + " does not contain boundary!");
        }
        return parts.get(1);
    }

    /**
     * Parses a string expected to be a part of an <a href=
     * "http://docs.oasis-open.org/odata/odata/v4.01/odata-v4.01-part1-protocol.html#sec_MultipartBatchResponse">OData
     * Multipart Batch Response</a>. The string MUST NOT include the boundary delimiter.
     *
     * @param value string value of a batch response part
     * @return part information extracted from given value string
     */
    @VisibleForTesting
    static Part parseAsPart(String value) {
        List<String> sections = Splitter.on(Pattern.compile("(\\r\\n){2}")).limit(3).splitToList(value);

        Pattern lineSeparatorPattern = Pattern.compile("\\r\\n");
        List<String> statusLineAndHeaders = Splitter.on(lineSeparatorPattern).limit(2).splitToList(sections.get(1));
        int statusCode = Integer.parseInt(statusLineAndHeaders.get(0).replaceAll("(.*)(\\d{3})(.*)", "$2"));
        Map<String, String> headers =
                Splitter.on(lineSeparatorPattern).withKeyValueSeparator(onPattern(": |:"))
                        .split(statusLineAndHeaders.get(1));

        return new Part(statusCode, caseInsensitiveMultiMap().addAll(headers), buffer(sections.get(2)));
    }

    /**
     * Represents one part of an <a href=
     * "http://docs.oasis-open.org/odata/odata/v4.01/odata-v4.01-part1-protocol.html#sec_MultipartBatchResponse">OData
     * Multipart Batch Response</a>.
     */
    public static class Part {
        private final int statusCode;

        private final MultiMap headers;

        private final Buffer body;

        Part(int statusCode, MultiMap headers, Buffer body) {
            this.statusCode = statusCode;
            this.headers = Optional.ofNullable(headers).orElse(caseInsensitiveMultiMap());
            this.body = body;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public MultiMap getHeaders() {
            return headers;
        }

        /**
         * Returns the value of the header with the specified name. If there is more than one value for the specified
         * header name, only the first value is returned.
         *
         * @param name name of the header to search
         * @return first header value or {@code null} if there is no such entry
         */
        public String getHeader(String name) {
            return headers.get(name);
        }

        public Buffer getBody() {
            return body;
        }
    }
}
