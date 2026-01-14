package io.neonbee.internal.buffer;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.internal.buffer.ImmutableBuffer.EMPTY;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.ByteBuffer;

import org.junit.jupiter.api.Test;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.internal.buffer.BufferInternal;

@SuppressWarnings("deprecation") // see https://github.com/SAP/neonbee/issues/387
class ImmutableBufferTest {
    @Test
    void testStaticConstructors() {
        // test assumptions made in JavaDoc
        assertThat(ImmutableBuffer.buffer()).isInstanceOf(ImmutableBuffer.class);
        assertThat(ImmutableBuffer.buffer()).isEqualTo(EMPTY);
        assertThat(ImmutableBuffer.buffer()).isSameInstanceAs(EMPTY);
        assertThat(ImmutableBuffer.buffer()).isSameInstanceAs(ImmutableBuffer.buffer());

        Buffer anyBuffer = Buffer.buffer("any");
        ImmutableBuffer anyImmutableBuffer = ImmutableBuffer.buffer(Buffer.buffer("anyOther"));
        assertThat(ImmutableBuffer.buffer(anyBuffer)).isInstanceOf(ImmutableBuffer.class);
        assertThat(ImmutableBuffer.buffer(anyImmutableBuffer)).isSameInstanceAs(anyImmutableBuffer);
        assertThat(ImmutableBuffer.buffer(anyBuffer)).isNotSameInstanceAs(anyBuffer);
        assertThat(ImmutableBuffer.buffer(anyBuffer)).isEqualTo(anyBuffer);

        assertThrows(NullPointerException.class, () -> ImmutableBuffer.buffer(null));
    }

    @Test
    void testGetBuffer() {
        BufferInternal anyBuffer = BufferInternal.buffer("any");
        Buffer anyImmutableBuffer = ImmutableBuffer.buffer(anyBuffer).getBuffer();
        assertThat(anyBuffer).isEqualTo(anyImmutableBuffer);
        assertThat(anyBuffer.getByteBuf().isReadOnly()).isFalse();
        assertThat(anyBuffer.getByteBuf().isReadOnly()).isTrue();
        assertThat(anyBuffer.getByteBuf().isWritable()).isFalse();
        assertThrows(IndexOutOfBoundsException.class, () -> anyBuffer.getByteBuf().writeInt(1));
    }

    @Test
    void testEmptyBuffer() {
        assertThat(EMPTY.length()).isEqualTo(0);
        assertThat(EMPTY.getBytes().length).isEqualTo(0);
    }

    @Test
    void testImmutable() {
        ImmutableBuffer buffer = new ImmutableBuffer();

        assertThrows(UnsupportedOperationException.class, () -> buffer.writeToBuffer(Buffer.buffer()));
        assertThrows(UnsupportedOperationException.class, () -> buffer.appendBuffer(Buffer.buffer()));
        assertThrows(UnsupportedOperationException.class, () -> buffer.appendBuffer(Buffer.buffer(), 1, 1));
        assertThrows(UnsupportedOperationException.class, () -> buffer.appendBytes(new byte[] { (byte) 1 }));
        assertThrows(UnsupportedOperationException.class, () -> buffer.appendBytes(new byte[] { (byte) 1 }, 1, 1));
        assertThrows(UnsupportedOperationException.class, () -> buffer.appendByte((byte) 1));
        assertThrows(UnsupportedOperationException.class, () -> buffer.appendUnsignedByte((short) 2));
        assertThrows(UnsupportedOperationException.class, () -> buffer.appendInt(3));
        assertThrows(UnsupportedOperationException.class, () -> buffer.appendIntLE(3));
        assertThrows(UnsupportedOperationException.class, () -> buffer.appendUnsignedInt(4L));
        assertThrows(UnsupportedOperationException.class, () -> buffer.appendUnsignedIntLE(4L));
        assertThrows(UnsupportedOperationException.class, () -> buffer.appendMedium(3));
        assertThrows(UnsupportedOperationException.class, () -> buffer.appendMediumLE(3));
        assertThrows(UnsupportedOperationException.class, () -> buffer.appendLong(5L));
        assertThrows(UnsupportedOperationException.class, () -> buffer.appendLongLE(5L));
        assertThrows(UnsupportedOperationException.class, () -> buffer.appendShort((short) 6));
        assertThrows(UnsupportedOperationException.class, () -> buffer.appendShortLE((short) 6));
        assertThrows(UnsupportedOperationException.class, () -> buffer.appendUnsignedShort(7));
        assertThrows(UnsupportedOperationException.class, () -> buffer.appendUnsignedShortLE(7));
        assertThrows(UnsupportedOperationException.class, () -> buffer.appendFloat((float) 8));
        assertThrows(UnsupportedOperationException.class, () -> buffer.appendDouble(9.1));
        assertThrows(UnsupportedOperationException.class, () -> buffer.appendString("foo", "UTF-8"));
        assertThrows(UnsupportedOperationException.class, () -> buffer.appendString("foo"));
        assertThrows(UnsupportedOperationException.class, () -> buffer.setByte(0, (byte) 1));
        assertThrows(UnsupportedOperationException.class, () -> buffer.setUnsignedByte(0, (short) 2));
        assertThrows(UnsupportedOperationException.class, () -> buffer.setInt(0, 3));
        assertThrows(UnsupportedOperationException.class, () -> buffer.setIntLE(0, 3));
        assertThrows(UnsupportedOperationException.class, () -> buffer.setUnsignedInt(0, 4L));
        assertThrows(UnsupportedOperationException.class, () -> buffer.setUnsignedIntLE(0, 4L));
        assertThrows(UnsupportedOperationException.class, () -> buffer.setMedium(0, 3));
        assertThrows(UnsupportedOperationException.class, () -> buffer.setMediumLE(0, 3));
        assertThrows(UnsupportedOperationException.class, () -> buffer.setLong(0, 5L));
        assertThrows(UnsupportedOperationException.class, () -> buffer.setLongLE(0, 5L));
        assertThrows(UnsupportedOperationException.class, () -> buffer.setDouble(0, 9.1));
        assertThrows(UnsupportedOperationException.class, () -> buffer.setFloat(0, (float) 8));
        assertThrows(UnsupportedOperationException.class, () -> buffer.setShort(0, (short) 6));
        assertThrows(UnsupportedOperationException.class, () -> buffer.setShortLE(0, (short) 6));
        assertThrows(UnsupportedOperationException.class, () -> buffer.setUnsignedShort(0, 7));
        assertThrows(UnsupportedOperationException.class, () -> buffer.setUnsignedShortLE(0, 7));
        assertThrows(UnsupportedOperationException.class, () -> buffer.setBuffer(0, Buffer.buffer()));
        assertThrows(UnsupportedOperationException.class, () -> buffer.setBuffer(0, Buffer.buffer(), 1, 1));
        assertThrows(UnsupportedOperationException.class, () -> buffer.setBytes(0, ByteBuffer.allocate(1)));
        assertThrows(UnsupportedOperationException.class, () -> buffer.setBytes(0, new byte[] { (byte) 11 }));
        assertThrows(UnsupportedOperationException.class, () -> buffer.setBytes(0, new byte[] { (byte) 11 }, 1, 1));
        assertThrows(UnsupportedOperationException.class, () -> buffer.setString(0, "foo"));
        assertThrows(UnsupportedOperationException.class, () -> buffer.setString(0, "foo", "UTF-8"));
    }

    @Test
    void testImmutableConstruction() {
        assertThrows(UnsupportedOperationException.class, () -> new ImmutableBuffer().setString(0, "keyX"));
        assertThrows(UnsupportedOperationException.class,
                () -> new ImmutableBuffer(Buffer.buffer("test")).setString(0, "keyX"));
    }

    @Test
    void testGetPrimitives() {
        ImmutableBuffer buffer = new ImmutableBuffer(Buffer.buffer(new byte[] { 1, 2, 3, 4, 5 }));
        assertThat(buffer.getByte(0)).isEqualTo(1);
        assertThat(buffer.getBytes()).isEqualTo(new byte[] { 1, 2, 3, 4, 5 });
        assertThat(buffer.getBytes(1, 4)).isEqualTo(new byte[] { 2, 3, 4 });
    }

    @Test
    void testMutableCopyIsMutable() {
        assertDoesNotThrow(() -> new ImmutableBuffer().mutableCopy().appendMedium(1));
        assertDoesNotThrow(() -> new ImmutableBuffer(Buffer.buffer("foo")).mutableCopy().appendString("bar"));
    }

    @Test
    void testCopyIsAlsoNotMutable() {
        assertThrows(UnsupportedOperationException.class, () -> new ImmutableBuffer().copy().setInt(0, 1));
    }

    @Test
    void testStandardMethods() {
        Buffer buffer = Buffer.buffer(new byte[] { 1, 2, 3, 4, 5 });
        ImmutableBuffer immutableBuffer = new ImmutableBuffer(buffer);

        assertThat(immutableBuffer.toString()).isEqualTo(buffer.toString());
        assertThat(immutableBuffer.hashCode()).isEqualTo(buffer.hashCode());

        assertThat(immutableBuffer).isEqualTo(buffer);
        assertThat(immutableBuffer).isEqualTo(new ImmutableBuffer(buffer));
        assertThat(immutableBuffer).isEqualTo(Buffer.buffer(new byte[] { 1, 2, 3, 4, 5 }));
        assertThat(immutableBuffer).isEqualTo(new ImmutableBuffer(Buffer.buffer(new byte[] { 1, 2, 3, 4, 5 })));
        assertThat(immutableBuffer).isNotEqualTo(EMPTY);
    }
}
