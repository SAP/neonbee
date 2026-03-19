package io.neonbee.internal.codec;

import java.nio.charset.StandardCharsets;

import io.neonbee.endpoint.odatav4.rawbatch.RawBatchDecision;
import io.neonbee.endpoint.odatav4.rawbatch.RawBatchResult;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageCodec;

/**
 * Message codec for {@link RawBatchResult} for event bus transmission.
 */
public class RawBatchResultMessageCodec implements MessageCodec<RawBatchResult, RawBatchResult> {

    /**
     * Create a new {@link RawBatchResultMessageCodec}.
     */
    public RawBatchResultMessageCodec() {
        // no initialization needed, however:
        // checkstyle suggests to create an empty constructor to explain the use of the class and
        // sonarcube is complaining if the constructor is empty and suggests to add (this) comment ;)
    }

    @Override
    public void encodeToWire(Buffer buffer, RawBatchResult result) {
        Buffer payload = result.buffer();
        if (result.hasBuffer() && payload != null) {
            buffer.appendInt(payload.length());
            buffer.appendBuffer(payload);
        } else {
            buffer.appendInt(-1);
        }
        RawBatchDecision decision = result.decision();
        if (decision != null) {
            byte[] nameBytes = decision.name().getBytes(StandardCharsets.UTF_8);
            buffer.appendInt(nameBytes.length);
            buffer.appendBytes(nameBytes);
        } else {
            buffer.appendInt(-1);
        }
    }

    @Override
    public RawBatchResult decodeFromWire(int position, Buffer buffer) {
        int pos = position;
        int bufferLen = buffer.getInt(pos);
        pos += Integer.BYTES;
        Buffer payload = bufferLen >= 0 ? buffer.getBuffer(pos, pos + bufferLen) : null;
        pos += bufferLen >= 0 ? bufferLen : 0;
        int nameLen = buffer.getInt(pos);
        pos += Integer.BYTES;
        RawBatchDecision decision = nameLen >= 0
                ? RawBatchDecision.valueOf(new String(buffer.getBytes(pos, pos + nameLen), StandardCharsets.UTF_8))
                : null;
        return payload != null ? RawBatchResult.buffer(payload) : RawBatchResult.decision(decision);
    }

    @Override
    public RawBatchResult transform(RawBatchResult result) {
        return result;
    }

    @Override
    public String name() {
        return "rawbatchresult";
    }

    @Override
    public byte systemCodecID() {
        return -1;
    }
}
