package io.neonbee.internal.codec;

import io.neonbee.data.DataQuery;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageCodec;
import io.vertx.core.json.JsonObject;

public class DataQueryMessageCodec implements MessageCodec<DataQuery, DataQuery> {
    /**
     * Create a new {@link DataQueryMessageCodec}.
     */
    public DataQueryMessageCodec() {
        // no initialization needed, however:
        // checkstyle suggests to create an empty constructor to explain the use of the class and
        // sonarcube is complaining if the constructor is empty and suggests to add (this) comment ;)
    }

    @Override
    public void encodeToWire(Buffer buffer, DataQuery query) {
        JsonObject.mapFrom(query).writeToBuffer(buffer);
    }

    @Override
    public DataQuery decodeFromWire(int position, Buffer buffer) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.readFromBuffer(position, buffer);
        return jsonObject.mapTo(DataQuery.class);
    }

    @Override
    public DataQuery transform(DataQuery query) {
        return query.copy();
    }

    @Override
    public String name() {
        return "dataquery";
    }

    @Override
    public byte systemCodecID() {
        return -1;
    }
}
