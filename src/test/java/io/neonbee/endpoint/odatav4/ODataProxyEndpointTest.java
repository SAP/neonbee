package io.neonbee.endpoint.odatav4;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.endpoint.odatav4.ODataV4EndpointTest.mockEntityModel;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.neonbee.config.EndpointConfig;
import io.neonbee.endpoint.odatav4.ODataV4Endpoint.UriConversion;
import io.neonbee.entity.EntityModel;

class ODataProxyEndpointTest {
    static Stream<Arguments> filterModelArguments() {
        return Stream.of(
                Arguments.of(mockEntityModel(ODataV4Endpoint.NEONBEE_ENDPOINT_CDS_SERVICE_ANNOTATION, "odataproxy"),
                        true),
                Arguments.of(mockEntityModel(ODataV4Endpoint.NEONBEE_ENDPOINT_CDS_SERVICE_ANNOTATION, "something"),
                        false),
                Arguments.of(mockEntityModel("foo", "bar"), false));
    }

    @Test
    void getDefaultConfig() {
        ODataProxyEndpoint endpoint = new ODataProxyEndpoint();
        EndpointConfig defaultConfig = endpoint.getDefaultConfig();
        assertThat(defaultConfig.getType()).isEqualTo(ODataProxyEndpoint.class.getName());
        assertThat(defaultConfig.getBasePath()).isEqualTo(ODataProxyEndpoint.DEFAULT_BASE_PATH);
        assertThat(defaultConfig.getAdditionalConfig().getString("uriConversion"))
                .isEqualTo(UriConversion.STRICT.name());
    }

    @Test
    @DisplayName("Test get request handler returns ODataProxyEndpointHandler")
    void testGetRequestHandlerReturnsODataProxyEndpointHandler() {
        ODataProxyEndpoint endpoint = new ODataProxyEndpoint();
        assertThat(endpoint.getRequestHandler(null, UriConversion.STRICT))
                .isInstanceOf(ODataProxyEndpointHandler.class);
        // explicit JUnit assertion so static analysis recognizes this test has assertions
        assertNotNull(endpoint);
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("filterModelArguments")
    @DisplayName("Test filter models includes only those with neonbee.endpoint annotation set to odataproxy")
    void testFilterModelsIncludesOnlyAnnotatedModels(EntityModel model, boolean resutl) {
        ODataProxyEndpoint endpoint = new ODataProxyEndpoint();
        assertThat(endpoint.filterModels(model)).isEqualTo(resutl);
    }
}
