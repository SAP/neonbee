package io.neonbee.internal;

import static com.google.common.truth.Truth.assertThat;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.impl.NoStackTraceThrowable;
import io.vertx.core.json.JsonArray;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.Timeout;
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
            assertThat(mapValue).isEqualTo(new JsonArray().add(value));
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
                    assertThat(mapValue).isEqualTo(new JsonArray().add(value));
                })).compose(unused -> registry.unregister(key, value)).compose(unused -> registry.get(key))
                .onSuccess(mapValue -> context.verify(() -> {
                    assertThat(mapValue).isEqualTo(new JsonArray());
                    context.completeNow();
                })).onFailure(context::failNow);
    }

    @Test
    @DisplayName("get value from registry")
    void get(Vertx vertx, VertxTestContext context) {
        String key = "key";
        String value = "value";

        WriteSafeRegistry<String> registry = new WriteSafeRegistry<>(vertx, REGISTRY_NAME);
        registry.register(key, value).compose(unused -> registry.get(key)).onSuccess(jsonArray -> context.verify(() -> {
            assertThat(jsonArray).isNotNull();
            assertThat(jsonArray.contains(value)).isTrue();
            context.completeNow();
        })).onFailure(context::failNow);
    }

    @Test
    @DisplayName("test lock method")
    void lock(Vertx vertx, VertxTestContext context) {
        WriteSafeRegistry<String> registry = new WriteSafeRegistry<>(vertx, REGISTRY_NAME);

        Checkpoint checkpoints = context.checkpoint(4);
        String lockedname = "test-lock-key1";

        // execute the lockTest twice to make sure that you can acquire the lock multiple times
        lockTest(lockedname, context, registry, checkpoints)
                .compose(unused -> lockTest(lockedname, context, registry, checkpoints));
    }

    @Test
    // The used timeout for the lock is set to 10 seconds
    // @see io.vertx.core.shareddata.impl.SharedDataImpl#DEFAULT_LOCK_TIMEOUT
    @Timeout(value = 12, timeUnit = TimeUnit.SECONDS)
    @DisplayName("test acquire lock twice")
    void acquireLockTwice(Vertx vertx, VertxTestContext context) {
        WriteSafeRegistry<String> registry = new WriteSafeRegistry<>(vertx, REGISTRY_NAME);

        Checkpoint checkpoints = context.checkpoint(5);

        String lockedname = "test-lock-key2";
        registry.lock(lockedname, () -> {
            checkpoints.flag();
            // try to acquire the lock to make sure that it is locked
            return registry.lock(lockedname, () -> {
                context.failNow("should not be called because lock cannot be acquired");
                return Future.succeededFuture();
            })
                    .onSuccess(unused -> context.failNow("should not be successful"))
                    .onFailure(cause -> context.verify(() -> {
                        assertThat(cause).isInstanceOf(NoStackTraceThrowable.class);
                        assertThat(cause).hasMessageThat().isEqualTo("Timed out waiting to get lock");
                        checkpoints.flag();
                    }))
                    .recover(throwable -> Future.succeededFuture());
        })
                .onSuccess(unused -> checkpoints.flag())
                .onFailure(context::failNow)
                // execute the lockTest to make sure that you can acquire the lock again
                .compose(unused -> lockTest(lockedname, context, registry, checkpoints));
    }

    private static Future<Void> lockTest(String lockName, VertxTestContext context, WriteSafeRegistry<String> registry,
            Checkpoint checkpoints) {
        return registry.lock(lockName, () -> {
            checkpoints.flag();
            return Future.succeededFuture();
        })
                .onSuccess(unused -> checkpoints.flag())
                .onFailure(context::failNow);
    }

    @Test
    @DisplayName("test lock supplier retuning null")
    void lockNPE(Vertx vertx, VertxTestContext context) {
        WriteSafeRegistry<String> registry = new WriteSafeRegistry<>(vertx, REGISTRY_NAME);

        Checkpoint checkpoints = context.checkpoint(2);

        registry.lock("lockNPE", () -> {
            checkpoints.flag();
            return null;
        }).onSuccess(unused -> context.failNow("should not be successful"))
                .onFailure(cause -> context.verify(() -> {
                    assertThat(cause).isInstanceOf(NullPointerException.class);
                    checkpoints.flag();
                }));
    }

    @Test
    @DisplayName("test lock supplier throws exception")
    void lockISE(Vertx vertx, VertxTestContext context) {
        WriteSafeRegistry<String> registry = new WriteSafeRegistry<>(vertx, REGISTRY_NAME);

        Checkpoint checkpoints = context.checkpoint(2);

        registry.lock("lockISE", () -> {
            checkpoints.flag();
            throw new IllegalStateException("Illegal state");
        }).onSuccess(unused -> context.failNow("should not be successful"))
                .onFailure(cause -> context.verify(() -> {
                    assertThat(cause).isInstanceOf(IllegalStateException.class);
                    checkpoints.flag();
                }));
    }
}
