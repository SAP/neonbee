package io.neonbee.internal.codec;

import io.neonbee.endpoint.raw.RawResponse;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageCodec;
import io.vertx.core.http.impl.headers.HeadersMultiMap;
import io.vertx.core.json.JsonObject;

/**
 * A codec for {@link RawResponse} objects.
 */
public class RawResponseCodec implements MessageCodec<RawResponse, RawResponse> {

    @Override
    public void encodeToWire(Buffer buffer, RawResponse response) {
        JsonObject json = new JsonObject();
        json.put("statusCode", response.getStatusCode());
        json.put("reasonPhrase", response.getReasonPhrase());
        json.put("headers", multiMapToJson(response.getHeaders()));
        json.put("body", response.getBody().getBytes());

        Buffer jsonBuffer = json.toBuffer();
        buffer.appendInt(jsonBuffer.length());
        buffer.appendBuffer(jsonBuffer);
    }

    @Override
    public RawResponse decodeFromWire(int position, Buffer buffer) {
        int length = buffer.getInt(position);
        int nexPosition = position + Integer.BYTES;
        JsonObject json = new JsonObject(buffer.slice(nexPosition, nexPosition + length));

        return new RawResponse(
                json.getInteger("statusCode"),
                json.getString("reasonPhrase"),
                jsonToMultiMap(json.getJsonObject("headers")),
                Buffer.buffer(json.getBinary("body")));
    }

    @Override
    public RawResponse transform(RawResponse response) {
        return response;
    }

    @Override
    public String name() {
        return RawResponse.class.getSimpleName() + "Codec";
    }

    @Override
    public byte systemCodecID() {
        return -1; // User codec
    }

    /**
     * Converts a {@link MultiMap} to a {@link JsonObject}.
     *
     * @param multiMap the {@link MultiMap} to convert
     * @return the {@link JsonObject}
     */
    private JsonObject multiMapToJson(MultiMap multiMap) {
        JsonObject json = new JsonObject();
        multiMap.forEach(entry -> json.put(entry.getKey(), entry.getValue()));
        return json;
    }

    /**
     * Converts a {@link JsonObject} to a {@link MultiMap}.
     *
     * @param json the {@link JsonObject} to convert
     * @return the {@link MultiMap}
     */
    private MultiMap jsonToMultiMap(JsonObject json) {
        MultiMap multiMap = new HeadersMultiMap();
        json.forEach(entry -> multiMap.add(entry.getKey(), entry.getValue().toString()));
        return multiMap;
    }
}
