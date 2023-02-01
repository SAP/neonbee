package io.neonbee.test.base;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.NeonBeeMockHelper.defaultVertxMock;
import static io.neonbee.NeonBeeMockHelper.registerNeonBeeMock;
import static io.neonbee.test.base.ODataBatchRequest.toBodyPart;
import static io.vertx.core.buffer.Buffer.buffer;
import static io.vertx.core.http.HttpHeaders.CONTENT_TYPE;
import static io.vertx.core.http.HttpMethod.GET;
import static io.vertx.core.http.HttpMethod.POST;
import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.apache.olingo.commons.api.edm.FullQualifiedName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.vertx.core.buffer.Buffer;

class ODataBatchRequestTest {

    private static final FullQualifiedName FQN = new FullQualifiedName("my-namespace", "my-entity");

    private ODataRequest postRequest;

    private ODataRequest getRequest;

    private String boundary;

    private ODataBatchRequest batchRequest;

    @BeforeEach
    @DisplayName("Setup the base OData batch request")
    void setUp() {
        getRequest = new ODataRequest(FQN).setMethod(GET);
        postRequest = new ODataRequest(FQN).setMethod(POST)
                .addHeader("content-type", "text/plain")
                .setBody(buffer("This is my entity"));
        boundary = randomUUID().toString();
        batchRequest = new ODataBatchRequest(FQN, boundary);
    }

    @Test
    @DisplayName("ODataRequest to body part without payload")
    void testToBodyPartWithoutPayload() {
        String expected = "content-transfer-encoding:binary"
                + "\ncontent-type:application/http"
                + "\n"
                + "\nGET my-entity HTTP/1.1"
                + "\n"
                + "\n";

        Buffer buffer = toBodyPart(getRequest);
        assertThat(buffer.toString()).isEqualTo(expected);
    }

    @Test
    @DisplayName("ODataRequest to body part with payload")
    void testToBodyPartWithPayload() {
        String expected = "content-transfer-encoding:binary\n"
                + "content-type:application/http\n"
                + "\n"
                + "POST my-entity HTTP/1.1\n"
                + "content-type:text/plain\n"
                + "\n"
                + "This is my entity";

        Buffer buffer = toBodyPart(postRequest);
        assertThat(buffer.toString()).isEqualTo(expected);
    }

    @Test
    @DisplayName("ODataRequest to body part with query parameter")
    void testToBodyPartWithQuery() {
        String expected = "content-transfer-encoding:binary"
                + "\ncontent-type:application/http"
                + "\n"
                + "\nGET my-entity?count=true&filter=a:%20b HTTP/1.1"
                + "\naccept:application/json"
                + "\n"
                + "\n";

        Buffer buffer = toBodyPart(getRequest.addQueryParam("count", "true")
                .addQueryParam("filter", "a: b")
                .addHeader("accept", "application/json"));
        assertThat(buffer.toString()).isEqualTo(expected);
    }

    @Test
    @DisplayName("Batch request must have HTTP method 'POST'")
    void testMethod() {
        assertThat(batchRequest.method).isEqualTo(POST);
    }

    @Test
    @DisplayName("Batch request must provide mandatory Content-Type header")
    void testMandatoryHeader() {
        batchRequest.send(registerNeonBeeMock(defaultVertxMock()));
        assertThat(batchRequest.headers.get(CONTENT_TYPE)).isEqualTo("multipart/mixed; boundary=" + boundary);
    }

    @Test
    @DisplayName("Batch request body must comply to OData specification")
    void testCreateBody() {
        Buffer body = batchRequest.addRequests(getRequest, postRequest).createBody();

        String expected = "--" + boundary
                + "\ncontent-transfer-encoding:binary"
                + "\ncontent-type:application/http"
                + "\n"
                + "\nGET my-entity HTTP/1.1"
                + "\n\n"
                + "\n--" + boundary
                + "\ncontent-transfer-encoding:binary"
                + "\ncontent-type:application/http"
                + "\n"
                + "\nPOST my-entity HTTP/1.1"
                + "\ncontent-type:text/plain"
                + "\n"
                + "\nThis is my entity"
                + "\n--" + boundary + "--";

        assertThat(body.toString()).isEqualTo(expected);
    }

    @Test
    @DisplayName("Passing no arguments to addRequests must result in IllegalArgumentException")
    void testAddRequestsWithNoArguments() {
        assertThrows(IllegalArgumentException.class, () -> batchRequest.addRequests());
    }

    @Test
    @DisplayName("Passing null argument to addRequests must result in NullPointerException")
    void testAddRequestsPassingNull() {
        assertThrows(NullPointerException.class, () -> batchRequest.addRequests((ODataRequest[]) null));
    }

    @Test
    @DisplayName("Verify URI")
    void testGetUri() {
        String actual = batchRequest.getUri();
        assertThat(actual).isEqualTo("my-namespace/$batch");
    }

    @Test
    @DisplayName("Verify self() returns correct instance")
    void testGetSelf() {
        assertThat(batchRequest.self()).isEqualTo(batchRequest);
    }
}
