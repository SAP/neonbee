package io.neonbee.internal.codec;

import static com.google.common.truth.Truth.assertThat;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.neonbee.data.DataException;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

class DataExceptionMessageCodecTest {
    private final DataExceptionMessageCodec codec = new DataExceptionMessageCodec();

    private final DataException exception0 = new DataException(400, null);

    private final DataException exception1 = new DataException(400, "Bad Response");

    private final DataException exception2 =
            new DataException(400, "Bad Response", Map.of("error", "This is a bad response."));

    private static final JsonObject FAILURE_OBJECT = new JsonObject().put("code", new JsonArray()
            .add(new JsonObject().put("message", "This is a bad response2")).add(new JsonObject().put("lang", "en")));

    private final DataException exception3 = new DataException(400, "Bad Response", Map.of("error", FAILURE_OBJECT));

    private static final JsonArray FAILURE_ARRAY =
            new JsonArray().add(FAILURE_OBJECT).add(new JsonObject().put("key", "value"));

    private final DataException exception4 = new DataException(400, "Bad Response", Map.of("error", FAILURE_ARRAY));

    @Test
    @DisplayName("enocde/decode for data exception with failure code")
    void testEncodeDecodeDataExceptionWithFailureCode() {
        Buffer buffer = Buffer.buffer();
        codec.encodeToWire(buffer, exception0);
        DataException decoded = codec.decodeFromWire(0, buffer);
        assertThat(decoded).isEqualTo(exception0);
    }

    @Test
    @DisplayName("enocde/decode for data exception with failure code and message")
    void testEncodeDecodeDataExceptionWithFailureCodeAndMessage() {
        Buffer buffer = Buffer.buffer();
        codec.encodeToWire(buffer, exception1);
        DataException decoded = codec.decodeFromWire(0, buffer);
        assertThat(decoded).isEqualTo(exception1);
    }

    @Test
    @DisplayName("enocde/decode for data exception: failureDetail as String")
    void testEncodeDecodeWithStringFailureDetail() {
        Buffer buffer = Buffer.buffer();
        codec.encodeToWire(buffer, exception2);
        DataException decoded = codec.decodeFromWire(0, buffer);
        assertThat(decoded).isEqualTo(exception2);
    }

    @Test
    @DisplayName("enocde/decode for data exception: failureDetail as JsonObject")
    void testEncodeDecodeWithJsonObjectFailureDetail() {
        Buffer buffer = Buffer.buffer();
        codec.encodeToWire(buffer, exception3);
        DataException decoded = codec.decodeFromWire(0, buffer);
        assertThat(decoded).isEqualTo(exception3);
    }

    @Test
    @DisplayName("enocde/decode for data exception: failureDetail as JsonArray")
    void testEncodeDecodeWithJsonArrayFailureDetail() {
        Buffer buffer = Buffer.buffer();
        codec.encodeToWire(buffer, exception4);
        DataException decoded = codec.decodeFromWire(0, buffer);
        assertThat(decoded).isEqualTo(exception4);
    }

    @Test
    void testTransform() {
        assertThat(codec.transform(exception0)).isEqualTo(exception0);
        assertThat(codec.transform(exception1)).isEqualTo(exception1);
        assertThat(codec.transform(exception2)).isEqualTo(exception2);
    }

    @Test
    void testName() {
        assertThat(codec.name()).isEqualTo("dataexception");
    }

    @Test
    void testSystemCodecID() {
        assertThat(codec.systemCodecID()).isEqualTo(-1);
    }
}
