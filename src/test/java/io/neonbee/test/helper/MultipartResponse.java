package io.neonbee.test.helper;

import static io.vertx.core.MultiMap.caseInsensitiveMultiMap;
import static io.vertx.core.http.HttpHeaders.CONTENT_TYPE;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;

import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;

/**
 * Represents boundary and parts of an <a href=
 * "http://docs.oasis-open.org/odata/odata/v4.01/odata-v4.01-part1-protocol.html#sec_MultipartBatchResponse">OData
 * Multipart Batch Response</a>.
 */
public class MultipartResponse {
    private final String boundary;

    private final List<Part> parts;

    public MultipartResponse(String boundary, List<Part> parts) {
        this.boundary = boundary;
        this.parts = parts;
    }

    public String getBoundary() {
        return boundary;
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

        int startIndex = 0;
        while ((startIndex = body.indexOf(delimiter, startIndex)) >= 0) {
            int nextIndex = body.indexOf(delimiter, startIndex + 1);
            if (nextIndex < 0) {
                // this can only happen on the final delimiter line
                break;
            }

            int endOfLineIndex = body.indexOf('\n', startIndex);
            String partString = body.substring(endOfLineIndex + 1, nextIndex - 1);
            responseParts.add(parseAsPart(partString));
            startIndex = nextIndex;
        }

        return new MultipartResponse(boundary, responseParts);
    }

    @VisibleForTesting
    static String extractBoundary(HttpResponse<Buffer> batchResponse) {
        String contentType = Optional.ofNullable(batchResponse.headers().get(CONTENT_TYPE)).orElseThrow(
                () -> new IllegalArgumentException("batchResponse does not contain mandatory header " + CONTENT_TYPE));
        int indexOfBoundary = contentType.indexOf("boundary");
        if (indexOfBoundary < 0) {
            throw new IllegalArgumentException("batchResponse header " + CONTENT_TYPE + " does not contain boundary!");
        }
        String[] boundaryKeyAndValue = contentType.substring(indexOfBoundary).split("=", 2);
        if (boundaryKeyAndValue.length < 2) {
            throw new IllegalArgumentException(
                    "Boundary not properly specified in batchResponse header " + CONTENT_TYPE + "!");
        }
        return boundaryKeyAndValue[1];
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
        Collector<Map.Entry<String, String>, ?, Map<String, String>> headerCollector =
                Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue);
        Function<String, Map.Entry<String, String>> headerMapper = header -> {
            String[] keyAndValue = header.split(":", 2);
            return Map.entry(keyAndValue[0].trim(), keyAndValue[1].replaceFirst("\\R{1,2}$", "").trim());
        };

        String[] sections = value.split("\\R{2,}", 3);

        Map<String, String> partHeaders = sections[0].lines().map(headerMapper).collect(headerCollector);

        String responseHead = sections[1];
        Integer statusCode =
                responseHead.lines().findFirst().map(status -> Iterables.get(Splitter.on(' ').split(status), 1))
                        .map(Integer::parseInt)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Status code extraction from response part failed!"));
        Map<String, String> responseHeaders = responseHead.lines().skip(1).map(headerMapper).collect(headerCollector);

        return new Part(statusCode, caseInsensitiveMultiMap().addAll(partHeaders),
                caseInsensitiveMultiMap().addAll(responseHeaders), Buffer.buffer(sections[2]));
    }

    /**
     * Represents one part of an <a href=
     * "http://docs.oasis-open.org/odata/odata/v4.01/odata-v4.01-part1-protocol.html#sec_MultipartBatchResponse">OData
     * Multipart Batch Response</a>.
     */
    public static class Part {
        private final int statusCode;

        private final MultiMap partHeaders;

        private final MultiMap responseHeaders;

        private final Buffer payload;

        public Part(int statusCode, MultiMap partHeaders, MultiMap responseHeaders, Buffer payload) {
            this.statusCode = statusCode;
            this.partHeaders = Optional.ofNullable(partHeaders).orElse(caseInsensitiveMultiMap());
            this.responseHeaders = Optional.ofNullable(responseHeaders).orElse(caseInsensitiveMultiMap());
            this.payload = payload;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public MultiMap getPartHeaders() {
            return partHeaders;
        }

        public MultiMap getResponseHeaders() {
            return responseHeaders;
        }

        public Buffer getPayload() {
            return payload;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (!(o instanceof Part))
                return false;
            Part part = (Part) o;
            return statusCode == part.statusCode && partHeaders.equals(part.partHeaders) && responseHeaders.equals(
                    part.responseHeaders) && Objects.equals(payload, part.payload);
        }

        @Override
        public int hashCode() {
            return Objects.hash(statusCode, partHeaders, responseHeaders, payload);
        }
    }
}
