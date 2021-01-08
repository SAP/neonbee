package io.neonbee.internal.codec;

import io.neonbee.internal.verticle.LoggerConfigurations;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageCodec;
import io.vertx.core.json.JsonObject;

public class LoggerConfigurationsMessageCodec implements MessageCodec<LoggerConfigurations, LoggerConfigurations> {
    @Override
    public void encodeToWire(Buffer buffer, LoggerConfigurations config) {
        JsonObject.mapFrom(config).writeToBuffer(buffer);
    }

    @Override
    public LoggerConfigurations decodeFromWire(int position, Buffer buffer) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.readFromBuffer(position, buffer);
        return jsonObject.mapTo(LoggerConfigurations.class);
    }

    @Override
    public LoggerConfigurations transform(LoggerConfigurations config) {
        return config.copy();
    }

    @Override
    public String name() {
        return "loggerconfigurations";
    }

    @Override
    public byte systemCodecID() {
        return -1;
    }
}
