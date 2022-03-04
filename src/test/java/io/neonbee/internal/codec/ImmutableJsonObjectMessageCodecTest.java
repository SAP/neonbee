package io.neonbee.internal.codec;

import static com.google.common.truth.Truth.assertThat;

import org.junit.jupiter.api.Test;

import io.neonbee.internal.json.ImmutableJsonObject;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

class ImmutableJsonObjectMessageCodecTest {
    private final ImmutableJsonObjectMessageCodec codec = new ImmutableJsonObjectMessageCodec();

    private final ImmutableJsonObject jsonObject = new ImmutableJsonObject(
            new JsonObject().put("foo", "bar").put("test", 1).put("arr", new JsonArray().add("foo").add("2")));

    @Test
    void testEncode() {
        Buffer buffer = Buffer.buffer();
        codec.encodeToWire(buffer, jsonObject);
        ImmutableJsonObject decoded = codec.decodeFromWire(0, buffer);
        assertThat(decoded).isEqualTo(jsonObject);
    }

    @Test
    void testTransform() {
        assertThat(codec.transform(jsonObject)).isEqualTo(jsonObject);
        // special case for this codec: because it is immutable it MUST be the same instance!
        assertThat(codec.transform(jsonObject)).isSameInstanceAs(jsonObject);
    }

    @Test
    void testName() {
        assertThat(codec.name()).isEqualTo("immutablejsonobject");
    }

    @Test
    void testSystemCodecID() {
        assertThat(codec.systemCodecID()).isEqualTo(-1);
    }
}
