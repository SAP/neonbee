package io.neonbee.endpoint.metrics;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
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
import io.neonbee.NeonBee;
import io.neonbee.hook.HookRegistry;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
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

            RoutingContext rcMock = mock(RoutingContext.class);
            nph.handle(rcMock);

            verify(rcMock).fail(HttpResponseStatus.INTERNAL_SERVER_ERROR.code());
        }
    }

    @Test
    void testPrometheusMeterRegistry() {
        PrometheusScrapingHandler nph = new PrometheusScrapingHandler("something");
        try (MockedStatic<BackendRegistries> br = mockStatic(BackendRegistries.class);
                MockedStatic<NeonBee> staticNeonBeeMock = mockStatic(NeonBee.class)) {
            br.when(() -> BackendRegistries.getNow(any()))
                    .thenReturn(new PrometheusMeterRegistry(PrometheusConfig.DEFAULT));

            NeonBee neonBeeMock = mock(NeonBee.class);
            HookRegistry hookRegistryMock = mock(HookRegistry.class);
            lenient().when(neonBeeMock.getHookRegistry()).thenReturn(hookRegistryMock);
            lenient().when(hookRegistryMock.executeHooks(any(), any()))
                    .thenReturn(Future.all(Future.succeededFuture(), Future.succeededFuture()));
            staticNeonBeeMock.when(() -> NeonBee.get(any(Vertx.class))).thenReturn(neonBeeMock);

            ArgumentCaptor<CharSequence> contentTypeCaptor = ArgumentCaptor.forClass(CharSequence.class);
            ArgumentCaptor<CharSequence> contentTypeCaptor004 = ArgumentCaptor.forClass(CharSequence.class);

            RoutingContext rcMock = mock(RoutingContext.class);
            HttpServerResponse responseMock = mock(HttpServerResponse.class);
            lenient().when(rcMock.vertx()).thenReturn(mock(Vertx.class));
            when(rcMock.response()).thenReturn(responseMock);
            when(responseMock.putHeader(contentTypeCaptor.capture(), contentTypeCaptor004.capture()))
                    .thenReturn(responseMock);
            lenient().when(rcMock.end(ArgumentMatchers.<String>any())).thenReturn(Future.succeededFuture());
            nph.handle(rcMock);

            assertThat(contentTypeCaptor.getValue().toString()).isEqualTo(HttpHeaders.CONTENT_TYPE.toString());
            assertThat(contentTypeCaptor004.getValue().toString())
                    .isEqualTo(PrometheusScrapingHandler.PROMETHEUS_TEXT_FORMAT_CONTENT_TYPE);
        }
    }
}
