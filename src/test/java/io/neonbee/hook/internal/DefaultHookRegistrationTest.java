package io.neonbee.hook.internal;

import static com.google.common.truth.Truth.assertThat;

import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.neonbee.NeonBee;
import io.neonbee.hook.HookContext;
import io.neonbee.hook.HookRegistration;
import io.neonbee.hook.HookType;
import io.neonbee.internal.BasicJar;
import io.neonbee.internal.Helper;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class DefaultHookRegistrationTest {
    private DefaultHookRegistry hookRegistry;

    private Object instanceWithHook;

    private Method hookMethod;

    private DefaultHookRegistration defaultHookegistration;

    @BeforeEach
    void setup(Vertx vertx) throws Exception {
        hookRegistry = new DefaultHookRegistry(vertx);

        BasicJar jarWithHookAnnotation =
                new HookClassTemplate(HookClassTemplate.VALID_HOOK_TEMPLATE, "HodorHook", "hook")
                        .setMethodAnnotation("@Hook(HookType.ONCE_PER_REQUEST)").asJar();
        @SuppressWarnings("resource")
        ClassLoader loader =
                new URLClassLoader(jarWithHookAnnotation.writeToTempURL(), ClassLoader.getSystemClassLoader());

        Class<?> classWithValidHook = loader.loadClass("hook.HodorHook");

        instanceWithHook = classWithValidHook.getConstructor().newInstance();
        hookMethod = classWithValidHook.getMethod("doNothing", NeonBee.class, HookContext.class, Promise.class);

        defaultHookegistration =
                new DefaultHookRegistration(hookRegistry, instanceWithHook, hookMethod, HookType.ONCE_PER_REQUEST);
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("Check that unregister works correct")
    void testUnregister(VertxTestContext testContext) {
        hookRegistry.registerInstanceHooks(instanceWithHook, "correlId").compose(registrations -> {
            testContext.verify(() -> assertThat(registrations).hasSize(1));
            HookRegistration currentHookRegistration = registrations.stream().findFirst().get();
            return hookRegistry.getHookRegistrations().compose(allRegistrations -> {
                testContext.verify(() -> assertThat(allRegistrations).containsExactly(currentHookRegistration));
                return currentHookRegistration.unregister();
            });
        }).compose(v -> hookRegistry.getHookRegistrations())
                .onComplete(testContext.succeeding(allRegistrations -> testContext.verify(() -> {
                    assertThat(allRegistrations).isEmpty();
                    testContext.completeNow();
                })));
    }

    @Test
    @DisplayName("Check that getters of DefaultHookRegistration are working correct")
    void testGetters() {
        assertThat(defaultHookegistration.getId()).isNotNull();
        assertThat(defaultHookegistration.getRelatedObject()).isEqualTo(instanceWithHook);
        assertThat(defaultHookegistration.getHookMethod()).isEqualTo(hookMethod);
        assertThat(defaultHookegistration.getType()).isEqualTo(HookType.ONCE_PER_REQUEST);
        assertThat(defaultHookegistration.getName()).isEqualTo("hook.HodorHook::doNothing");
    }

    @Test
    @DisplayName("Check that equals works correct")
    @SuppressWarnings("TruthSelfEquals")
    void testEquals() {
        DefaultHookRegistration defaultHookegistrationClone =
                new DefaultHookRegistration(hookRegistry, instanceWithHook, hookMethod, HookType.ONCE_PER_REQUEST);

        DefaultHookRegistration differentHookegistration =
                new DefaultHookRegistration(hookRegistry, instanceWithHook, hookMethod, HookType.BEFORE_BOOTSTRAP);

        assertThat(defaultHookegistrationClone).isEqualTo(defaultHookegistrationClone);
        assertThat(defaultHookegistrationClone).isEqualTo(defaultHookegistration);
        assertThat(differentHookegistration).isNotEqualTo(defaultHookegistration);
        assertThat(differentHookegistration).isNotEqualTo(null);
    }

    @Test
    @DisplayName("Check that equals works correct")
    void testHashcode() {
        DefaultHookRegistration defaultHookegistrationClone =
                new DefaultHookRegistration(hookRegistry, instanceWithHook, hookMethod, HookType.ONCE_PER_REQUEST);

        DefaultHookRegistration differentHookegistration =
                new DefaultHookRegistration(hookRegistry, instanceWithHook, hookMethod, HookType.BEFORE_SHUTDOWN);

        assertThat(defaultHookegistration.hashCode()).isEqualTo(defaultHookegistrationClone.hashCode());

        assertThat(differentHookegistration.hashCode()).isNotEqualTo(defaultHookegistration.hashCode());
        assertThat(differentHookegistration.hashCode()).isNotEqualTo(Helper.EMPTY.hashCode());
    }
}
