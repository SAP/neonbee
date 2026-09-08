package io.neonbee.internal.tracing;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.ISO_8859_1;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.zip.GZIPInputStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.neonbee.NeonBeeOptions;
import io.neonbee.config.TracingConfig;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.vertx.core.Vertx;
import io.vertx.core.VertxBuilder;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.tracing.TracingPolicy;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * End-to-end test of the OpenTelemetry <b>export pipeline</b> against an in-process mock OTLP receiver.
 * <p>
 * A tiny Vert.x HTTP server plays the role of the OTLP backend (e.g. a Dynatrace environment or an OpenTelemetry
 * Collector) so the real production export path can be exercised without any external infrastructure, container runtime
 * or additional dependency:
 * <ul>
 * <li>{@link #tracesAreExportedThroughTheOtlpPipeline} builds the production {@link OpenTelemetrySdk} through
 * {@link NeonBeeOpenTelemetry#buildSdk(NeonBeeOptions)}, emits a span and asserts it is POSTed to {@code /v1/traces}
 * with the {@code Authorization: Api-Token ...} header (as required by Dynatrace).</li>
 * <li>{@link #verticleCommunicationIsExportedAsSpans} attaches the tracer to a real Vert.x instance exactly as
 * {@link NeonBeeOpenTelemetry#applyTracing(VertxBuilder, NeonBeeOptions)} does in production, performs an event bus
 * request/reply (the mechanism NeonBee's {@code DataVerticle} / {@code EntityVerticle} communication rides on) and
 * asserts that the resulting spans are exported and carry the event bus address.</li>
 * </ul>
 */
@ExtendWith(VertxExtension.class)
class NeonBeeOpenTelemetryExportTest {

    private static final String EVENT_BUS_ADDRESS = "neonbee.test.verticle.communication";

    private static NeonBeeOptions optionsFor(int mockPort) {
        return new NeonBeeOptions.Mutable().setInstanceName("otel-export-test")
                .setTracingConfig(new TracingConfig().setEnabled(true).setExportIntervalSeconds(1)
                        .setOtlpApiToken("dt0c01.test-token")
                        .setOtlpEndpoint("http://localhost:" + mockPort));
    }

    @Test
    @Timeout(30_000)
    @DisplayName("spans are exported to the OTLP /v1/traces endpoint with the Api-Token header")
    void tracesAreExportedThroughTheOtlpPipeline(Vertx vertx, VertxTestContext testContext) {
        List<RecordedRequest> received = new CopyOnWriteArrayList<>();

        startMockOtlpReceiver(vertx, received, testContext, request -> testContext.verify(() -> {
            assertThat(request.path).isEqualTo("/v1/traces");
            assertThat(request.authorization).isEqualTo("Api-Token dt0c01.test-token");
            assertThat(request.contentType).contains("application/x-protobuf");
            // the span name is carried verbatim (UTF-8) inside the protobuf payload
            assertThat(request.bodyAsText()).contains("export-pipeline-span");
            testContext.completeNow();
        })).onFailure(testContext::failNow).onSuccess(port -> {
            OpenTelemetrySdk sdk = NeonBeeOpenTelemetry.buildSdk(optionsFor(port)).orElseThrow();
            testContext.verify(() -> {
                sdk.getTracer("neonbee-test").spanBuilder("export-pipeline-span").startSpan().end();
                // non-blocking flush; the mock receiver completes the test once the export arrives
                sdk.getSdkTracerProvider().forceFlush();
            });
        });
    }

    @Test
    @Timeout(30_000)
    @DisplayName("event bus (verticle) communication is traced and exported as spans")
    void verticleCommunicationIsExportedAsSpans(Vertx vertx, VertxTestContext testContext) {
        List<RecordedRequest> received = new CopyOnWriteArrayList<>();

        startMockOtlpReceiver(vertx, received, testContext, request -> testContext.verify(() -> {
            assertThat(request.path).isEqualTo("/v1/traces");
            String body = request.bodyAsText();
            // the root span we created and the event bus address of the "verticle" call must both be exported
            assertThat(body).contains("verticle-communication-root");
            assertThat(body).contains(EVENT_BUS_ADDRESS);
            testContext.completeNow();
        })).onFailure(testContext::failNow).onSuccess(port -> {
            // attach the tracer to a real Vert.x instance exactly as NeonBee does before building Vert.x
            VertxBuilder builder = Vertx.builder();
            OpenTelemetrySdk sdk = NeonBeeOpenTelemetry.applyTracing(builder, optionsFor(port)).orElseThrow();
            Vertx tracedVertx = builder.build();

            // a "verticle" that replies to requests on the event bus
            tracedVertx.eventBus().<String>consumer(EVENT_BUS_ADDRESS, message -> message.reply("pong"));

            // perform the request within a root span on a Vert.x context so the whole exchange is traced
            tracedVertx.runOnContext(v -> {
                Span root = sdk.getTracer("neonbee-test").spanBuilder("verticle-communication-root").startSpan();
                try (Scope scope = root.makeCurrent()) {
                    tracedVertx.eventBus()
                            .request(EVENT_BUS_ADDRESS, "ping",
                                    new DeliveryOptions().setTracingPolicy(TracingPolicy.ALWAYS))
                            .onComplete(reply -> {
                                root.end();
                                sdk.getSdkTracerProvider().forceFlush();
                            });
                }
            });
        });
    }

    /**
     * Starts an in-process HTTP server that records incoming OTLP requests and invokes {@code onRequest} for each of
     * them, then responds with {@code 200} (an empty protobuf body, which OTLP treats as success).
     *
     * @return a future to the port the mock receiver listens on
     */
    private io.vertx.core.Future<Integer> startMockOtlpReceiver(Vertx vertx, List<RecordedRequest> received,
            VertxTestContext testContext, java.util.function.Consumer<RecordedRequest> onRequest) {
        HttpServer server = vertx.createHttpServer();
        return server.requestHandler(request -> request.body().onComplete(body -> {
            RecordedRequest recorded = new RecordedRequest(request.path(), request.getHeader("Authorization"),
                    request.getHeader("Content-Type"), request.getHeader("Content-Encoding"),
                    body.succeeded() ? body.result() : Buffer.buffer());
            received.add(recorded);
            try {
                onRequest.accept(recorded);
            } catch (RuntimeException e) {
                testContext.failNow(e);
            }
            // application/x-protobuf empty ExportTraceServiceResponse is a valid success response
            request.response().putHeader("Content-Type", "application/x-protobuf").end();
        })).listen(0).map(HttpServer::actualPort);
    }

    /**
     * A single request received by the mock OTLP receiver.
     */
    private static final class RecordedRequest {
        private final String path;

        private final String authorization;

        private final String contentType;

        private final String contentEncoding;

        private final Buffer body;

        RecordedRequest(String path, String authorization, String contentType, String contentEncoding, Buffer body) {
            this.path = path;
            this.authorization = authorization;
            this.contentType = contentType;
            this.contentEncoding = contentEncoding;
            this.body = body;
        }

        /**
         * Returns the (optionally gzip-decompressed) request body as a byte-preserving ISO-8859-1 string, so ASCII span
         * names / attribute values embedded in the protobuf payload can be searched for.
         *
         * @return the body as text
         */
        String bodyAsText() {
            byte[] bytes = body.getBytes();
            if ("gzip".equalsIgnoreCase(contentEncoding)) {
                try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
                    bytes = gzip.readAllBytes();
                } catch (IOException e) {
                    throw new IllegalStateException("Failed to gunzip OTLP request body", e);
                }
            }
            return new String(bytes, ISO_8859_1);
        }
    }
}
