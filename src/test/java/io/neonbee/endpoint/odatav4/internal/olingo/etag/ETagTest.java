package io.neonbee.endpoint.odatav4.internal.olingo.etag;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.test.helper.ResourceHelper.TEST_RESOURCES;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.apache.olingo.commons.api.edm.FullQualifiedName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import io.neonbee.test.base.ODataEndpointTestBase;
import io.neonbee.test.base.ODataRequest;
import io.vertx.core.http.HttpMethod;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxTestContext;

class ETagTest extends ODataEndpointTestBase {
    @Override
    protected List<Path> provideEntityModels() {
        return List.of(TEST_RESOURCES.resolveRelated("ProcessorService.csn"));
    }

    static Stream<HttpMethod> withHttpMethods() {
        return Stream.of(HttpMethod.GET, HttpMethod.HEAD);
    }

    @ParameterizedTest
    @MethodSource("withHttpMethods")
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("An origin server MUST NOT perform the requested method if the condition evaluates to false."
            + "Instead, the origin server MUST respond with either a) the 304 (Not Modified) status code if"
            + "the request method is HEAD or GET.")
    void testIfNoneMatchConditionRequest(HttpMethod method, VertxTestContext testContext) {
        FullQualifiedName fqn = new FullQualifiedName("io.neonbee.processor.ProcessorService", "WillBeIgnored");
        ODataRequest oDataRequest = new ODataRequest(fqn).setMethod(method).setMetadata();

        requestOData(oDataRequest).compose(firstResponse -> {
            assertThat(firstResponse.statusCode()).isEqualTo(200);
            if (method.equals(HttpMethod.HEAD)) {
                assertThat(firstResponse.bodyAsString()).isNull();
            } else {
                assertThat(firstResponse.bodyAsString()).isNotNull();
            }
            String eTag = firstResponse.getHeader("ETag");
            assertThat(eTag).isNotNull();

            return requestOData(oDataRequest.addHeader("If-None-Match", eTag));
        }).onComplete(testContext.succeeding(secondResponse -> {
            testContext.verify(() -> assertThat(secondResponse.statusCode()).isEqualTo(304));
            testContext.completeNow();
        }));
    }
}
