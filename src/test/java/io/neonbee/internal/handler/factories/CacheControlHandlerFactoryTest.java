package io.neonbee.internal.handler.factories;

import static com.google.common.truth.Truth.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.neonbee.internal.handler.CacheControlHandler;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class CacheControlHandlerFactoryTest {

    @Test
    void testCreateHandler(VertxTestContext testContext) {
        new CacheControlHandlerFactory().createHandler()
                .onComplete(testContext.succeeding(instance -> testContext.verify(() -> {
                    assertThat(instance).isInstanceOf(CacheControlHandler.class);
                    testContext.completeNow();
                })));
    }
}
