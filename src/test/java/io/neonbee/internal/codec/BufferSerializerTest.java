package io.neonbee.internal.codec;

import java.io.IOException;

import org.json.JSONException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.buffer.Buffer;

class BufferSerializerTest {

    @Test
    @DisplayName("serialization of a buffer should produce the right JSON representation.")
    void testSerialization() throws IOException, JSONException {
        BufferWrapper wrapper = new BufferWrapper("file", Buffer.buffer("body"));
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(wrapper);
        JSONAssert.assertEquals(json, "{\"type\":\"file\",\"content\":\"body\"}", JSONCompareMode.LENIENT);
    }
}
