package io.neonbee.config;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.config.AuthProviderConfig.AuthProviderType.HTDIGEST;
import static io.neonbee.config.AuthProviderConfig.AuthProviderType.JDBC;
import static io.neonbee.config.AuthProviderConfig.AuthProviderType.MONGO;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.function.BiConsumer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.neonbee.NeonBeeMockHelper;
import io.neonbee.config.AuthProviderConfig.AuthProviderType;
import io.neonbee.internal.helper.StringHelper;
import io.vertx.core.json.JsonObject;

class AuthProviderConfigTest {

    @Test
    @DisplayName("Throw error if AuthProvderType is undupported")
    void testUnsupportedAuthProviderTypes() {
        BiConsumer<AuthProviderType, String> checkUnsupported = (type, msg) -> {
            UnsupportedOperationException exception = assertThrows(UnsupportedOperationException.class, () -> {
                new AuthProviderConfig().setType(type).createAuthProvider(NeonBeeMockHelper.defaultVertxMock());
            });
            assertThat(exception.getMessage()).isEqualTo(msg);
        };
        checkUnsupported.accept(JDBC, "JDBC authentication provider is not implemented yet");
        checkUnsupported.accept(MONGO, "MongoDB authentication provider is not implemented yet");
    }

    @Test
    @DisplayName("test toJson")
    void testToJson() {
        JsonObject additionalConfig = new JsonObject().put("Hodor", "Hodor");
        AuthProviderConfig apc = new AuthProviderConfig().setType(JDBC).setAdditionalConfig(additionalConfig);

        JsonObject expectedJson = new JsonObject().put("type", "JDBC").mergeIn(additionalConfig);
        assertThat(apc.toJson()).isEqualTo(expectedJson);
    }

    @Test
    @DisplayName("test JsonConstructor")
    void testJsonConstructor() {
        JsonObject additionalConfig = new JsonObject().put("Hodor", "Hodor");
        JsonObject json = new JsonObject().put("type", "JDBC").mergeIn(additionalConfig);

        AuthProviderConfig expectedAPC = new AuthProviderConfig().setType(JDBC).setAdditionalConfig(additionalConfig);
        assertThat(new AuthProviderConfig(json)).isEqualTo(expectedAPC);
    }

    @Test
    @DisplayName("test getters and setters")
    void testGettersAndSetters() {
        AuthProviderConfig apc = new AuthProviderConfig();

        assertThat(apc.setType(HTDIGEST)).isSameInstanceAs(apc);
        assertThat(apc.getType()).isEqualTo(HTDIGEST);

        JsonObject additionalConfig = new JsonObject().put("Hodor", "Hodor");
        assertThat(apc.setAdditionalConfig(additionalConfig)).isSameInstanceAs(apc);
        assertThat(apc.getAdditionalConfig()).isEqualTo(additionalConfig);
    }

    @Test
    @DisplayName("test hashcode and equals")
    @SuppressWarnings("PMD.EqualsNull")
    void hashCodeAndEquals() {
        AuthProviderConfig apc = new AuthProviderConfig();
        AuthProviderConfig apc2 = new AuthProviderConfig().setType(HTDIGEST);

        // hashcode
        assertThat(apc.hashCode()).isEqualTo(new AuthProviderConfig().hashCode());
        assertThat(apc.hashCode()).isNotEqualTo(apc2.hashCode());
        assertThat(apc.hashCode()).isNotEqualTo(StringHelper.EMPTY.hashCode());

        // equals
        assertThat(apc.equals(apc)).isTrue();
        assertThat(apc.equals(null)).isFalse();
        assertThat(apc).isEqualTo(new AuthProviderConfig());
        assertThat(apc.equals(new Object())).isFalse();

        assertThat(apc).isNotEqualTo(apc2);
    }
}
