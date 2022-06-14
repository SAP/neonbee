package io.neonbee.internal.handler.factories;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;

import io.neonbee.NeonBee;
import io.neonbee.config.ServerConfig;
import io.neonbee.internal.handler.CorrelationIdHandler;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class CorrelationIdHandlerFactoryTest {

    @Test
    void testCreateHandler(VertxTestContext testContext) {

        try (MockedStatic<NeonBee> staticNeonBeeMock = mockStatic(NeonBee.class)) {
            NeonBee neonBeeMock = mock(NeonBee.class);
            when(neonBeeMock.getServerConfig()).thenReturn(new ServerConfig());
            staticNeonBeeMock.when(() -> NeonBee.get()).thenReturn(neonBeeMock);

            new CorrelationIdHandlerFactory().createHandler()
                    .onComplete(testContext.succeeding(instance -> testContext.verify(() -> {
                        assertThat(instance).isInstanceOf(CorrelationIdHandler.class);
                        testContext.completeNow();
                    })));
        }

    }
}
