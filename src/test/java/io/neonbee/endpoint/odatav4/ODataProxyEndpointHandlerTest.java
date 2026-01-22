package io.neonbee.endpoint.odatav4;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.test.helper.ResourceHelper.TEST_RESOURCES;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import org.apache.olingo.commons.api.edm.Edm;
import org.apache.olingo.commons.api.edm.EdmEntityContainer;
import org.apache.olingo.commons.api.http.HttpHeader;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.server.api.ODataRequest;
import org.apache.olingo.server.api.ODataResponse;
import org.apache.olingo.server.api.ServiceMetadata;
import org.apache.olingo.server.api.deserializer.batch.BatchRequestPart;
import org.apache.olingo.server.api.deserializer.batch.ODataResponsePart;
import org.apache.olingo.server.core.MetadataParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import io.neonbee.data.DataAction;
import io.neonbee.data.DataContext;
import io.neonbee.data.DataQuery;
import io.neonbee.data.internal.DataContextImpl;
import io.neonbee.entity.AbstractEntityVerticle;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.net.HostAndPort;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.impl.RequestBodyImpl;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class ODataProxyEndpointHandlerTest {

    @Test
    void handleCopiesResponseDataIntoHttpResponse() {
        // mocks
        RoutingContext routingContext = mock(RoutingContext.class);
        HttpServerRequest request = mock(HttpServerRequest.class);
        HttpServerResponse response = mock(HttpServerResponse.class);

        when(routingContext.request()).thenReturn(request);
        when(routingContext.response()).thenReturn(response);
        when(request.method()).thenReturn(HttpMethod.GET);
        when(request.path()).thenReturn("/odataproxy");
        when(routingContext.vertx()).thenReturn(Vertx.vertx());

        // mock ServiceMetadata and nested edm container namespace
        ServiceMetadata serviceMetadata = mock(ServiceMetadata.class);
        Edm edm = mock(Edm.class);
        EdmEntityContainer container = mock(EdmEntityContainer.class);
        when(serviceMetadata.getEdm()).thenReturn(edm);
        when(edm.getEntityContainer()).thenReturn(container);
        when(container.getNamespace()).thenReturn("example");

        // prepare a simple ODataRequest to be returned by mapToODataRequest
        ODataRequest odataRequest = new ODataRequest();
        odataRequest.setRawServiceResolutionUri("example");
        odataRequest.setRawODataPath("/Birds");
        odataRequest.setRawQueryPath("");
        odataRequest.setMethod(org.apache.olingo.commons.api.http.HttpMethod.GET);

        // mock static OlingoEndpointHandler.mapToODataRequest
        try (MockedStatic<io.neonbee.endpoint.odatav4.internal.olingo.OlingoEndpointHandler> olingoMock =
                Mockito.mockStatic(io.neonbee.endpoint.odatav4.internal.olingo.OlingoEndpointHandler.class)) {
            olingoMock.when(() -> io.neonbee.endpoint.odatav4.internal.olingo.OlingoEndpointHandler
                    .mapToODataRequest(any(RoutingContext.class), any(String.class))).thenReturn(odataRequest);

            // mock static AbstractEntityVerticle.requestEntity to populate the passed context.responseData()
            try (MockedStatic<AbstractEntityVerticle> aevMock = Mockito.mockStatic(AbstractEntityVerticle.class)) {
                aevMock.when(() -> AbstractEntityVerticle.requestEntity(eq(Buffer.class), any(Vertx.class), any(),
                        any(DataContext.class)))
                        .thenAnswer(invocation -> {
                            // the 4th arg is the DataContext
                            DataContext ctx = invocation.getArgument(3);
                            ctx.mergeResponseData(Map.of(
                                    "X-Custom", List.of("a", "b"),
                                    DataContext.STATUS_CODE_HINT, 202,
                                    "Single", "value"));
                            return Future.succeededFuture(Buffer.buffer("the-body"));
                        });

                ODataProxyEndpointHandler handler = new ODataProxyEndpointHandler(
                        serviceMetadata,
                        ODataV4Endpoint.UriConversion.STRICT);
                handler.handle(routingContext);

                // verify that status code and headers were set and response was ended
                verify(response).setStatusCode(202);
                verify(response).putHeader("X-Custom", "a");
                verify(response).putHeader("X-Custom", "b");
                verify(response).putHeader("Single", "value");
                // end with buffer
                verify(response).end(any(Buffer.class));
            }
        }
    }

    @Test
    void handleMetadataRequest(Vertx vertx, VertxTestContext vertxTestContext) throws Exception {
        // mocks
        RoutingContext routingContext = mock(RoutingContext.class);
        HttpServerRequest request = mock(HttpServerRequest.class);
        HttpServerResponse response = mock(HttpServerResponse.class);

        when(routingContext.request()).thenReturn(request);
        when(routingContext.response()).thenReturn(response);
        when(routingContext.mountPoint()).thenReturn("/odataproxy");
        when(routingContext.body()).thenReturn(new RequestBodyImpl(routingContext));

        // The path must use the same service namespace as defined in Service.edmx.
        // Service.edmx declares an EntityContainer named 'Service' in namespace 'service'.
        // The ODataProxyEndpointHandler will call mapToODataRequest with that namespace,
        // so we use '/odataproxy/service/$metadata' here.
        when(request.method()).thenReturn(HttpMethod.GET);
        when(request.path()).thenReturn("/odataproxy/$metadata");
        when(request.headers()).thenReturn(MultiMap.caseInsensitiveMultiMap());
        when(request.scheme()).thenReturn("http");
        when(request.authority()).thenReturn(HostAndPort.create("localhost", 8080));

        when(routingContext.vertx()).thenReturn(vertx);

        MetadataParser parser = new MetadataParser();
        Path serviceEdmxPath = TEST_RESOURCES.resolveRelated("Service.edmx");
        ServiceMetadata serviceMetadata;
        try (BufferedReader reader = Files.newBufferedReader(serviceEdmxPath, StandardCharsets.UTF_8)) {
            serviceMetadata = parser.buildServiceMetadata(reader);
        }

        ODataProxyEndpointHandler handler = new ODataProxyEndpointHandler(
                serviceMetadata,
                ODataV4Endpoint.UriConversion.STRICT);

        handler.handleMetadataRequest(routingContext)
                .onSuccess(odr -> vertxTestContext.verify(() -> {
                    // verify that status code and headers were set and response was ended
                    verify(response).setStatusCode(200);
                    verify(response).putHeader("OData-Version", "4.0");
                    verify(response).putHeader("Content-Type", "application/xml");
                    verify(response).end(any(Buffer.class));
                    vertxTestContext.completeNow();
                }))
                .onFailure(vertxTestContext::failNow);
    }

    @Test
    void handleFailsWhenMappingODataRequestThrows() {
        RoutingContext routingContext = mock(RoutingContext.class);
        HttpServerRequest request = mock(HttpServerRequest.class);
        when(routingContext.request()).thenReturn(request);
        when(request.method()).thenReturn(HttpMethod.GET);
        when(request.path()).thenReturn("/odataproxy");

        ServiceMetadata serviceMetadata = mock(ServiceMetadata.class);
        Edm edm = mock(Edm.class);
        EdmEntityContainer container = mock(EdmEntityContainer.class);
        when(serviceMetadata.getEdm()).thenReturn(edm);
        when(edm.getEntityContainer()).thenReturn(container);
        when(container.getNamespace()).thenReturn("example");

        try (MockedStatic<io.neonbee.endpoint.odatav4.internal.olingo.OlingoEndpointHandler> olingoMock =
                Mockito.mockStatic(io.neonbee.endpoint.odatav4.internal.olingo.OlingoEndpointHandler.class)) {
            // make mapToODataRequest throw
            olingoMock.when(() -> io.neonbee.endpoint.odatav4.internal.olingo.OlingoEndpointHandler
                    .mapToODataRequest(any(RoutingContext.class), any(String.class)))
                    .thenThrow(new RuntimeException("boom"));

            // also mock getStatusCode to return a specific code (e.g. 400)
            olingoMock
                    .when(() -> io.neonbee.endpoint.odatav4.internal.olingo.OlingoEndpointHandler
                            .getStatusCode(any(Throwable.class)))
                    .thenReturn(400);

            ODataProxyEndpointHandler handler = new ODataProxyEndpointHandler(
                    serviceMetadata,
                    ODataV4Endpoint.UriConversion.STRICT);
            handler.handle(routingContext);

            // verify that routingContext.fail was invoked with code 400
            verify(routingContext).fail(eq(400), any(Throwable.class));
        }
    }

    @Test
    void setHeaderValues() {
        // prepare response data with deterministic iteration order
        Map<String, Object> respData = new LinkedHashMap<>();
        ArrayList<String> value = new ArrayList<>();
        value.add("a");
        value.add("b");
        value.add(null);
        respData.put("X-Custom", value);
        respData.put("Single", "value");
        respData.put("NullVal", null);
        respData.put(DataContext.STATUS_CODE_HINT, 202); // should be ignored

        // capture calls to the BiConsumer
        List<Map.Entry<String, String>> captured = new ArrayList<>();
        BiConsumer<String, String> capturer = (k, v) -> captured.add(new AbstractMap.SimpleEntry<>(k, v));

        // invoke
        ODataProxyEndpointHandler.setHeaderValues(respData, capturer);

        assertThat(captured).hasSize(5);

        assertThat(captured.get(0).getKey()).isEqualTo("X-Custom");
        assertThat(captured.get(0).getValue()).isEqualTo("a");

        assertThat(captured.get(1).getKey()).isEqualTo("X-Custom");
        assertThat(captured.get(1).getValue()).isEqualTo("b");

        assertThat(captured.get(2).getKey()).isEqualTo("X-Custom");
        assertThat(captured.get(2).getValue()).isNull();

        assertThat(captured.get(3).getKey()).isEqualTo("Single");
        assertThat(captured.get(3).getValue()).isEqualTo("value");

        assertThat(captured.get(4).getKey()).isEqualTo("NullVal");
        assertThat(captured.get(4).getValue()).isNull();
    }

    @Test
    void getFullQualifiedName() {
        ODataRequest req = new ODataRequest();
        req.setRawServiceResolutionUri("example");
        req.setRawODataPath("/Birds");
        // success
        var fqn = ODataProxyEndpointHandler.getFullQualifiedName(req);
        assertThat(fqn.getNamespace()).isEqualTo("example");
        assertThat(fqn.getName()).isEqualTo("Birds");

        // failure scenario: path that doesn't match the expected pattern
        ODataRequest bad = new ODataRequest();
        bad.setRawServiceResolutionUri("example");
        bad.setRawODataPath("/");
        try {
            ODataProxyEndpointHandler.getFullQualifiedName(bad);
            throw new AssertionError("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertThat(e).hasMessageThat().contains("Cannot determine entity name");
        }
    }

    @Test
    void odataRequestToQuery() throws Exception {
        ODataRequest oDataRequest = new ODataRequest();
        oDataRequest.setRawServiceResolutionUri("ns");
        oDataRequest.setRawODataPath("/Entities");
        oDataRequest.setRawQueryPath("a=1&b=two");
        oDataRequest.setMethod(org.apache.olingo.commons.api.http.HttpMethod.POST);
        oDataRequest.setHeader("Accept", "application/json");

        Buffer body = Buffer.buffer("payload");
        DataQuery dataQuery = ODataProxyEndpointHandler.odataRequestToQuery(oDataRequest, DataAction.CREATE, body);

        assertThat(dataQuery.getAction()).isEqualTo(DataAction.CREATE);
        assertThat(dataQuery.getParameters()).containsKey("a");
        assertThat(dataQuery.getParameters().get("a")).contains("1");
        assertThat(dataQuery.getParameters()).containsKey("b");
        assertThat(dataQuery.getParameters().get("b")).contains("two");

        assertThat(dataQuery.getHeader("Accept")).isEqualTo("application/json");
        assertThat(dataQuery.getHeader("X-HTTP-Method")).isEqualTo("POST");

        assertThat(dataQuery.getUriPath()).isEqualTo("/ns/Entities");
        assertThat(dataQuery.getBody()).isEqualTo(Buffer.buffer("payload"));
    }

    @Test
    void createODataResponse() throws Exception {
        DataContextImpl ctx = new DataContextImpl();
        ctx.mergeResponseData(Map.of(
                "X-Test", List.of("a", "b"),
                DataContext.STATUS_CODE_HINT, 201));
        Buffer buf = Buffer.buffer("the-body");

        var resp = ODataProxyEndpointHandler.createODataResponse(ctx, buf);

        // status code
        assertThat(resp.getStatusCode()).isEqualTo(201);

        // headers map
        var headers = resp.getAllHeaders();
        assertThat(headers.get("X-Test")).containsExactly("a", "b");

        // content bytes
        InputStream content = resp.getContent();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        content.transferTo(baos);
        assertThat(baos.toString(StandardCharsets.UTF_8)).isEqualTo("the-body");
    }

    @Test
    void getStatusCode() {
        // map-based status code
        Integer code = ODataProxyEndpointHandler.getStatusCode(Map.of(DataContext.STATUS_CODE_HINT, 123));
        assertThat(code).isEqualTo(123);

        Integer absent = ODataProxyEndpointHandler.getStatusCode(Map.of());
        assertThat(absent).isEqualTo(-1);

        Integer defaultValue = ODataProxyEndpointHandler.getStatusCode(Map.of(), 404);
        assertThat(defaultValue).isEqualTo(404);

        Integer hintCode = ODataProxyEndpointHandler.getStatusCode(Map.of(DataContext.STATUS_CODE_HINT, 123), 404);
        assertThat(hintCode).isEqualTo(123);

        // throwable-based status code delegates to OlingoEndpointHandler
        try (MockedStatic<io.neonbee.endpoint.odatav4.internal.olingo.OlingoEndpointHandler> olingoMock =
                Mockito.mockStatic(io.neonbee.endpoint.odatav4.internal.olingo.OlingoEndpointHandler.class)) {
            olingoMock
                    .when(() -> io.neonbee.endpoint.odatav4.internal.olingo.OlingoEndpointHandler
                            .getStatusCode(any(Throwable.class)))
                    .thenReturn(400);
            int sc = ODataProxyEndpointHandler.getStatusCode(new RuntimeException("boom"));
            assertThat(sc).isEqualTo(400);
        }
    }

    @Test
    void createBatchResponsePartChangeSetReturnsChangeSetPartWhenSuccessful() {
        RoutingContext routingContext = mock(RoutingContext.class);
        DataContext context = new DataContextImpl();
        ODataRequest request1 = new ODataRequest();
        ODataRequest request2 = new ODataRequest();
        BatchRequestPart batchRequestPart = new BatchRequestPart(true, List.of(request1, request2));

        ODataResponse response1 = new ODataResponse();
        response1.setStatusCode(HttpStatusCode.CREATED.getStatusCode());
        ODataResponse response2 = new ODataResponse();
        response2.setStatusCode(HttpStatusCode.OK.getStatusCode());

        try (MockedStatic<ODataProxyEndpointHandler> handlerMock =
                Mockito.mockStatic(ODataProxyEndpointHandler.class, Mockito.CALLS_REAL_METHODS)) {
            handlerMock.when(() -> ODataProxyEndpointHandler.getODataResponse(eq(routingContext), eq(context),
                    eq(request1))).thenReturn(Future.succeededFuture(response1));
            handlerMock.when(() -> ODataProxyEndpointHandler.getODataResponse(eq(routingContext), eq(context),
                    eq(request2))).thenReturn(Future.succeededFuture(response2));

            ODataResponsePart responsePart =
                    ODataProxyEndpointHandler.createBatchResponsePart(routingContext, context, batchRequestPart)
                            .result();

            assertThat(responsePart.isChangeSet()).isTrue();
            assertThat(responsePart.getResponses()).containsExactly(response1, response2).inOrder();
        }
    }

    @Test
    void createBatchResponsePartChangeSetReturnsSingleErrorResponse() {
        RoutingContext routingContext = mock(RoutingContext.class);
        DataContext context = new DataContextImpl();
        ODataRequest request1 = new ODataRequest();
        ODataRequest request2 = new ODataRequest();
        BatchRequestPart batchRequestPart = new BatchRequestPart(true, List.of(request1, request2));

        ODataResponse response1 = new ODataResponse();
        response1.setStatusCode(HttpStatusCode.CREATED.getStatusCode());
        ODataResponse response2 = new ODataResponse();
        response2.setStatusCode(HttpStatusCode.BAD_REQUEST.getStatusCode());

        try (MockedStatic<ODataProxyEndpointHandler> handlerMock =
                Mockito.mockStatic(ODataProxyEndpointHandler.class, Mockito.CALLS_REAL_METHODS)) {
            handlerMock.when(() -> ODataProxyEndpointHandler.getODataResponse(eq(routingContext), eq(context),
                    eq(request1))).thenReturn(Future.succeededFuture(response1));
            handlerMock.when(() -> ODataProxyEndpointHandler.getODataResponse(eq(routingContext), eq(context),
                    eq(request2))).thenReturn(Future.succeededFuture(response2));

            ODataResponsePart responsePart =
                    ODataProxyEndpointHandler.createBatchResponsePart(routingContext, context, batchRequestPart)
                            .result();

            assertThat(responsePart.isChangeSet()).isFalse();
            assertThat(responsePart.getResponses()).containsExactly(response2);
        }
    }

    @Test
    void applyContentIdAddsHeaderWhenMissing() {
        ODataResponse response = new ODataResponse();
        String contentId = "content-1";

        ODataResponse updated = ODataProxyEndpointHandler.applyContentId(response, contentId);

        assertThat(updated.getHeader(HttpHeader.CONTENT_ID)).isEqualTo(contentId);
    }

    @Test
    void applyContentIdDoesNotOverrideExistingHeader() {
        ODataResponse response = new ODataResponse();
        response.addHeader(HttpHeader.CONTENT_ID, "existing");

        ODataResponse updated = ODataProxyEndpointHandler.applyContentId(response, "new-id");

        assertThat(updated.getHeader(HttpHeader.CONTENT_ID)).isEqualTo("existing");
    }
}
