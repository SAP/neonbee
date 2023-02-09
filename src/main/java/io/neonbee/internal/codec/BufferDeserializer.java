package io.neonbee.internal.codec;

import java.io.IOException;
import java.util.Iterator;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import io.vertx.core.buffer.Buffer;

@SuppressWarnings("checkstyle:MissingJavadocMethod")
public class BufferDeserializer extends StdDeserializer<Buffer> {
    private static final long serialVersionUID = 2689151954236213091L;

    public BufferDeserializer() {
        this(null);
    }

    public BufferDeserializer(Class<Buffer> clazz) {
        super(clazz);
    }

    @Override
    public Buffer deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
        Iterator<String> iterator = p.readValuesAs(String.class);
        if (iterator.hasNext()) {
            return Buffer.buffer(iterator.next());
        }
        return null;
    }
}
