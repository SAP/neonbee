package io.neonbee.config;

import static com.google.common.truth.Truth.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.neonbee.internal.helper.StringHelper;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

class CorsConfigTest {

    @Test
    void testJSONConstructor() {
        JsonObject corsConfigJson = new JsonObject();
        corsConfigJson.put("enabled", true);
        corsConfigJson.put("origins", new JsonArray().add("http://foo.bar"));
        corsConfigJson.put("relativeOrigins", new JsonArray().add("^http\\://.*.foo.bar$"));
        corsConfigJson.put("allowedMethods", new JsonArray().add("GET").add("PUT"));
        corsConfigJson.put("allowedHeaders", new JsonArray().add("foo").add("bar"));
        corsConfigJson.put("exposedHeaders", new JsonArray().add("foobar"));
        corsConfigJson.put("maxAgeSeconds", 1337);
        corsConfigJson.put("allowCredentials", true);

        CorsConfig cc = new CorsConfig(corsConfigJson);
        assertThat(cc.getOrigins()).containsExactly("http://foo.bar");
        assertThat(cc.getRelativeOrigins()).containsExactly("^http\\://.*.foo.bar$");
        assertThat(cc.isEnabled()).isTrue();
        assertThat(cc.getAllowedMethods()).containsExactly("GET", "PUT");
        assertThat(cc.getAllowedHeaders()).containsExactly("foo", "bar");
        assertThat(cc.getExposedHeaders()).containsExactly("foobar");
        assertThat(cc.getMaxAgeSeconds()).isEqualTo(1337);
        assertThat(cc.getAllowCredentials()).isTrue();
    }

    @Test
    void testToJSON() {
        JsonObject expectedJson = new JsonObject();
        expectedJson.put("enabled", true);
        expectedJson.put("origins", new JsonArray().add("http://foo.bar"));
        expectedJson.put("relativeOrigins", new JsonArray().add("^http\\://.*.foo.bar$"));
        expectedJson.put("allowedMethods", new JsonArray().add("GET"));
        expectedJson.put("allowedHeaders", new JsonArray().add("foo"));
        expectedJson.put("exposedHeaders", new JsonArray().add("foobar"));
        expectedJson.put("maxAgeSeconds", 1337);
        expectedJson.put("allowCredentials", true);

        CorsConfig cc = new CorsConfig().setEnabled(true).setOrigins(List.of("http://foo.bar"))
                .setRelativeOrigins(List.of("^http\\://.*.foo.bar$")).setAllowedHeaders(Set.of("foo"))
                .setAllowCredentials(true).setMaxAgeSeconds(1337).setAllowedMethods(Set.of("GET"))
                .setExposedHeaders(Set.of("foobar"));

        assertThat(cc.toJson()).containsExactlyElementsIn(expectedJson);
    }

    @Test
    void testDefaults() {
        CorsConfig cc = new CorsConfig();
        assertThat(cc.isEnabled()).isFalse();
        assertThat(cc.getOrigins()).isNull();
        assertThat(cc.getRelativeOrigins()).isNull();
        assertThat(cc.getAllowedMethods()).isNull();
        assertThat(cc.getAllowedHeaders()).isNull();
        assertThat(cc.getExposedHeaders()).isNull();
        assertThat(cc.getMaxAgeSeconds()).isEqualTo(-1);
        assertThat(cc.getAllowCredentials()).isFalse();
    }

    @Test
    void testDisabledWhenNoOriginIsSet() {
        CorsConfig cc = new CorsConfig().setEnabled(true);
        assertThat(cc.isEnabled()).isFalse();

        CorsConfig cc2 = new CorsConfig().setEnabled(true).setOrigins(List.of("http://foo.bar"));
        assertThat(cc2.isEnabled()).isTrue();

        CorsConfig cc3 = new CorsConfig().setEnabled(true).setRelativeOrigins(List.of("^http\\://.*.foo.bar$"));
        assertThat(cc3.isEnabled()).isTrue();
    }

    @Test
    void testSetters() {
        CorsConfig cc = new CorsConfig();

        cc.setOrigins(List.of("http://foo.bar"));
        assertThat(cc.getOrigins()).containsExactly("http://foo.bar");

        cc.setRelativeOrigins(List.of("^http\\://.*.foo.bar$"));
        assertThat(cc.getRelativeOrigins()).containsExactly("^http\\://.*.foo.bar$");

        cc.setEnabled(true);
        assertThat(cc.isEnabled()).isTrue();

        cc.setAllowedMethods(Set.of("GET", "PUT"));
        assertThat(cc.getAllowedMethods()).containsExactly("GET", "PUT");

        cc.setAllowedHeaders(Set.of("foo", "bar"));
        assertThat(cc.getAllowedHeaders()).containsExactly("foo", "bar");

        cc.setExposedHeaders(Set.of("foobar"));
        assertThat(cc.getExposedHeaders()).containsExactly("foobar");

        cc.setMaxAgeSeconds(1337);
        assertThat(cc.getMaxAgeSeconds()).isEqualTo(1337);

        cc.setAllowCredentials(true);
        assertThat(cc.getAllowCredentials()).isTrue();
    }

    @Test
    @DisplayName("test hashcode and equals")
    @SuppressWarnings("PMD.EqualsNull")
    void hashCodeAndEquals() {
        CorsConfig cc = new CorsConfig();
        CorsConfig cc2 = new CorsConfig().setMaxAgeSeconds(1337);

        // hashcode
        assertThat(cc.hashCode()).isEqualTo(new CorsConfig().hashCode());
        assertThat(cc.hashCode()).isNotEqualTo(cc2.hashCode());
        assertThat(cc.hashCode()).isNotEqualTo(StringHelper.EMPTY.hashCode());

        // equals
        assertThat(cc.equals(cc)).isTrue();
        assertThat(cc.equals(null)).isFalse();
        assertThat(cc).isEqualTo(new CorsConfig());
        assertThat(cc.equals(new Object())).isFalse();

        assertThat(cc).isNotEqualTo(cc2);
    }
}
