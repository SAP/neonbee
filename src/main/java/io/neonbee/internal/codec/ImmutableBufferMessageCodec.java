package io.neonbee.internal.codec;

import io.neonbee.internal.buffer.ImmutableBuffer;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageCodec;

// TODO change to <Buffer, ImmutableBuffer> as soon as EventBus interface was changed to MessageCodec<? super T, ?> codec
public class ImmutableBufferMessageCodec implements MessageCodec<ImmutableBuffer, ImmutableBuffer> {
    @Override
    public void encodeToWire(Buffer buffer, ImmutableBuffer b) {
        buffer.appendInt(b.length());
        buffer.appendBuffer(b.getBuffer());
    }

    @Override
    // reason to suppress warnings here: best-practice to do it like this in codecs
    @SuppressWarnings({ "PMD.AvoidReassigningParameters", "checkstyle:ParameterAssignment", "checkstyle:magicnumber" })
    public ImmutableBuffer decodeFromWire(int pos, Buffer buffer) {
        int length = buffer.getInt(pos);
        pos += 4;
        return ImmutableBuffer.buffer(buffer.getBuffer(pos, pos + length));
    }

    @Override
    public ImmutableBuffer transform(ImmutableBuffer b) {
        return b;
    }

    @Override
    public String name() {
        return "immutablebuffer";
    }

    @Override
    public byte systemCodecID() {
        return -1;
    }
}
