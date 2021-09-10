package io.neonbee.internal.codec;

import static com.google.common.truth.Truth.assertThat;

import org.junit.jupiter.api.Test;

import io.neonbee.internal.json.ImmutableJsonArray;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

class ImmutableJsonArrayMessageCodecTest {
    private final ImmutableJsonArrayMessageCodec codec = new ImmutableJsonArrayMessageCodec();

    private final ImmutableJsonArray jsonArray =
            new ImmutableJsonArray(new JsonArray().add("test").add(1).add(new JsonObject().put("foo", "bar")));

    @Test
    void testEncode() {
        Buffer buffer = Buffer.buffer();
        codec.encodeToWire(buffer, jsonArray);
        ImmutableJsonArray decoded = codec.decodeFromWire(0, buffer);
        assertThat(decoded).isEqualTo(jsonArray);
    }

    @Test
    void testTransform() {
        assertThat(codec.transform(jsonArray)).isEqualTo(jsonArray);
        // special case for this codec: because it is immutable it MUST be the same instance!
        assertThat(codec.transform(jsonArray)).isSameInstanceAs(jsonArray);
    }

    @Test
    void testName() {
        assertThat(codec.name()).isEqualTo("immutablejsonarray");
    }

    @Test
    void testSystemCodecID() {
        assertThat(codec.systemCodecID()).isEqualTo(-1);
    }
}
