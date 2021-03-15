package io.neonbee.hook.internal;

import static com.google.common.truth.Truth.assertThat;

import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.neonbee.NeonBee;
import io.neonbee.hook.Hook;
import io.neonbee.hook.HookContext;
import io.neonbee.hook.HookRegistration;
import io.neonbee.hook.HookType;
import io.neonbee.internal.BasicJar;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class DefaultHookRegistryTest {
    private static final String CORRELATION_ID = "bliblablub";

    private DefaultHookRegistry hookRegistry;

    private Class<?> classWithValidHook;

    @BeforeEach
    void setup(Vertx vertx) throws Exception {
        hookRegistry = new DefaultHookRegistry(vertx);

        BasicJar jarWithHookAnnotation =
                new HookClassTemplate(HookClassTemplate.VALID_HOOK_TEMPLATE, "HodorHook", "hook")
                        .setMethodAnnotation("@Hook(HookType.ONCE_PER_REQUEST)").asJar();
        @SuppressWarnings("resource")
        ClassLoader loader =
                new URLClassLoader(jarWithHookAnnotation.writeToTempURL(), ClassLoader.getSystemClassLoader());

        classWithValidHook = loader.loadClass("hook.HodorHook");
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("Check that hook from passed object were loaded correct")
    void registerInstanceHooksSuccessTest(VertxTestContext testContext) throws Exception {
        Object instanceWithHook = classWithValidHook.getConstructor().newInstance();
        Method hookMethod = classWithValidHook.getMethod("doNothing", NeonBee.class, HookContext.class, Promise.class);
        HookRegistration expectedRegistration =
                new DefaultHookRegistration(hookRegistry, instanceWithHook, hookMethod, HookType.ONCE_PER_REQUEST);

        hookRegistry.registerInstanceHooks(instanceWithHook, CORRELATION_ID)
                .onComplete(testContext.succeeding(hookRegistrations -> testContext.verify(() -> {
                    assertThat(hookRegistrations).hasSize(1);
                    hookRegistrations.stream().findFirst().ifPresentOrElse(current -> {
                        assertThat(current.getName()).isEqualTo(expectedRegistration.getName());
                        assertThat(current.getType()).isEqualTo(expectedRegistration.getType());
                    }, () -> testContext.failNow(new RuntimeException("Hook registration was not found")));
                    testContext.completeNow();
                })));
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("Check that hook from passed object were loaded correct")
    void registerInstanceHooksFailTest(VertxTestContext testContext) throws Exception {
        BasicJar jarWithHookAnnotation =
                new HookClassTemplate(HookClassTemplate.INVALID_HOOK_TEMPLATE, "InvalidHodorHook", "hook")
                        .setMethodAnnotation("@Hook(HookType.ONCE_PER_REQUEST)").asJar();
        @SuppressWarnings("resource")
        ClassLoader loader =
                new URLClassLoader(jarWithHookAnnotation.writeToTempURL(), ClassLoader.getSystemClassLoader());

        Class<?> classWithInvalidHook = loader.loadClass("hook.InvalidHodorHook");

        hookRegistry.registerHooks(classWithInvalidHook, CORRELATION_ID)
                .onComplete(testContext.succeeding(hookRegistrations -> testContext.verify(() -> {
                    assertThat(hookRegistrations).hasSize(0);
                    testContext.completeNow();
                })));
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("Check that executeHooks works correct")
    void executeHooksTest(VertxTestContext testContext) {
        TestHook hook = new TestHook();
        hookRegistry.registerInstanceHooks(hook, CORRELATION_ID)
                .compose(v -> hookRegistry.executeHooks(HookType.ONCE_PER_REQUEST, Map.of()))
                .onComplete(testContext.succeeding(compFuture -> {
                    assertThat(compFuture.list()).hasSize(1);
                    assertThat(TestHook.wasExecuted).isTrue();
                    testContext.completeNow();
                }));
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("Check that getHookRegistrations works correct")
    void getHookRegistruationsTest(VertxTestContext testContext) {
        hookRegistry.registerHooks(classWithValidHook, CORRELATION_ID).compose(hookRegistrations -> {
            return hookRegistry.getHookRegistrations()
                    .onComplete(testContext.succeeding(allRegistrations -> testContext.verify(() -> {
                        assertThat(hookRegistrations).hasSize(1);
                        assertThat(allRegistrations).containsExactlyElementsIn(hookRegistrations);
                        testContext.completeNow();
                    })));
        });
    }

    public static class TestHook {
        static boolean wasExecuted;

        @SuppressWarnings("PMD.UnusedFormalParameter")
        @Hook(HookType.ONCE_PER_REQUEST)
        public void test(NeonBee neonBee, HookContext hookContext, Promise<Void> promise) {
            wasExecuted = true;
            promise.complete();
        }
    }
}
