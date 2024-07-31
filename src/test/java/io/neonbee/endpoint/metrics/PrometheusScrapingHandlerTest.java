package io.neonbee.endpoint.metrics;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Future;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import io.vertx.junit5.VertxExtension;
import io.vertx.micrometer.backends.BackendRegistries;

@ExtendWith({ VertxExtension.class, MockitoExtension.class })
class PrometheusScrapingHandlerTest {
    @Test
    void testWithoutPrometheusMeterRegistry() {
        PrometheusScrapingHandler nph = new PrometheusScrapingHandler();
        try (MockedStatic<BackendRegistries> br = mockStatic(BackendRegistries.class)) {
            br.when(BackendRegistries::getDefaultNow).thenReturn(new CompositeMeterRegistry());

            ArgumentCaptor<Integer> statusCodeCaptor = ArgumentCaptor.forClass(Integer.class);
            ArgumentCaptor<String> statusMessageCaptor = ArgumentCaptor.forClass(String.class);

            RoutingContext rcMock = mock(RoutingContext.class);
            HttpServerResponse responseMock = mock(HttpServerResponse.class);
            when(rcMock.response()).thenReturn(responseMock);
            when(responseMock.setStatusCode(statusCodeCaptor.capture())).thenReturn(responseMock);
            when(responseMock.setStatusMessage(statusMessageCaptor.capture())).thenReturn(responseMock);
            when(responseMock.end()).thenReturn(Future.succeededFuture());
            nph.handle(rcMock);

            assertThat(statusCodeCaptor.getValue()).isEqualTo(HttpResponseStatus.INTERNAL_SERVER_ERROR.code());
            assertThat(statusMessageCaptor.getValue()).isEqualTo("Could not find a PrometheusMeterRegistry");
        }
    }

    @Test
    void testPrometheusMeterRegistry() {
        PrometheusScrapingHandler nph = new PrometheusScrapingHandler("something");
        try (MockedStatic<BackendRegistries> br = mockStatic(BackendRegistries.class)) {
            br.when(() -> BackendRegistries.getNow(any()))
                    .thenReturn(new PrometheusMeterRegistry(PrometheusConfig.DEFAULT));

            ArgumentCaptor<CharSequence> contentTypeCaptor = ArgumentCaptor.forClass(CharSequence.class);
            ArgumentCaptor<CharSequence> contentTypeCaptor004 = ArgumentCaptor.forClass(CharSequence.class);

            RoutingContext rcMock = mock(RoutingContext.class);
            HttpServerResponse responseMock = mock(HttpServerResponse.class);
            when(rcMock.response()).thenReturn(responseMock);
            when(responseMock.putHeader(contentTypeCaptor.capture(), contentTypeCaptor004.capture()))
                    .thenReturn(responseMock);
            when(responseMock.end(ArgumentMatchers.<String>any())).thenReturn(Future.succeededFuture());
            nph.handle(rcMock);

            assertThat(contentTypeCaptor.getValue().toString()).isEqualTo(HttpHeaders.CONTENT_TYPE.toString());
            assertThat(contentTypeCaptor004.getValue().toString())
                    .isEqualTo("text/plain; version=0.0.4; charset=utf-8");
        }
    }
}
