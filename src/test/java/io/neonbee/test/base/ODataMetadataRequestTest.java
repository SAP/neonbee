package io.neonbee.test.base;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.stream.Stream;

import org.apache.olingo.commons.api.edm.FullQualifiedName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.vertx.core.http.HttpMethod;

class ODataMetadataRequestTest {

    private ODataMetadataRequest metadataRequest;

    static Stream<Arguments> withNamespaces() {
        return Stream.of(arguments("my-namespace", "my-namespace/$metadata"), arguments("", "$metadata"));
    }

    @BeforeEach
    void setUp() {
        metadataRequest = new ODataMetadataRequest(new FullQualifiedName("my-namespace", "my-entity"));
    }

    @Test
    void testMethod() {
        assertThat(metadataRequest.method).isEqualTo(HttpMethod.GET);
    }

    @ParameterizedTest(name = "{index}: with namespace ''{0}''")
    @MethodSource("withNamespaces")
    @DisplayName("test for correct URI")
    void testGetUri(String namespace, String expected) {
        ODataMetadataRequest request =
                new ODataMetadataRequest(new FullQualifiedName(namespace, "my-entity"));
        assertThat(request.getUri()).isEqualTo(expected);
    }

    @Test
    @DisplayName("Verify self() returns correct instance")
    void testGetSelf() {
        assertThat(metadataRequest.self()).isEqualTo(metadataRequest);
    }
}
