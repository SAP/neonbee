package io.neonbee.internal.handler.factories;

import static com.google.common.truth.Truth.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class DisallowingFileUploadBodyHandlerFactoryTest {
    @Test
    void testCreateHandler(VertxTestContext testContext) {
        new DisallowingFileUploadBodyHandlerFactory().createHandler()
                .onComplete(testContext.succeeding(instance -> testContext.verify(() -> {
                    assertThat(instance).isInstanceOf(BodyHandler.class);
                    testContext.completeNow();
                })));
    }
}
