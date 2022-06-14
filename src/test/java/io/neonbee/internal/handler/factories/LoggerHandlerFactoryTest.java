package io.neonbee.internal.handler.factories;

import static com.google.common.truth.Truth.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.neonbee.internal.handler.LoggerHandler;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class LoggerHandlerFactoryTest {
    @Test
    void testCreateHandler(VertxTestContext testContext) {
        new LoggerHandlerFactory().createHandler()
                .onComplete(testContext.succeeding(instance -> testContext.verify(() -> {
                    assertThat(instance).isInstanceOf(LoggerHandler.class);
                    testContext.completeNow();
                })));
    }
}
