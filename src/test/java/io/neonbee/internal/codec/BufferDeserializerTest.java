package io.neonbee.internal.codec;

import static com.google.common.truth.Truth.assertThat;

import java.io.IOException;

import org.json.JSONException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class BufferDeserializerTest {

    @Test
    @DisplayName("deserialization of a stream to buffer.")
    public void testDeserialization() throws IOException, JSONException {
        String json = "{\"type\":\"file\",\"content\":\"body\"}";
        ObjectMapper mapper = new ObjectMapper();
        BufferWrapper wrapper = mapper.readValue(json, BufferWrapper.class);
        assertThat(wrapper.getContent().toString()).isEqualTo("body");
    }
}
