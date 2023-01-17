package io.neonbee.endpoint.health;

import static io.neonbee.endpoint.health.HealthCheckHandler.APPLICATION_JSON_CHARSET_UTF_8;
import static io.neonbee.endpoint.health.HealthCheckHandler.VERSION_FILE;
import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentMatchers;

import io.neonbee.data.DataContext;
import io.neonbee.health.HealthCheckRegistry;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.FileSystem;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.RoutingContext;
import io.vertx.junit5.VertxExtension;

@ExtendWith(VertxExtension.class)
class HealthCheckHandlerTest {
    HealthCheckRegistry registry;

    RoutingContext routingContext;

    HttpServerResponse httpServerResponse;

    HttpServerRequest serverRequest;

    @BeforeEach
    void setUp() {
        registry = mock(HealthCheckRegistry.class);
        routingContext = mock(RoutingContext.class);
        httpServerResponse = mock(HttpServerResponse.class);

        when(httpServerResponse.setStatusCode(anyInt())).thenReturn(httpServerResponse);
        when(httpServerResponse.setStatusMessage(anyString())).thenReturn(httpServerResponse);
        when(httpServerResponse.putHeader(any(CharSequence.class), any(CharSequence.class)))
                .thenReturn(httpServerResponse);
        when(httpServerResponse.end()).thenReturn(succeededFuture());
        when(routingContext.response()).thenReturn(httpServerResponse);

        Route route = mock(Route.class);
        when(route.getPath()).thenReturn("/");
        when(routingContext.currentRoute()).thenReturn(route);
        when(routingContext.mountPoint()).thenReturn("/health");

        serverRequest = mock(HttpServerRequest.class);
        when(serverRequest.path()).thenReturn("/health");
        when(routingContext.request()).thenReturn(serverRequest);
    }

    private static JsonObject buildExpectedJson(String status, String outcome, JsonObject... checks) {
        JsonObject expected = new JsonObject().put("status", status).put("outcome", outcome);
        if (checks != null && checks.length > 0) {
            expected.put("checks", new JsonArray(Arrays.asList(checks)));
        }
        return expected;
    }

    static Stream<Arguments> getInputs() {
        return Stream.of(
                Arguments.of(buildExpectedJson("UP", "UP", new JsonObject().put("id", "dummy").put("status", "UP")),
                        200, "status is up"),
                Arguments.of(buildExpectedJson("UP", "UP"), 204, "response is empty"),
                Arguments.of(
                        buildExpectedJson("DOWN", "DOWN", new JsonObject().put("id", "dummy").put("status", "DOWN")),
                        503, "status is down (without any reason)"),
                Arguments.of(
                        buildExpectedJson("DOWN", "DOWN").put("data",
                                new JsonObject().put("procedure-execution-failure", true)),
                        500, "status is down (with procedure error, but no checks)"),
                Arguments.of(
                        buildExpectedJson("DOWN", "DOWN",
                                new JsonObject().put("id", "dummy").put("status", "DOWN").put("data",
                                        new JsonObject().put("procedure-execution-failure", true))),
                        500, "status is down (with procedure error)"));
    }

    @ParameterizedTest(name = "{index}: {2} => HTTP {1}")
    @MethodSource("getInputs")
    @DisplayName("should handle response, if")
    @SuppressWarnings("unused")
    void testHandle(JsonObject expectedResponse, int expectedStatusCode, String description) {
        when(registry.collectHealthCheckResults(any(DataContext.class), anyString()))
                .thenReturn(succeededFuture(expectedResponse));

        new HealthCheckHandler(registry, null).handle(routingContext);

        verify(httpServerResponse).putHeader(eq(HttpHeaders.CONTENT_TYPE), eq(APPLICATION_JSON_CHARSET_UTF_8));
        verify(httpServerResponse).setStatusCode(eq(expectedStatusCode));
        if (expectedStatusCode == 204) {
            verify(httpServerResponse).end();
        } else {
            verify(httpServerResponse).end(anyString());
        }
    }

    @Test
    @DisplayName("should return internal server error, if retrieving data fails")
    void testHandleFailure() {
        when(registry.collectHealthCheckResults(any(DataContext.class), anyString()))
                .thenReturn(failedFuture(new Throwable("oops")));

        new HealthCheckHandler(registry, null).handle(routingContext);

        verify(httpServerResponse).setStatusCode(eq(500));
        verify(httpServerResponse).setStatusMessage(matches("Could not request any health data."));
    }

    @Test
    @DisplayName("should handle requests of specific checks")
    void testHandleCheck() {
        String checkName = "dummy";
        when(serverRequest.path()).thenReturn("/health/" + checkName);
        JsonObject expectedResponse =
                buildExpectedJson("UP", "UP", new JsonObject().put("id", checkName).put("status", "UP"));
        when(registry.collectHealthCheckResults(any(DataContext.class), anyString()))
                .thenReturn(succeededFuture(expectedResponse));

        new HealthCheckHandler(registry, null).handle(routingContext);

        verify(httpServerResponse).setStatusCode(eq(200));
        verify(httpServerResponse).end(contains(checkName));
        verify(registry).collectHealthCheckResults(any(DataContext.class), eq(checkName));
    }

    @Test
    @DisplayName("should handle non-verbose checks")
    void testHandleNonVerbose(Vertx vertx) {
        String expectedStatus = "UP";
        JsonObject expectedResponse = buildExpectedJson(expectedStatus, expectedStatus,
                new JsonObject().put("id", "dummy").put("status", expectedStatus));
        when(registry.collectHealthCheckResults(any(DataContext.class), anyString()))
                .thenReturn(succeededFuture(expectedResponse));

        Vertx vertxSpy = spy(vertx);
        String expectedVersion = "1.0.1";
        FileSystem fs = mock(FileSystem.class);
        when(fs.readFile(eq(VERSION_FILE))).thenReturn(succeededFuture(Buffer.buffer(expectedVersion)));
        doReturn(fs).when(vertxSpy).fileSystem();

        new HealthCheckHandler(registry, false, vertxSpy).handle(routingContext);

        verify(httpServerResponse).setStatusCode(eq(200));
        verify(httpServerResponse).end(ArgumentMatchers.<String>argThat(m -> {
            JsonObject entries = new JsonObject(m);
            return entries.getString("status").equals(expectedStatus)
                    && entries.getString("version").equals(expectedVersion);
        }));
    }
}
