package io.neonbee.internal.codec;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import io.vertx.core.buffer.Buffer;

@SuppressWarnings("checkstyle:MissingJavadocMethod")
public class BufferSerializer extends StdSerializer<Buffer> {
    private static final long serialVersionUID = 2689151954236213091L;

    public BufferSerializer() {
        this(null);
    }

    public BufferSerializer(Class<Buffer> clazz) {
        super(clazz);
    }

    @Override
    public void serialize(Buffer value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        gen.writeString(value.toString());
    }
}
