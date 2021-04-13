package io.neonbee.config;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.config.AuthHandlerConfig.AuthHandlerType.HTDIGEST;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.function.BiConsumer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.neonbee.NeonBeeMockHelper;
import io.neonbee.config.AuthHandlerConfig.AuthHandlerType;
import io.neonbee.config.AuthProviderConfig.AuthProviderType;
import io.neonbee.internal.helper.StringHelper;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

class AuthHandlerConfigTest {

    @Test
    @DisplayName("Throw error if auth provder type does not match auth handler type")
    void testUnsupportedAuthProviderTypes() {
        Vertx vertx = NeonBeeMockHelper.defaultVertxMock();
        BiConsumer<AuthHandlerType, AuthProviderType> checkUnsupported = (handler, provider) -> {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                handler.createAuthHandler(vertx, new AuthProviderConfig().setType(provider), new JsonObject());
            });
            assertThat(exception.getMessage()).startsWith("Cannot configure");
        };
        checkUnsupported.accept(HTDIGEST, AuthProviderType.JDBC);
        checkUnsupported.accept(AuthHandlerType.JWT, AuthProviderType.JDBC);
        checkUnsupported.accept(AuthHandlerType.OAUTH2, AuthProviderType.JDBC);
    }

    @Test
    @DisplayName("test toJson")
    void testToJson() {
        AuthProviderConfig apc = new AuthProviderConfig().setType(AuthProviderType.JDBC);
        JsonObject additionalConfig = new JsonObject().put("Hodor", "Hodor");
        AuthHandlerConfig ahc = new AuthHandlerConfig().setType(HTDIGEST).setAdditionalConfig(additionalConfig)
                .setAuthProviderConfig(apc);

        JsonObject providerJson = new JsonObject().put("type", "JDBC");
        JsonObject expectedJson =
                new JsonObject().put("type", "HTDIGEST").mergeIn(additionalConfig).put("provider", providerJson);
        assertThat(ahc.toJson()).isEqualTo(expectedJson);
    }

    @Test
    @DisplayName("test JsonConstructor")
    void testJsonConstructor() {
        JsonObject additionalConfig = new JsonObject().put("Hodor", "Hodor");
        JsonObject providerJson = new JsonObject().put("type", "JDBC");
        JsonObject json =
                new JsonObject().put("type", "HTDIGEST").mergeIn(additionalConfig).put("provider", providerJson);

        AuthProviderConfig apc = new AuthProviderConfig().setType(AuthProviderType.JDBC);
        AuthHandlerConfig expectedAHC = new AuthHandlerConfig().setType(HTDIGEST).setAdditionalConfig(additionalConfig)
                .setAuthProviderConfig(apc);
        assertThat(new AuthHandlerConfig(json)).isEqualTo(expectedAHC);
    }

    @Test
    @DisplayName("test getters and setters")
    void testGettersAndSetters() {
        AuthHandlerConfig ahc = new AuthHandlerConfig();

        assertThat(ahc.setType(HTDIGEST)).isSameInstanceAs(ahc);
        assertThat(ahc.getType()).isEqualTo(HTDIGEST);

        JsonObject additionalConfig = new JsonObject().put("Hodor", "Hodor");
        assertThat(ahc.setAdditionalConfig(additionalConfig)).isSameInstanceAs(ahc);
        assertThat(ahc.getAdditionalConfig()).isEqualTo(additionalConfig);

        AuthProviderConfig apc = new AuthProviderConfig().setType(AuthProviderType.JDBC);
        assertThat(ahc.setAuthProviderConfig(apc)).isSameInstanceAs(ahc);
        assertThat(ahc.getAuthProviderConfig()).isEqualTo(apc);
    }

    @Test
    @DisplayName("test hashcode and equals")
    @SuppressWarnings("PMD.EqualsNull")
    void hashCodeAndEquals() {
        AuthHandlerConfig ahc = new AuthHandlerConfig();
        AuthHandlerConfig ahc2 = new AuthHandlerConfig().setType(HTDIGEST);

        // hashcode
        assertThat(ahc.hashCode()).isEqualTo(new AuthHandlerConfig().hashCode());
        assertThat(ahc.hashCode()).isNotEqualTo(ahc2.hashCode());
        assertThat(ahc.hashCode()).isNotEqualTo(StringHelper.EMPTY.hashCode());

        // equals
        assertThat(ahc.equals(ahc)).isTrue();
        assertThat(ahc.equals(null)).isFalse();
        assertThat(ahc).isEqualTo(new AuthHandlerConfig());
        assertThat(ahc.equals(new Object())).isFalse();

        assertThat(ahc).isNotEqualTo(ahc2);
    }
}
