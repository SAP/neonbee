package io.neonbee.internal.codec;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.internal.verticle.LoggerConfigurationTest.LOGGER1;
import static io.neonbee.internal.verticle.LoggerConfigurationTest.LOGGER2;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.neonbee.internal.verticle.LoggerConfigurations;
import io.vertx.core.buffer.Buffer;

public class LoggerConfigurationMessageCodecTest {

    private final LoggerConfigurationsMessageCodec codec = new LoggerConfigurationsMessageCodec();

    private final LoggerConfigurations config = new LoggerConfigurations(List.of(LOGGER1, LOGGER2));

    @Test
    void encodeDecode() {
        Buffer buffer = Buffer.buffer();
        codec.encodeToWire(buffer, config);
        LoggerConfigurations decodeFromWire = codec.decodeFromWire(0, buffer);
        assertThat(decodeFromWire.getConfigurations()).containsExactlyElementsIn(config.getConfigurations());
    }

    @Test
    void transform() {
        assertThat(codec.transform(config).getConfigurations()).containsExactlyElementsIn(config.getConfigurations());
        assertThat(codec.transform(config)).isNotSameInstanceAs(config);
    }

    @Test
    void name() {
        assertThat(codec.name()).isEqualTo("loggerconfigurations");
    }

    @Test
    void systemCodecID() {
        assertThat(codec.systemCodecID()).isEqualTo(-1);
    }
}
