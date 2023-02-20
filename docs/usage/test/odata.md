# Testing OData services

## APIs supporting OData testing

The following classes provide you a simple API so you can focus on writing your test cases without requiring deep
knowledge of the [OData protocol 4.0](http://docs.oasis-open.org/odata/odata/v4.0/odata-v4.0-part1-protocol.html).

> Hint: Current implementations follow OData protocol version 4.0

| Class                                                                                                                    | Description                                                                                                               |
|--------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------|
| [io.neonbee.test.base.ODataMetadataRequest](../../../src/test/java/io/neonbee/test/base/ODataMetadataRequest.java)       | Allows to create and dispatch an individual OData request for performing metadata requests on OData services.             |
| [io.neonbee.test.base.ODataRequest](../../../src/test/java/io/neonbee/test/base/ODataRequest.java)                       | Allows to create and dispatch an individual OData request for performing data requests on OData services.                 |
| [io.neonbee.test.base.ODataBatchRequest](../../../src/test/java/io/neonbee/test/base/ODataBatchRequest.java)             | Allows to create and dispatch an OData batch request constituting from one or more individual OData requests.             |
| [io.neonbee.test.helper.MultipartResponse](../../../src/test/java/io/neonbee/test/helper/MultipartResponse.java)         | Provides parsing and access to details of an HTTP multipart response as it is retrieved as a result OData batch requests. |
| [io.neonbee.test.helper.ODataResponseVerifier](../../../src/test/java/io/neonbee/test/helper/ODataResponseVerifier.java) | Provides common assertions for OData related test cases like response status code verification.                           |

## OData batch request example

This example explains the test case from [io.neonbee.test.endpoint.odata.ODataBatchTest#testMultipleParts](../../../src/test/java/io/neonbee/test/endpoint/odata/ODataBatchTest.java)
in more detail to provide an introduction on OData related testing and relevant classes.

```java
@Test
@DisplayName("Test batch request containing more than one part")
void testMultipleParts(VertxTestContext testContext) {
    /** Phase 'Initialize' **/
    String key = "id-1";
    ODataRequest filterRequest = new ODataRequest(TestService1EntityVerticle.TEST_ENTITY_SET_FQN)
    .setQuery(Map.of("$filter", "KeyPropertyString in ('" + key + "')"));
    ODataRequest readRequest = new ODataRequest(TestService1EntityVerticle.TEST_ENTITY_SET_FQN).setKey(key);
    ODataBatchRequest batchRequest = new ODataBatchRequest(TestService1EntityVerticle.TEST_ENTITY_SET_FQN)
        .addRequests(filterRequest, readRequest);

    /** Phase 'Send' **/
    assertODataBatch(requestOData(batchRequest), multipartResponse -> {
        /** Phase 'Assert' **/
        // verify correct amount of response parts
        List<MultipartResponse.Part> parts = multipartResponse.getParts();
        assertThat(parts).hasSize(2);

        // verify status code of first response part correlating to 'filterRequest'
        assertThat(parts.get(0).getStatusCode()).isEqualTo(200);

        // [...]

        testContext.completeNow();
    }, testContext);
}
```

### Phase 1 'Initialize'

In the beginning the individual OData requests `filterRequest` and `readRequest` are created.</br>
Those requests target to search for an entity using filters on an entity's properties and reading an entity by its key field respectively.</br>
In the line following the individual requests are wrapped in an `ODataBatchRequest` to be dispatched as a single HTTP request.

### Phase 2 'Send'

The initialized `ODataBatchRequest` is dispatched via `requestOData(batchRequest)`.

### Phase 3 'Assert'

`assertODataBatch(Future<HttpResponse<Buffer>>, Consumer<MultipartResponse>, VertxTestContext)` of `io.neonbee.test.helper.ODataResponseVerifier` takes care of verifying

* the OData batch request was dispatched without exceptions and
* the corresponding HTTP response returns successfully (HTTP status code between 200 and 399)

In case of a successful HTTP response the response body is parsed via `io.neonbee.test.helper.MultipartResponse#of(HttpResponse<Buffer>)` and the result is passed to the assertion handler provided as the second argument to method `assertODataBatch`.

In this example the resulting `io.neonbee.test.helper.MultipartResponse` is referenced as `multipartResponse`.

Response details to the individual batch requests are accessible as parts of the `multipartResponse` via `io.neonbee.test.helper.MultipartResponse#getParts()`.
The part details cover HTTP response status code, headers and body for test-case-specific verification.

## More OData related examples

More OData related test cases are available within the [io.neonbee.test.endpoint.odata](../../../src/test/java/io/neonbee/test/endpoint/odata) package providing more examples.
