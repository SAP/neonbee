package io.neonbee.internal.codec;

import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import io.neonbee.data.DataException;
import io.netty.util.CharsetUtil;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageCodec;
import io.vertx.core.json.JsonObject;

public class DataExceptionMessageCodec implements MessageCodec<DataException, DataException> {

    @Override
    public void encodeToWire(Buffer buffer, DataException exception) {
        buffer.appendInt(exception.failureCode());

        String msgEncoded = exception.getMessage();
        if (msgEncoded != null) {
            byte[] msgBytes = msgEncoded.getBytes(CharsetUtil.UTF_8);
            buffer.appendInt(msgBytes.length);
            buffer.appendBytes(msgBytes);
        } else {
            buffer.appendInt(-1);
        }

        Buffer failureDetailEncoded = new JsonObject(exception.failureDetail()).toBuffer();
        buffer.appendInt(failureDetailEncoded.length());
        buffer.appendBuffer(failureDetailEncoded);
    }

    @Override
    public DataException decodeFromWire(int pos, Buffer buffer) {
        int tmpPos = pos;
        int failureCode = buffer.getInt(tmpPos);
        tmpPos += Integer.BYTES;

        int msgLength = buffer.getInt(tmpPos);
        tmpPos += Integer.BYTES;
        String message =
                msgLength != -1 ? new String(buffer.getBytes(tmpPos, tmpPos + msgLength), CharsetUtil.UTF_8) : null;
        tmpPos += msgLength != -1 ? msgLength : 0;

        int failureDetailLength = buffer.getInt(tmpPos);
        tmpPos += Integer.BYTES;

        Buffer buff = buffer.getBuffer(tmpPos, tmpPos + failureDetailLength);
        Map<String, Object> failureDetail =
                buff.toJsonObject().stream().collect(Collectors.toMap(Entry::getKey, Entry::getValue));

        return new DataException(failureCode, message, failureDetail);
    }

    @Override
    public DataException transform(DataException exception) {
        return exception;
    }

    @Override
    public String name() {
        return "dataexception";
    }

    @Override
    public byte systemCodecID() {
        return -1;
    }

}
