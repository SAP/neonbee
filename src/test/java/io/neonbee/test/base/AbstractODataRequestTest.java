package io.neonbee.test.base;

import static com.google.common.truth.Truth.assertThat;
import static io.vertx.core.MultiMap.caseInsensitiveMultiMap;
import static io.vertx.core.http.HttpHeaders.ACCEPT_CHARSET;
import static io.vertx.core.http.HttpHeaders.CONTENT_TYPE;
import static io.vertx.core.http.HttpMethod.GET;
import static java.nio.charset.Charset.defaultCharset;
import static java.util.stream.Collectors.toMap;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.apache.olingo.commons.api.edm.FullQualifiedName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.junit5.VertxTestContext;

class AbstractODataRequestTest extends NeonBeeTestBase {

    private AbstractODataRequest<AbstractODataRequestImpl> odataRequest;

    static Stream<Arguments> provideNamespaceInput() {
        return Stream.of(
                arguments(new FullQualifiedName("my-namespace", "my-entity"), "my-namespace/"),
                arguments(new FullQualifiedName("", "my-entity"), ""));
    }

    static Stream<Arguments> withHeaders() {
        return Stream.of(
                arguments(Map.of(CONTENT_TYPE.toString(), "application/http", ACCEPT_CHARSET.toString(),
                        defaultCharset().name())));
    }

    static Stream<HttpMethod> withHttpMethods() {
        return HttpMethod.values().stream();
    }

    private void assertContainsExactlyEntriesIn(MultiMap actual, Map<String, String> expected) {
        Map<String, String> actualJavaMap = actual.entries().stream().collect(toMap(Entry::getKey, Entry::getValue));
        assertThat(actual.size()).isEqualTo(actualJavaMap.size());
        assertThat(actualJavaMap).containsExactlyEntriesIn(expected);
    }

    @BeforeEach
    @DisplayName("setup the base OData request")
    void setUp() {
        odataRequest = new AbstractODataRequestImpl(new FullQualifiedName("my-namespace", "my-entity"))
                .setHeaders(Map.of("any-header", "any-value"));
    }

    @ParameterizedTest(name = "{index}: with FQN ''{0}'' expecting ''{1}''")
    @MethodSource("provideNamespaceInput")
    @DisplayName("get OData request's URI namespace path")
    void testGetUriNamespacePath(FullQualifiedName fqn, String expected) {
        String actual = new AbstractODataRequestImpl(fqn).getUriNamespacePath();
        assertThat(actual).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{index}: http method {0}")
    @MethodSource("withHttpMethods")
    @DisplayName("set OData request method")
    void testSetMethod(HttpMethod method) {
        odataRequest.setMethod(method);
        assertThat(odataRequest.method).isEqualTo(method);
    }

    @Test
    @DisplayName("set OData request method to null")
    void testSetMethodPassingNull() {
        odataRequest.setMethod(null);
        assertThat(odataRequest.method).isEqualTo(GET);
    }

    @Test
    @DisplayName("intercept and send request")
    void testInterceptAndSend(VertxTestContext testContext) {
        testContext.verify(() -> {
            Consumer<HttpRequest<Buffer>> interceptor = mock(Consumer.class);
            odataRequest.interceptRequest(interceptor).send(getNeonBee())
                    .onComplete(testContext.succeeding(response -> {
                        verify(interceptor, times(1)).accept(any());
                        assertThat(response.statusCode()).isEqualTo(404);
                        testContext.completeNow();
                    }));
        });
    }

    @ParameterizedTest(name = "{index}: with entries {0}")
    @MethodSource("io.neonbee.test.base.AbstractODataRequestTest#withHeaders")
    @DisplayName("add OData request header")
    void testAddHeader(Map<String, String> headers) {
        Map<String, String> expected =
                Stream.of(odataRequest.headers.entries(), headers.entrySet()).flatMap(Collection::stream).collect(
                        toMap(Entry::getKey, Entry::getValue));
        headers.forEach((name, value) -> odataRequest.addHeader(name, value));
        assertContainsExactlyEntriesIn(odataRequest.headers, expected);
    }

    @ParameterizedTest(name = "{index}: with entries {0}")
    @MethodSource("io.neonbee.test.base.AbstractODataRequestTest#withHeaders")
    @DisplayName("set OData request's headers as MultiMap")
    void testSetHeadersMultiMap(Map<String, String> expected) {
        odataRequest.setHeaders(caseInsensitiveMultiMap().addAll(expected));
        assertContainsExactlyEntriesIn(odataRequest.headers, expected);
    }

    @ParameterizedTest(name = "{index}: with entries {0}")
    @MethodSource("io.neonbee.test.base.AbstractODataRequestTest#withHeaders")
    @DisplayName("set OData request's headers as Java's map")
    void testSetHeadersMap(Map<String, String> expected) {
        odataRequest.setHeaders(expected);
        assertContainsExactlyEntriesIn(odataRequest.headers, expected);
    }

    @Test
    @DisplayName("set OData request's headers to null")
    void testSetHeadersNull() {
        odataRequest.setHeaders((Map<String, String>) null);
        assertThat(odataRequest.headers).isEmpty();
    }

    @Test
    @DisplayName("verify self() returns correct instance")
    void testGetSelf() {
        assertThat(odataRequest.self()).isEqualTo(odataRequest);
    }

    private static class AbstractODataRequestImpl extends AbstractODataRequest<AbstractODataRequestImpl> {
        AbstractODataRequestImpl(FullQualifiedName entity) {
            super(entity);
        }

        @Override
        protected AbstractODataRequestImpl self() {
            return this;
        }

        @Override
        protected String getUri() {
            return null;
        }
    }
}
