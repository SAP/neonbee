package io.neonbee.test.base;

import com.google.common.escape.Escaper;
import com.google.common.net.UrlEscapers;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpVersion;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static java.lang.String.format;

/**
 * Provides methods to serialize the body of an OData batch request according to the
 * <a href="http://docs.oasis-open.org/odata/odata/v4.01/odata-v4.01-part1-protocol.html#sec_MultipartBatchRequestBody">OData Multipart Request Body</a>
 * specification.
 */
public class ODataBatchRequestBody {

    private final List<? extends Part> parts;

    public ODataBatchRequestBody(Part... parts) {
        this(Arrays.stream(parts).collect(Collectors.toList()));
    }

    public ODataBatchRequestBody(List<? extends Part> parts) {
        this.parts = parts;
    }

    /**
     * Appends the contained each {@link ODataBatchRequestBody.Part} to the given buffer.
     * The {@link ODataBatchRequestBody.Part}s are serialized in accordance with the
     * <a href="http://docs.oasis-open.org/odata/odata/v4.01/odata-v4.01-part1-protocol.html#sec_MultipartBatchRequestBody">OData Multipart Request Body</a>
     * specification.
     *
     * @param buffer  The buffer to append t
     * @param context provides contextual information for the parent batch request.
     * @return new buffer instance containing the serialized part.
     */
    public Buffer writeTo(Buffer buffer, BatchContext context) {
        Buffer result = buffer;
        for (Part part : parts) {
            result = part.writeTo(result, context);
        }
        result = closeBoundary(context.getBoundary(), buffer);
        return result;
    }

    private Buffer closeBoundary(String boundary, Buffer buffer) {
        return buffer.appendString("--" + boundary + "--\n");
    }

    /**
     * Represents the bare minimum for an OData batch request body part implementation.
     */
    public interface Part {
        Buffer writeTo(Buffer buffer, BatchContext context);
    }

    /**
     * Base class for batch request parts, providing convenience functions for serialization.
     */
    static class PartBase {

        public static final String CONTENT_TYPE_HTTP = "application/http";

        Buffer startBoundary(String boundary, Buffer buffer) {
            return buffer.appendString("--" + boundary + "\n");
        }

        Buffer writeHeader(String key, String value, Buffer buffer) {
            return buffer.appendString(key + ":" + value + "\n");
        }

        Buffer writeMandatoryHeaders(Buffer buffer) {
            return writeHeader(HttpHeaders.CONTENT_TYPE.toString(), CONTENT_TYPE_HTTP, buffer);
        }
    }

    /**
     * Defines an OData batch request part, itself consisting of 1..n requests, each separated by a boundary delimiter line.
     * See <a href="http://docs.oasis-open.org/odata/odata/v4.01/odata-v4.01-part1-protocol.html#sec_MultipartBatchRequestBody">OData Multipart Request Body</a> for more details.
     */
    public static class Request extends PartBase implements Part {
        private final ODataRequest request;

        public Request(ODataRequest request) {
            this.request = request;
        }

        @Override
        public Buffer writeTo(Buffer buffer, BatchContext context) {
            // create URI
            String uri = request.getUri();
            String namespace = request.getEntity().getNamespace();
            if (uri.startsWith(namespace)) {
                // providing namespace prefix with the URL results in bad request from olingo
                // failing example: GET io.neonbee.test.TestService1/AllPropertiesNullable('id-1') HTTP/1.1
                // working example: GET AllPropertiesNullable('id-1') HTTP/1.1
                uri = uri.substring(namespace.length() + 1);
            }

            // add URI query if required
            MultiMap query = request.getQuery();
            if (query != null && !query.isEmpty()) {
                Escaper escaper = UrlEscapers.urlPathSegmentEscaper();
                String queryString = query.entries().stream()
                    .map(entry -> format("%s=%s", entry.getKey(), entry.getValue()))
                    .map(escaper::escape)
                    .collect(Collectors.joining("&"));
                uri += "?" + queryString;
            }

            final AtomicReference<Buffer> partRef = new AtomicReference<>(buffer);
            // append boundary delimiter line
            partRef.updateAndGet(buf -> startBoundary(context.getBoundary(), buf));
            // append boundary headers
            partRef.updateAndGet(this::writeMandatoryHeaders);
            String finalUri = uri;

            // append HTTP request line
            partRef.updateAndGet(part -> part.appendString("\n")
                // http version needs to be provided in upper case to be valid for processing in Olingo
                .appendString(format("%s %s %s\n", request.getMethod().name(), finalUri, HttpVersion.HTTP_1_1.alpnName().toUpperCase())));

            // append request headers
            request.getHeaders().forEach((name, value) -> partRef.getAndUpdate(ref -> writeHeader(name, value, ref)));

            // finally append body or empty line
            return partRef.get()
                .appendBuffer(Optional.ofNullable(request.getBody()).orElse(Buffer.buffer("\n")));
        }
    }
}
