package io.neonbee.internal.codec;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.vertx.core.buffer.Buffer;

/**
 * This is just a test class for the serialization and deserialization test of Buffer.
 */
public class BufferWrapper {
    private String type;

    @JsonSerialize(using = BufferSerializer.class)
    @JsonDeserialize(using = BufferDeserializer.class)
    private Buffer content;

    public BufferWrapper() {}

    public BufferWrapper(String type, Buffer content) {
        this.type = type;
        this.content = content;
    }

    public String getType() {
        return type;
    }

    public Buffer getContent() {
        return content;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setContent(Buffer content) {
        this.content = content;
    }
}
