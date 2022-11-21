package io.neonbee.internal.codec;

import static com.google.common.truth.Truth.assertThat;

import java.util.List;
import java.util.Map;

import org.json.JSONException;
import org.junit.jupiter.api.Test;

import io.neonbee.data.DataAction;
import io.neonbee.data.DataQuery;
import io.vertx.core.buffer.Buffer;

class DataQueryMessageCodecTest {
    private final DataQueryMessageCodec codec = new DataQueryMessageCodec();

    @SuppressWarnings("deprecation")
    private final DataQuery query = new DataQuery(DataAction.UPDATE, "uri", "query1=value",
            Map.of("header1", List.of("value1")), Buffer.buffer("body"));

    @Test
    void testEncode() throws JSONException {
        Buffer buffer = Buffer.buffer();
        codec.encodeToWire(buffer, query);
        DataQuery decoded = codec.decodeFromWire(0, buffer);
        assertThat(decoded).isEqualTo(query);
    }

    @Test
    void testTransform() {
        assertThat(codec.transform(query)).isEqualTo(query);
        assertThat(codec.transform(query)).isNotSameInstanceAs(query);
    }

    @Test
    void testName() {
        assertThat(codec.name()).isEqualTo("dataquery");
    }

    @Test
    void testSystemCodecID() {
        assertThat(codec.systemCodecID()).isEqualTo(-1);
    }
}
