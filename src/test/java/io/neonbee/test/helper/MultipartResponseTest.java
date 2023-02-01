package io.neonbee.test.helper;

import static com.google.common.collect.Iterables.get;
import static com.google.common.truth.Truth.assertThat;
import static io.vertx.core.MultiMap.caseInsensitiveMultiMap;
import static io.vertx.core.buffer.Buffer.buffer;
import static java.util.Collections.emptyList;
import static java.util.Optional.ofNullable;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;

public class MultipartResponseTest {
    static final String BOUNDARY = "b_243234_25424_ef_892u748";

    static final String RESPONSE_PART_1 =
            "Content-Type: application/http\n" + "\n" + "HTTP/1.1 200 Ok\n" + "Content-Type: application/json\n"
                    + "Content-Length: 19\n" + "\n{\"state\":\"success\"}\n";

    static final String RESPONSE_PART_2 =
            "Content-Type: application/http\n" + "\n" + "HTTP/1.1 404 Not Found\n" + "Content-Type: application/xml\n"
                    + "Content-Length: 68\n" + "\n<error><code>500</code><message>Some error happend</message></error>";

    static final MultipartResponse.Part RESPONSE_PART_1_EXPECTED =
            new MultipartResponse.Part(200, caseInsensitiveMultiMap().add("Content-Type", "application/http"),
                    caseInsensitiveMultiMap()
                            .addAll(Map.of("Content-Type", "application/json", "Content-Length", "19")),
                    Buffer.buffer("{\"state\":\"success\"}\n"));

    static final MultipartResponse.Part RESPONSE_PART_2_EXPECTED =
            new MultipartResponse.Part(404, caseInsensitiveMultiMap().add("Content-Type", "application/http"),
                    caseInsensitiveMultiMap().addAll(Map.of("Content-Type", "application/xml", "Content-Length", "68")),
                    Buffer.buffer("<error><code>500</code><message>Some error happend</message></error>"));

    private HttpResponse<Buffer> response;

    static Stream<Arguments> withParts() {
        return Stream.of(arguments(RESPONSE_PART_1, RESPONSE_PART_1_EXPECTED),
                arguments(RESPONSE_PART_2, RESPONSE_PART_2_EXPECTED));
    }

    List<Map.Entry<String, String>> getJavaMapEntries(MultiMap multiMap) {
        return multiMap.entries().stream().map(entry -> Map.entry(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }

    /**
     * Asserts equality of two maps by verifying equality of maps' entries.
     * {@link com.google.common.truth.ComparableSubject#isEqualTo(Object)} can not be used as it uses the maps' hashCode
     * which is currently implemented to create different hashCodes for maps with equal entries.
     *
     * @param actual   the actual map
     * @param expected the expected map
     */
    void assertContainsExactlyElementsIn(MultiMap actual, MultiMap expected) {
        List<Map.Entry<String, String>> actualEntries =
                ofNullable(actual).map(this::getJavaMapEntries).orElse(emptyList());
        List<Map.Entry<String, String>> expectedEntries =
                ofNullable(expected).map(this::getJavaMapEntries).orElse(emptyList());
        assertThat(actualEntries).containsExactlyElementsIn(expectedEntries);
    }

    void assertEquality(MultipartResponse.Part actual, MultipartResponse.Part expected) {
        assertThat(actual.getStatusCode()).isEqualTo(expected.getStatusCode());
        assertContainsExactlyElementsIn(actual.getPartHeaders(), expected.getPartHeaders());
        assertContainsExactlyElementsIn(actual.getResponseHeaders(), expected.getResponseHeaders());
        assertThat(ofNullable(actual.getPayload()).map(Buffer::getBytes).orElse(null)).isEqualTo(
                ofNullable(expected.getPayload()).map(Buffer::getBytes).orElse(null));
    }

    @BeforeEach
    void setUp() {
        response = mock(HttpResponse.class);
        Buffer body =
                buffer("--" + BOUNDARY + "\n").appendString(RESPONSE_PART_1).appendString("\n--" + BOUNDARY + "\n")
                        .appendString(RESPONSE_PART_2).appendString("\n--" + BOUNDARY + "--");
        doReturn(body).when(response).body();
        doReturn(200).when(response).statusCode();
        doReturn(caseInsensitiveMultiMap().addAll(
                Map.of("OData-Version", "4.0", "Content-Length", "" + body.length(), "Content-Type",
                        "multipart/mixed; boundary=" + BOUNDARY))).when(response).headers();
    }

    @ParameterizedTest
    @MethodSource("withParts")
    @DisplayName("Test parsing a multipart request body part")
    void testParseAsPart(String value, MultipartResponse.Part expected) {
        MultipartResponse.Part actual = MultipartResponse.parseAsPart(value);
        assertEquality(actual, expected);
    }

    @Test
    @DisplayName("Test creating MultipartResponse from HttpResponse")
    void testOfHttpResponse() {
        MultipartResponse multipartResponse = MultipartResponse.of(response);
        assertThat(multipartResponse.getBoundary()).isEqualTo(BOUNDARY);
        assertThat(multipartResponse.getParts()).hasSize(2);
        assertEquality(get(multipartResponse.getParts(), 0), RESPONSE_PART_1_EXPECTED);
        assertEquality(get(multipartResponse.getParts(), 1), RESPONSE_PART_2_EXPECTED);
    }
}
