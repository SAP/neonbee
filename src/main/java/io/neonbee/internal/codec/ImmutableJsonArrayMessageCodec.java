package io.neonbee.internal.codec;

import io.neonbee.internal.json.ImmutableJsonArray;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageCodec;

// TODO change to <JsonArray, ImmutableJsonObject> as soon as EventBus interface was changed to MessageCodec<? super T, ?> codec
public class ImmutableJsonArrayMessageCodec implements MessageCodec<ImmutableJsonArray, ImmutableJsonArray> {
    @Override
    public void encodeToWire(Buffer buffer, ImmutableJsonArray jsonArray) {
        Buffer encoded = jsonArray.toBuffer();
        buffer.appendInt(encoded.length());
        buffer.appendBuffer(encoded);
    }

    @Override
    public ImmutableJsonArray decodeFromWire(int position, Buffer buffer) {
        int temporaryPosition = position;
        int length = buffer.getInt(temporaryPosition);
        temporaryPosition += Integer.BYTES;
        return new ImmutableJsonArray(buffer.slice(temporaryPosition, temporaryPosition + length));
    }

    @Override
    public ImmutableJsonArray transform(ImmutableJsonArray jsonArray) {
        return jsonArray;
    }

    @Override
    public String name() {
        return "immutablejsonarray";
    }

    @Override
    public byte systemCodecID() {
        return -1;
    }
}
