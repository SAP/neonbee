package io.neonbee.test.base;

import static io.vertx.core.buffer.Buffer.buffer;
import static io.vertx.core.http.HttpHeaders.CONTENT_TRANSFER_ENCODING;
import static io.vertx.core.http.HttpHeaders.CONTENT_TYPE;
import static java.util.UUID.randomUUID;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

import org.apache.olingo.commons.api.edm.FullQualifiedName;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.net.UrlEscapers;

import io.neonbee.NeonBee;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpVersion;
import io.vertx.ext.web.client.HttpResponse;

/**
 * This class can be used to construct an
 * <a href="http://docs.oasis-open.org/odata/odata/v4.01/odata-v4.01-part1-protocol.html#sec_BatchRequests">OData batch
 * request</a> for dispatching multiple single OData requests within one {@link io.vertx.ext.web.client.HttpRequest}.
 */
public class ODataBatchRequest extends AbstractODataRequest<ODataBatchRequest> {

    private static final Buffer EMPTY = buffer();

    private final String boundary;

    private final List<ODataRequest> requests = new ArrayList<>();

    @VisibleForTesting
    ODataBatchRequest(FullQualifiedName entity, String boundary) {
        super(entity);
        this.boundary = boundary;
        setMethod(HttpMethod.POST);
    }

    public ODataBatchRequest(FullQualifiedName entity) {
        this(entity, randomUUID().toString());
    }

    @Override
    protected ODataBatchRequest self() {
        return this;
    }

    @Override
    protected String getUri() {
        return getUriNamespacePath() + "$batch";
    }

    @VisibleForTesting
    Buffer createBody() {
        Buffer buffer = buffer();
        for (ODataRequest request : requests) {
            buffer.appendString("--" + boundary + "\n").appendBuffer(toBodyPart(request)).appendString("\n");
        }

        // close boundary
        return buffer.appendString("--" + boundary + "--");
    }

    @VisibleForTesting
    static Buffer toBodyPart(AbstractODataRequest<?> request) {
        // create URI
        // providing namespace prefix with the URL results in bad request from olingo
        // failing example: GET io.neonbee.test.TestService1/AllPropertiesNullable('id-1') HTTP/1.1
        // working example: GET AllPropertiesNullable('id-1') HTTP/1.1
        String uri = request.getUri().replaceFirst("^" + request.entity.getNamespace() + "/", "");
        // add query to URI if present
        if (!request.query.isEmpty()) {
            uri += '?' + UrlEscapers.urlPathSegmentEscaper()
                    .escape(Joiner.on('&').withKeyValueSeparator('=').join(request.query));
        }

        Joiner.MapJoiner headerJoiner = Joiner.on("\n").withKeyValueSeparator(':');
        Buffer buffer = buffer(headerJoiner
                // TreeMap guarantees predictable order for header serialization via toBodyPart(...) required for test
                // assertion
                .join(new TreeMap<>(Map.of(CONTENT_TYPE, "application/http", CONTENT_TRANSFER_ENCODING, "binary"))))
                        .appendString("\n\n")
                        .appendString(
                                Joiner.on(' ').join(request.method.name(), uri,
                                        HttpVersion.HTTP_1_1.alpnName().toUpperCase(
                                                Locale.getDefault())));
        if (!request.headers.isEmpty()) {
            buffer.appendString("\n").appendString(headerJoiner.join(request.headers));
        }
        return buffer.appendString("\n\n")
                .appendBuffer(Optional.ofNullable(request.body).orElse(EMPTY));
    }

    /**
     * Adds individual OData requests for batch processing.
     *
     * @param requests single requests to dispatch with the batch request
     * @return An {@link ODataBatchRequest} for OData batch processing
     */
    public ODataBatchRequest addRequests(ODataRequest... requests) {
        Objects.requireNonNull(requests, "requests must not be null!");
        if (requests.length < 1) {
            throw new IllegalArgumentException("requests must not be empty!");
        }

        this.requests.addAll(Arrays.asList(requests));
        return this;
    }

    @Override
    public Future<HttpResponse<Buffer>> send(NeonBee neonBee) {
        this.body = createBody();
        // set mandatory multipart header
        headers.set(CONTENT_TYPE, "multipart/mixed; boundary=" + boundary);
        return super.send(neonBee);
    }
}
