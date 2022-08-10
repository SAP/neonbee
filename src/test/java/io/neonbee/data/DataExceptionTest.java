package io.neonbee.data;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

class DataExceptionTest {

    private final DataException exception0 = new DataException(400);

    private final DataException exception1 = new DataException("Bad Response");

    private final DataException exception2 = new DataException(400, "Bad Response");

    private final DataException exception3 =
            new DataException(400, "Bad Response", Map.of("error", "This is an error."));

    private final JsonObject exceptionJsonObject = new JsonObject().put("message", "This is an error.");

    private final DataException exception4 =
            new DataException(400, "Bad Response", Map.of("error", exceptionJsonObject));

    private final JsonArray exceptionJsonArray = new JsonArray()
            .add(new JsonObject().put("message", "This is an error.")).add(new JsonObject().put("lang", "en"));

    private final DataException exception5 =
            new DataException(400, "Bad Response", Map.of("error", exceptionJsonArray));

    @Test
    @DisplayName("Test DataException getters")
    void testGetters() {
        assertThat(exception0.failureCode()).isEqualTo(400);
        assertThat(exception0.getMessage()).isEqualTo(null);
        assertThat(exception0.failureDetail()).isEqualTo(Map.of());

        assertThat(exception1.failureCode()).isEqualTo(-1);
        assertThat(exception1.getMessage()).isEqualTo("Bad Response");
        assertThat(exception1.failureDetail()).isEqualTo(Map.of());

        assertThat(exception2.failureCode()).isEqualTo(400);
        assertThat(exception2.getMessage()).isEqualTo("Bad Response");
        assertThat(exception2.failureDetail()).isEqualTo(Map.of());

        assertThat(exception3.failureCode()).isEqualTo(400);
        assertThat(exception3.getMessage()).isEqualTo("Bad Response");
        assertThat(exception3.failureDetail()).isEqualTo(Map.of("error", "This is an error."));

        assertThat(exception4.failureCode()).isEqualTo(400);
        assertThat(exception4.getMessage()).isEqualTo("Bad Response");
        assertThat(exception4.failureDetail()).isEqualTo(Map.of("error", exceptionJsonObject));

        assertThat(exception5.failureCode()).isEqualTo(400);
        assertThat(exception5.getMessage()).isEqualTo("Bad Response");
        assertThat(exception5.failureDetail()).isEqualTo(Map.of("error", exceptionJsonArray));

        assertThrows(NullPointerException.class, () -> new DataException(400, "Bad Response", null));
    }

    @Test
    @DisplayName("Test toString method")
    void testToString() {
        assertThat(exception0.toString()).isEqualTo("(400)");
        assertThat(exception1.toString()).isEqualTo("(-1) Bad Response");
        assertThat(exception2.toString()).isEqualTo("(400) Bad Response");
        assertThat(exception3.toString()).isEqualTo("(400) Bad Response");
    }

    @Test
    @DisplayName("Test hashCode and equals")
    void testHashCode() {
        DataException exceptionClone = new DataException(400, "Bad Response", Map.of("error", "This is an error."));
        DataException exceptionClone2 = new DataException(400, "Bad Response", Map.of());

        // hashCode
        assertThat(exception3.hashCode()).isEqualTo(exceptionClone.hashCode());
        assertThat(exception3.hashCode()).isNotEqualTo(exceptionClone2.hashCode());

        // equals
        assertThat(exception3.equals(exceptionClone)).isTrue();
        assertThat(exception3.equals(exceptionClone2)).isFalse();
        assertThat(exception2.equals(exceptionClone2)).isTrue();
    }
}
