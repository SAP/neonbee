package io.neonbee.test.helper;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.test.helper.ResourceHelper.TEST_RESOURCES;
import static io.vertx.core.MultiMap.caseInsensitiveMultiMap;
import static io.vertx.core.buffer.Buffer.buffer;
import static java.util.Optional.ofNullable;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.google.common.truth.Correspondence;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.client.HttpResponse;

class MultipartResponseTest {
    static final String BOUNDARY = "b_243234_25424_ef_892u748";

    static final String RESPONSE_PART_1 =
            "Content-Type: application/http\r\n" + "\r\n" + "HTTP/1.1 200 Ok\r\n" + "Content-Type: application/json\r\n"
                    + "Content-Length: 19\r\n" + "\r\n{\"state\":\"success\"}\r\n";

    static final String RESPONSE_PART_2 =
            "Content-Type: application/http\r\n" + "\r\n" + "HTTP/1.1 404 Not Found\r\n"
                    + "Content-Type: application/xml\r\n"
                    + "Content-Length: 68\r\n"
                    + "\r\n<error><code>500</code><message>\r\n\r\nSome error happened\r\n\r\n</message></error>";

    static final MultipartResponse.Part RESPONSE_PART_1_EXPECTED =
            new MultipartResponse.Part(200, caseInsensitiveMultiMap()
                    .addAll(Map.of("Content-Type", "application/json", "Content-Length", "19")),
                    buffer("{\"state\":\"success\"}\r\n"));

    static final MultipartResponse.Part RESPONSE_PART_2_EXPECTED =
            new MultipartResponse.Part(404,
                    caseInsensitiveMultiMap().addAll(Map.of("Content-Type", "application/xml", "Content-Length", "68")),
                    buffer("<error><code>500</code><message>\r\n\r\nSome error happened\r\n\r\n</message></error>"));

    private HttpResponse<Buffer> response;

    static Stream<Arguments> withParts() {
        return Stream.of(arguments(RESPONSE_PART_1, RESPONSE_PART_1_EXPECTED),
                arguments(RESPONSE_PART_2, RESPONSE_PART_2_EXPECTED));
    }

    static Stream<Arguments> withValidContentTypeHeaders() {
        return Stream.of(arguments("multipart/mixed; boundary=" + BOUNDARY, BOUNDARY),
                arguments("multipart/mixed;boundary=" + BOUNDARY, BOUNDARY),
                arguments("multipart/mixed;boundary=ABC=123", "ABC=123"));
    }

    static Stream<Arguments> withInvalidContentTypeHeaders() {
        return Stream.of(arguments(null, "Content-Type header missing"),
                arguments("multipart/mixed", "Boundary definition missing"),
                arguments("multipart/mixed; boundary: " + BOUNDARY, "Invalid boundary value delimiter"));
    }

    void assertEquality(MultipartResponse.Part actual, MultipartResponse.Part expected) {
        assertThat(actual.getStatusCode()).isEqualTo(expected.getStatusCode());
        assertThat(actual.getHeaders().entries()).comparingElementsUsing(
                Correspondence.from((ae, ee) -> ae.toString().equals(ee.toString()), "string compares to"))
                .containsExactlyElementsIn(expected.getHeaders().entries());
        assertThat(ofNullable(actual.getBody()).map(Buffer::toString).orElse(null)).isEqualTo(
                ofNullable(expected.getBody()).map(Buffer::toString).orElse(null));
    }

    @BeforeEach
    void setUp() throws IOException {
        response = mock(HttpResponse.class);
        Buffer body = TEST_RESOURCES.getRelated("multipart-response-body-CRLF.txt");
        doReturn(body).when(response).body();
        doReturn(200).when(response).statusCode();
        doReturn("multipart/mixed; boundary=" + BOUNDARY).when(response).getHeader(HttpHeaders.CONTENT_TYPE.toString());
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("withParts")
    @DisplayName("parsing a multipart request body part from string")
    void testParseAsPart(String value, MultipartResponse.Part expected) {
        MultipartResponse.Part actual = MultipartResponse.parseAsPart(value);
        assertEquality(actual, expected);
    }

    @Test
    @DisplayName("create MultipartResponse from HttpResponse")
    void testOfHttpResponse() {
        MultipartResponse multipartResponse = MultipartResponse.of(response);
        assertThat(multipartResponse.getParts()).hasSize(2);
        assertEquality(multipartResponse.getParts().get(0), RESPONSE_PART_1_EXPECTED);
        assertEquality(multipartResponse.getParts().get(1), RESPONSE_PART_2_EXPECTED);
    }

    @ParameterizedTest(name = "{index}: with content type header ''{0}'' expecting ''{1}''")
    @MethodSource("withValidContentTypeHeaders")
    @DisplayName("extract boundary from header")
    void testExtractBoundary(String contentTypeHeader, String expected) {
        doReturn(contentTypeHeader).when(response).getHeader(HttpHeaders.CONTENT_TYPE.toString());
        String actual = MultipartResponse.extractBoundary(response);
        assertThat(actual).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{index}: {1} with Content-Type header value ''{0}''")
    @MethodSource("withInvalidContentTypeHeaders")
    @DisplayName("invalid Content-Type header must result in IllegalArgumentException")
    void testExtractBoundaryWithInvalidContentTypes(String contentType, String testCase) {
        doReturn(contentType).when(response).getHeader(HttpHeaders.CONTENT_TYPE.toString());
        assertThrows(IllegalArgumentException.class, () -> MultipartResponse.extractBoundary(response));
    }

    @Test
    @DisplayName("get originating HTTP response")
    void testGetHttpResponse() {
        MultipartResponse multipartResponse = MultipartResponse.of(response);
        assertThat(multipartResponse.getHttpResponse()).isEqualTo(response);
    }

    @Test
    @DisplayName("test getting part header by name")
    void testPartGetHeader() {
        assertThat(RESPONSE_PART_1_EXPECTED.getHeader("Content-Type")).isEqualTo("application/json");
    }
}
