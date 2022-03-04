package io.neonbee.internal.buffer;

import static io.neonbee.internal.buffer.ImmutableBuffer.EMPTY;
import static io.netty.buffer.Unpooled.wrappedUnmodifiableBuffer;

import java.util.Arrays;

import io.netty.buffer.ByteBuf;
import io.vertx.core.buffer.Buffer;

public final class CompositeBuffer {
    /**
     * Wrap the given {@link Buffer}(s) in an unmodifiable {@link Buffer}. If more than one {@link Buffer} is supplied,
     * the resulting composite {@link Buffer} represents a concatenation of all supplied buffers. If any of the
     * underlying buffers is modified, also the returned composite buffer is.
     *
     * The returned {@link ByteBuf} wraps the provided array directly, and so should not be subsequently modified.
     *
     * @param buffers Any number of buffers to create a composite of
     * @return a resulting composite buffer as a view on all passed buffers
     */
    public static Buffer buffer(Buffer... buffers) {
        switch (buffers.length) {
        case 0:
            return EMPTY;
        case 1:
            return ImmutableBuffer.buffer(buffers[0]);
        default:
            return new ImmutableBuffer(
                    wrappedUnmodifiableBuffer(Arrays.stream(buffers).map(Buffer::getByteBuf).toArray(ByteBuf[]::new))
                            .asReadOnly());
        }
    }
}
