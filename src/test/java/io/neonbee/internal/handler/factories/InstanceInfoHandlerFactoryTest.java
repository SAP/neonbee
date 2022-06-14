package io.neonbee.internal.handler.factories;

import static com.google.common.truth.Truth.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.neonbee.internal.handler.InstanceInfoHandler;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class InstanceInfoHandlerFactoryTest {
    @Test
    void testCreateHandler(VertxTestContext testContext) {
        new InstanceInfoHandlerFactory().createHandler()
                .onComplete(testContext.succeeding(instance -> testContext.verify(() -> {
                    assertThat(instance).isInstanceOf(InstanceInfoHandler.class);
                    testContext.completeNow();
                })));
    }
}
