package io.neonbee.endpoint.odatav4.internal.olingo.etag;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.test.helper.ResourceHelper.TEST_RESOURCES;

import java.nio.file.Path;
import java.util.List;

import org.apache.olingo.commons.api.edm.FullQualifiedName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.neonbee.test.base.ODataEndpointTestBase;
import io.neonbee.test.base.ODataMetadataRequest;
import io.vertx.junit5.VertxTestContext;

class ETagTest extends ODataEndpointTestBase {
    @Override
    protected List<Path> provideEntityModels() {
        return List.of(TEST_RESOURCES.resolveRelated("ProcessorService.csn"));
    }

    @Test
    @DisplayName("An origin server MUST NOT perform the requested method if the condition evaluates to false."
            + "Instead, the origin server MUST respond with the 304 (Not Modified) status code if"
            + "the request method is HEAD or GET.")
    void testIfNoneMatchConditionRequest(VertxTestContext testContext) {
        ODataMetadataRequest oDataRequest = new ODataMetadataRequest(
                new FullQualifiedName("io.neonbee.processor.ProcessorService", "WillBeIgnored"));

        requestOData(oDataRequest).compose(firstResponse -> {
            assertThat(firstResponse.statusCode()).isEqualTo(200);
            assertThat(firstResponse.bodyAsString()).isNotNull();
            String eTag = firstResponse.getHeader("ETag");
            assertThat(eTag).isNotNull();

            return requestOData(oDataRequest.addHeader("If-None-Match", eTag));
        }).onComplete(testContext.succeeding(secondResponse -> {
            testContext.verify(() -> assertThat(secondResponse.statusCode()).isEqualTo(304));
            testContext.completeNow();
        }));
    }
}
