package io.neonbee.internal.buffer;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.internal.buffer.ImmutableBuffer.EMPTY;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.ByteBuffer;

import org.junit.jupiter.api.Test;

import io.vertx.core.buffer.Buffer;

class CompositeBufferTest {
    @Test
    void testStaticConstructor() {
        Buffer buffer1 = Buffer.buffer("foo");
        Buffer buffer2 = Buffer.buffer("bar");
        Buffer buffer3 = Buffer.buffer("baz");

        assertThat(CompositeBuffer.buffer(new Buffer[0])).isSameInstanceAs(EMPTY);

        Buffer compositeBuffer1 = CompositeBuffer.buffer(new Buffer[] { buffer1 });
        assertThat(compositeBuffer1).isEqualTo(buffer1);
        assertThat(compositeBuffer1).isNotSameInstanceAs(buffer1);
        assertThat(compositeBuffer1).isInstanceOf(ImmutableBuffer.class);

        ImmutableBuffer immutableBuffer1 = (ImmutableBuffer) compositeBuffer1;
        compositeBuffer1 = CompositeBuffer.buffer(new Buffer[] { immutableBuffer1 });
        assertThat(compositeBuffer1).isSameInstanceAs(immutableBuffer1);

        Buffer buffer12 = CompositeBuffer.buffer(buffer1, buffer2);
        assertThat(buffer12.length()).isEqualTo(buffer1.length() + buffer2.length());
        assertThat(buffer12.toString()).isEqualTo(buffer1.toString() + buffer2.toString());

        Buffer buffer123 = CompositeBuffer.buffer(buffer1, buffer2, buffer3);
        assertThat(buffer123.getClass()).isEqualTo(ImmutableBuffer.class);
        assertThat(buffer123.toString()).isEqualTo("foobarbaz");

    }

    @Test
    void testImmutable() {
        Buffer buffer = CompositeBuffer.buffer(Buffer.buffer("foo"), Buffer.buffer("bar"));

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
}
