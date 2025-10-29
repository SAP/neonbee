package io.neonbee.endpoint.odatav4;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.apache.olingo.server.api.ODataRequest;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import io.neonbee.data.DataContext;
import io.neonbee.entity.AbstractEntityVerticle;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;

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
        when(routingContext.vertx()).thenReturn(Vertx.vertx());

        // mock ServiceMetadata and nested edm container namespace
        org.apache.olingo.server.api.ServiceMetadata serviceMetadata =
                mock(org.apache.olingo.server.api.ServiceMetadata.class);
        org.apache.olingo.commons.api.edm.Edm edm = mock(org.apache.olingo.commons.api.edm.Edm.class);
        org.apache.olingo.commons.api.edm.EdmEntityContainer container =
                mock(org.apache.olingo.commons.api.edm.EdmEntityContainer.class);
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

                ODataProxyEndpointHandler handler = new ODataProxyEndpointHandler(serviceMetadata);
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
    void handleFailsWhenMappingODataRequestThrows() throws Exception {
        RoutingContext routingContext = mock(RoutingContext.class);
        HttpServerRequest request = mock(HttpServerRequest.class);
        when(routingContext.request()).thenReturn(request);
        when(request.method()).thenReturn(HttpMethod.GET);

        org.apache.olingo.server.api.ServiceMetadata serviceMetadata =
                mock(org.apache.olingo.server.api.ServiceMetadata.class);
        org.apache.olingo.commons.api.edm.Edm edm = mock(org.apache.olingo.commons.api.edm.Edm.class);
        org.apache.olingo.commons.api.edm.EdmEntityContainer container =
                mock(org.apache.olingo.commons.api.edm.EdmEntityContainer.class);
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

            ODataProxyEndpointHandler handler = new ODataProxyEndpointHandler(serviceMetadata);
            handler.handle(routingContext);

            // verify that routingContext.fail was invoked with code 400
            verify(routingContext).fail(eq(400), any(Throwable.class));
        }
    }
}
