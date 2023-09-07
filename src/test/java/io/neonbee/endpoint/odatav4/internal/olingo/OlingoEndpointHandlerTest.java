package io.neonbee.endpoint.odatav4.internal.olingo;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.endpoint.odatav4.internal.olingo.OlingoEndpointHandler.mapODataResponse;
import static io.neonbee.endpoint.odatav4.internal.olingo.OlingoEndpointHandler.mapToODataRequest;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.OutputStream;

import org.apache.olingo.server.api.ODataContent;
import org.apache.olingo.server.api.ODataRequest;
import org.apache.olingo.server.api.ODataResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;

import com.google.common.base.Charsets;

import io.neonbee.internal.handler.CorrelationIdHandler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.net.HostAndPort;
import io.vertx.ext.web.RequestBody;
import io.vertx.ext.web.RoutingContext;

class OlingoEndpointHandlerTest {
    @Test
    @DisplayName("map generic response")
    void checkGenericResponseMapping() {
        ODataResponse odataResponse = new ODataResponse();
        odataResponse.setStatusCode(200);
        odataResponse.setHeader("expected1", "value1");
        odataResponse.setHeader("expected2", "value2");
        odataResponse.setContent(new ByteArrayInputStream("expected data".getBytes(Charsets.UTF_8)));

        HttpServerResponse responseMock = mock(HttpServerResponse.class);
        assertDoesNotThrow(() -> mapODataResponse(odataResponse, responseMock));

        verify(responseMock).setStatusCode(200);
        verify(responseMock).putHeader("expected1", "value1");
        verify(responseMock).putHeader("expected2", "value2");

        ArgumentCaptor<Buffer> endBuffer = ArgumentCaptor.forClass(Buffer.class);
        verify(responseMock).end(endBuffer.capture());
        assertThat(endBuffer.getValue().toString()).isEqualTo("expected data");
    }

    @Test
    @DisplayName("map OData response")
    void checkODataResponseMapping() {
        ODataResponse odataResponse = new ODataResponse();
        ODataContent odataContentMock = mock(ODataContent.class);
        doAnswer((Answer<ODataContent>) invocation -> {
            invocation.<OutputStream>getArgument(0).write("expected data".getBytes(Charsets.UTF_8));
            return null;
        }).when(odataContentMock).write(any(OutputStream.class));
        odataResponse.setODataContent(odataContentMock);

        HttpServerResponse responseMock = mock(HttpServerResponse.class);
        assertDoesNotThrow(() -> mapODataResponse(odataResponse, responseMock));

        ArgumentCaptor<Buffer> endBuffer = ArgumentCaptor.forClass(Buffer.class);
        verify(responseMock).end(endBuffer.capture());
        assertThat(endBuffer.getValue().toString()).isEqualTo("expected data");
    }

    @Test
    @DisplayName("test mapToODataRequest")
    void testMapToODataRequest() throws Exception {
        String expectedQuery = "$filter=foo eq 'bar'&$limit=42";
        String expectedPath = "/path";
        String expectedNamespace = "namespace";

        HttpServerRequest request = mock(HttpServerRequest.class);
        when(request.path()).thenReturn(expectedPath);
        when(request.query()).thenReturn(expectedQuery);
        when(request.scheme()).thenReturn("http");
        HostAndPort authority = mock(HostAndPort.class);
        when(authority.host()).thenReturn("localhost");
        when(request.authority()).thenReturn(authority);
        when(request.method()).thenReturn(HttpMethod.GET);
        when(request.headers()).thenReturn(MultiMap.caseInsensitiveMultiMap());

        RequestBody requestBody = mock(RequestBody.class);
        when(requestBody.buffer()).thenReturn(Buffer.buffer(""));

        RoutingContext routingContext = mock(RoutingContext.class);
        when(routingContext.request()).thenReturn(request);
        when(routingContext.body()).thenReturn(requestBody);
        when(routingContext.mountPoint()).thenReturn("/");
        when(routingContext.currentRoute()).thenReturn(null);
        when(routingContext.get(CorrelationIdHandler.CORRELATION_ID)).thenReturn("correlId");

        ODataRequest odataReq = mapToODataRequest(routingContext, expectedNamespace);
        assertThat(odataReq.getRawRequestUri())
                .isEqualTo("http://localhost/" + expectedNamespace + expectedPath + "?" + expectedQuery);
        assertThat(odataReq.getRawODataPath()).isEqualTo(expectedPath);
        assertThat(odataReq.getRawQueryPath()).isEqualTo(expectedQuery);
    }
}
