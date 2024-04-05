package io.neonbee.internal;

import static com.google.common.truth.Truth.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class WriteSafeRegistryTest {

    public static final String REGISTRY_NAME = "TEST_REGISTRY";

    @Test
    @DisplayName("register value in registry")
    void testRegister(Vertx vertx, VertxTestContext context) {
        WriteSafeRegistry<String> registry = new WriteSafeRegistry<>(vertx, REGISTRY_NAME);
        String key = "key";
        String value = "value";
        registry.register(key, value).compose(unused -> registry.get(key)).onSuccess(mapValue -> context.verify(() -> {
            assertThat(mapValue).containsExactly(value);
            context.completeNow();
        })).onFailure(context::failNow);
    }

    @Test
    @DisplayName("unregister value from registry")
    void unregister(Vertx vertx, VertxTestContext context) {
        WriteSafeRegistry<String> registry = new WriteSafeRegistry<>(vertx, REGISTRY_NAME);
        String key = "key";
        String value = "value";

        registry.register(key, value).compose(unused -> registry.unregister(key, "value2"))
                .compose(unused -> registry.get(key)).onSuccess(mapValue -> context.verify(() -> {
                    assertThat(mapValue).containsExactly(value);
                })).compose(unused -> registry.unregister(key, value)).compose(unused -> registry.get(key))
                .onSuccess(mapValue -> context.verify(() -> {
                    assertThat(mapValue).isEmpty();
                    context.completeNow();
                })).onFailure(context::failNow);
    }

    @Test
    @DisplayName("get value from registry")
    void get(Vertx vertx, VertxTestContext context) {
        String key = "key";
        String value = "value";

        WriteSafeRegistry<String> registry = new WriteSafeRegistry<>(vertx, REGISTRY_NAME);
        registry.register(key, value).compose(unused -> registry.get(key)).onSuccess(values -> context.verify(() -> {
            assertThat(values).isNotNull();
            assertThat(values).containsExactly(value);
            context.completeNow();
        })).onFailure(context::failNow);
    }
}
