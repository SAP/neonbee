package io.neonbee.test.base;

import io.vertx.core.buffer.Buffer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;

import static java.lang.String.format;
import static java.lang.String.join;

/**
 * Simple parser implementation for <a href="http://docs.oasis-open.org/odata/odata/v4.01/odata-v4.01-part1-protocol.html#sec_MultipartBatchResponse">OData multipart batch response</a>
 * payloads. Parts are split and extracted from the response payload following a batch request.
 */
public class ODataBatchResponseParser {

    private enum Step {Boundary, BoundaryHeaders, Status, RequestHeaders, Payload}

    private final static String BOUNDARY_IDENTIFIER = "--";
    private final static char HEADER_DELIMITER = ':';

    private final Buffer content;
    private ArrayDeque<ODataBatchResponsePart> responseParts;
    private ArrayDeque<String> openBoundaries;
    private Map<String, String> headers;
    private Buffer payload = null;
    private Step step;
    private int lineIndex;

    public ODataBatchResponseParser(Buffer content) {
        this.content = content;
    }

    public Collection<ODataBatchResponsePart> parse() throws IOException {
        if (responseParts != null) {
            throw new IllegalStateException("Content has already been parsed and must processed only once!");
        }

        try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(content.getBytes())) {
            responseParts = new ArrayDeque<>();
            openBoundaries = new ArrayDeque<>();

            Scanner scanner = new Scanner(byteArrayInputStream, Charset.defaultCharset());
            lineIndex = -1;
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                lineIndex += 1;

                if (line.startsWith(BOUNDARY_IDENTIFIER)) {
                    if (Step.Payload.equals(step) && payload != null) {
                        ODataBatchResponsePart part = responseParts.getLast();
                        part.setPayload(payload);
                    }

                    step = Step.Boundary;
                    payload = null;
                    headers = new HashMap<>();

                    consumeBoundary(line);
                    if (openBoundaries.isEmpty()) {
                        break;
                    }

                    step = Step.BoundaryHeaders;
                    continue;
                }

                if (line.isBlank()) {
                    if (step.equals(Step.RequestHeaders)) {
                        ODataBatchResponsePart part = responseParts.getLast();
                        part.setHeaders(headers);
                        this.step = Step.Payload;
                    } else if (step.equals(Step.BoundaryHeaders)) {
                        headers = new HashMap<>();
                        this.step = Step.Status;
                    }
                    continue;
                }

                if (step.equals(Step.BoundaryHeaders) || step.equals(Step.RequestHeaders)) {
                    consumeHeader(line);
                } else if (step.equals(Step.Status)) {
                    consumeStatusLine(line);
                    step = Step.RequestHeaders;
                } else {
                    processPayload(line);
                }
            }

            verifyAfterParse();
            return responseParts;
        }
    }

    private void consumeBoundary(String line) {
        String boundary = line.substring(BOUNDARY_IDENTIFIER.length());
        String openBoundary = openBoundaries.isEmpty() ? null : openBoundaries.getLast();
        if (line.endsWith(BOUNDARY_IDENTIFIER)) {
            boundary = boundary.substring(0, boundary.length() - BOUNDARY_IDENTIFIER.length());
            if (!Objects.equals(boundary, openBoundary)) {
                throw new IllegalStateException(format("Boundary %s was not closed properly!", openBoundary));
            }
            openBoundaries.removeLast();
            return;
        }

        if (!boundary.equals(openBoundary)) {
            openBoundaries.addLast(boundary);
        }
        headers = new HashMap<>();

        if (payload != null) {
            responseParts.getLast().setPayload(payload);
        }
        payload = null;

        ODataBatchResponsePart part = new ODataBatchResponsePart();
        responseParts.add(part);
    }

    private void consumeHeader(String line) {
        int indexOfDelim = line.indexOf(HEADER_DELIMITER);
        if (indexOfDelim <= 0) {
            throw new IllegalStateException(format("Found invalid header format in line %s: %s", lineIndex, line));
        }

        String key = line.substring(0, indexOfDelim);
        String value = line.substring(indexOfDelim + 1).replaceAll("^ +", "");
        headers.put(key, value);
    }

    private void consumeStatusLine(String line) {
        String[] segments = line.split(" ", 3);
        if (segments.length != 3) {
            throw new IllegalStateException(format("Invalid status line! \"%s\" provides %s segments where exactly 3 are required!", line, segments.length));
        }

        try {
            int statusCode = Integer.parseInt(segments[1]);
            ODataBatchResponsePart part = responseParts.getLast();
            part.setStatusCode(statusCode);
        } catch (NumberFormatException ex) {
            throw new RuntimeException(format("Found invalid format for response status code: %s (Line %s)", segments[1], lineIndex));
        }
    }

    private void processPayload(String line) {
        if (payload == null) {
            payload = Buffer.buffer();
        }
        payload = payload.appendString(line);
    }

    private void verifyAfterParse() {
        if (!openBoundaries.isEmpty()) {
            throw new IllegalStateException(format("Found boundaries %s not closed properly!", join(", ", openBoundaries)));
        }
    }
}
