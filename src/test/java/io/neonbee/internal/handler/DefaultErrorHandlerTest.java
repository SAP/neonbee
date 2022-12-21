package io.neonbee.internal.handler;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.neonbee.NeonBee;
import io.neonbee.config.ServerConfig;
import io.vertx.core.Vertx;
import io.vertx.core.file.FileSystemException;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class DefaultErrorHandlerTest {

    @Test
    void testGetErrorHandlerDefault(Vertx vertx, VertxTestContext testContext) throws Exception {
        NeonBee neonBee = mock(NeonBee.class);
        ServerConfig config = mock(ServerConfig.class);
        when(config.getErrorHandlerTemplate()).thenReturn("Hodor");
        when(neonBee.getServerConfig()).thenReturn(config);
        when(neonBee.getVertx()).thenReturn(vertx);

        new DefaultErrorHandler().initialize(neonBee).onComplete(testContext.failing(t -> testContext.verify(() -> {
            assertThat(t).isInstanceOf(FileSystemException.class);
            assertThat(t).hasMessageThat().contains("Hodor");
            testContext.completeNow();
        })));
    }
}
