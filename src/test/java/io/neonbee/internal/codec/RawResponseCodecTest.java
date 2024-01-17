package io.neonbee.internal.codec;

import static com.google.common.truth.Truth.assertThat;

import org.junit.jupiter.api.Test;

import io.neonbee.endpoint.raw.RawResponse;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.impl.headers.HeadersMultiMap;

class RawResponseCodecTest {

    private final RawResponseCodec codec = new RawResponseCodec();

    private final RawResponse rawResponse = new RawResponse(
            200,
            "OK",
            new HeadersMultiMap().add("header", "foo"),
            Buffer.buffer("test"));

    @Test
    void testEncode() {
        Buffer buffer = Buffer.buffer();
        codec.encodeToWire(buffer, rawResponse);
        RawResponse decoded = codec.decodeFromWire(0, buffer);
        assertThat(decoded.getStatusCode()).isEqualTo(rawResponse.getStatusCode());
        assertThat(decoded.getReasonPhrase()).isEqualTo(rawResponse.getReasonPhrase());
        assertThat(decoded.getHeaders().get("header")).isEqualTo(rawResponse.getHeaders().get("header"));
        assertThat(decoded.getBody().getBytes()).isEqualTo(rawResponse.getBody().getBytes());
    }

    @Test
    void testTransform() {
        assertThat(codec.transform(rawResponse)).isEqualTo(rawResponse);
        assertThat(codec.transform(rawResponse)).isSameInstanceAs(rawResponse);
    }

    @Test
    void testName() {
        assertThat(codec.name()).isEqualTo("RawResponseCodec");
    }

    @Test
    void testSystemCodecID() {
        assertThat(codec.systemCodecID()).isEqualTo(-1);
    }
}
