package io.neonbee.internal.codec;

import static com.google.common.truth.Truth.assertThat;

import org.junit.jupiter.api.Test;

import io.neonbee.internal.buffer.ImmutableBuffer;
import io.vertx.core.buffer.Buffer;

class ImmutableBufferMessageCodecTest {
    private final ImmutableBufferMessageCodec codec = new ImmutableBufferMessageCodec();

    private final ImmutableBuffer immutableBuffer = ImmutableBuffer.buffer(Buffer.buffer("testFooBar"));

    @Test
    void testEncode() {
        Buffer buffer = Buffer.buffer();
        codec.encodeToWire(buffer, immutableBuffer);
        ImmutableBuffer decoded = codec.decodeFromWire(0, buffer);
        assertThat(decoded).isEqualTo(immutableBuffer);
    }

    @Test
    void testTransform() {
        assertThat(codec.transform(immutableBuffer)).isEqualTo(immutableBuffer);
        // special case for this codec: because it is immutable it MUST be the same instance!
        assertThat(codec.transform(immutableBuffer)).isSameInstanceAs(immutableBuffer);
    }

    @Test
    void testName() {
        assertThat(codec.name()).isEqualTo("immutablebuffer");
    }

    @Test
    void testSystemCodecID() {
        assertThat(codec.systemCodecID()).isEqualTo(-1);
    }
}
