package io.neonbee.internal.codec;

import io.neonbee.internal.json.ImmutableJsonObject;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageCodec;

// TODO change to <JsonObject, ImmutableJsonObject> as soon as EventBus interface was changed to MessageCodec<? super T, ?> codec
public class ImmutableJsonObjectMessageCodec implements MessageCodec<ImmutableJsonObject, ImmutableJsonObject> {
    @Override
    public void encodeToWire(Buffer buffer, ImmutableJsonObject jsonObject) {
        Buffer encoded = jsonObject.toBuffer();
        buffer.appendInt(encoded.length());
        buffer.appendBuffer(encoded);
    }

    @Override
    public ImmutableJsonObject decodeFromWire(int pos, Buffer buffer) {
        int length = buffer.getInt(pos);
        int nextPos = pos + Integer.BYTES;
        return new ImmutableJsonObject(buffer.slice(nextPos, nextPos + length));
    }

    @Override
    public ImmutableJsonObject transform(ImmutableJsonObject jsonObject) {
        return jsonObject;
    }

    @Override
    public String name() {
        return "immutablejsonobject";
    }

    @Override
    public byte systemCodecID() {
        return -1;
    }
}
