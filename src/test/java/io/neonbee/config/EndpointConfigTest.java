package io.neonbee.config;

import static com.google.common.truth.Truth.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.neonbee.config.AuthHandlerConfig.AuthHandlerType;
import io.neonbee.internal.helper.StringHelper;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

class EndpointConfigTest {

    @Test
    @DisplayName("test toJson")
    void testToJson() {
        String basePath = "hodorPath";
        String type = "hodorType";
        JsonObject additionalConfig = new JsonObject().put("Hodor", "Hodor");
        AuthHandlerConfig ahc = new AuthHandlerConfig().setType(AuthHandlerType.BASIC);
        EndpointConfig epc = new EndpointConfig().setBasePath(basePath).setType(type);
        epc.setAdditionalConfig(additionalConfig).setAuthChainConfig(List.of(ahc));

        JsonArray authenticationChain = new JsonArray().add(ahc.toJson());
        JsonObject expectedJson = new JsonObject().put("type", type).put("basePath", basePath).put("enabled", true);
        expectedJson.mergeIn(additionalConfig).put("authenticationChain", authenticationChain);
        assertThat(epc.toJson()).isEqualTo(expectedJson);
    }

    @Test
    @DisplayName("test JSON constructor")
    void testJSONConstructor() {
        String basePath = "hodorPath";
        String type = "hodorType";
        JsonObject additionalConfig = new JsonObject().put("Hodor", "Hodor");
        AuthHandlerConfig ahc = new AuthHandlerConfig().setType(AuthHandlerType.BASIC);
        JsonArray authenticationChain = new JsonArray().add(ahc.toJson());
        JsonObject json = new JsonObject().put("type", type).put("basePath", basePath).put("enabled", true);
        json.mergeIn(additionalConfig).put("authenticationChain", authenticationChain);

        EndpointConfig epc = new EndpointConfig().setBasePath(basePath).setType(type);
        epc.setAdditionalConfig(additionalConfig).setAuthChainConfig(List.of(ahc));
        assertThat(new EndpointConfig(json)).isEqualTo(epc);
    }

    @Test
    @DisplayName("test getters and setters")
    void testGettersAndSetters() {
        EndpointConfig ec = new EndpointConfig();

        assertThat(ec.setType("setType")).isSameInstanceAs(ec);
        assertThat(ec.getType()).isEqualTo("setType");

        assertThat(ec.setBasePath("setBasePath")).isSameInstanceAs(ec);
        assertThat(ec.getBasePath()).isEqualTo("setBasePath");

        assertThat(ec.setEnabled(false)).isSameInstanceAs(ec);
        assertThat(ec.isEnabled()).isEqualTo(false);

        List<AuthHandlerConfig> authHandlerConfig = List.of(new AuthHandlerConfig());
        assertThat(ec.setAuthChainConfig(authHandlerConfig)).isSameInstanceAs(ec);
        assertThat(ec.getAuthChainConfig()).containsExactlyElementsIn(authHandlerConfig);

        JsonObject additionalConfig = new JsonObject().put("Hodor", "Hodor");
        assertThat(ec.setAdditionalConfig(additionalConfig)).isSameInstanceAs(ec);
        assertThat(ec.getAdditionalConfig()).isEqualTo(additionalConfig);
    }

    @Test
    @DisplayName("test hashcode and equals")
    @SuppressWarnings("PMD.EqualsNull")
    void hashCodeAndEquals() {
        EndpointConfig ec = new EndpointConfig();
        EndpointConfig ec2 = new EndpointConfig().setBasePath("Hodor");

        // hashcode
        assertThat(ec.hashCode()).isEqualTo(new EndpointConfig().hashCode());
        assertThat(ec.hashCode()).isNotEqualTo(ec2.hashCode());
        assertThat(ec.hashCode()).isNotEqualTo(StringHelper.EMPTY.hashCode());

        // equals
        assertThat(ec.equals(ec)).isTrue();
        assertThat(ec.equals(null)).isFalse();
        assertThat(ec).isEqualTo(new EndpointConfig());
        assertThat(ec.equals(new Object())).isFalse();

        assertThat(ec).isNotEqualTo(ec2);
    }
}
