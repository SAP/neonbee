package io.neonbee.hook.internal;

import static com.google.common.truth.Truth.assertThat;

import java.lang.reflect.Method;
import java.net.URLClassLoader;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.neonbee.NeonBee;
import io.neonbee.hook.HookContext;
import io.neonbee.hook.HookRegistration;
import io.neonbee.hook.HookType;
import io.neonbee.internal.BasicJar;
import io.neonbee.internal.helper.StringHelper;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class DefaultHookRegistrationTest {
    private DefaultHookRegistry hookRegistry;

    private Object instanceWithHook;

    private Method hookMethod;

    private DefaultHookRegistration defaultHookRegistration;

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

        defaultHookRegistration =
                new DefaultHookRegistration(hookRegistry, instanceWithHook, hookMethod, HookType.ONCE_PER_REQUEST);
    }

    @Test
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
        assertThat(defaultHookRegistration.getId()).isNotNull();
        assertThat(defaultHookRegistration.getRelatedObject()).isEqualTo(instanceWithHook);
        assertThat(defaultHookRegistration.getHookMethod()).isEqualTo(hookMethod);
        assertThat(defaultHookRegistration.getType()).isEqualTo(HookType.ONCE_PER_REQUEST);
        assertThat(defaultHookRegistration.getName()).isEqualTo("hook.HodorHook::doNothing");
    }

    @Test
    @DisplayName("Check that equals works correct")
    @SuppressWarnings("TruthSelfEquals")
    void testEquals() {
        DefaultHookRegistration defaultHookRegistrationClone =
                new DefaultHookRegistration(hookRegistry, instanceWithHook, hookMethod, HookType.ONCE_PER_REQUEST);

        DefaultHookRegistration differentHookRegistration =
                new DefaultHookRegistration(hookRegistry, instanceWithHook, hookMethod, HookType.BEFORE_BOOTSTRAP);

        assertThat(defaultHookRegistrationClone).isEqualTo(defaultHookRegistrationClone);
        assertThat(defaultHookRegistrationClone).isEqualTo(defaultHookRegistration);
        assertThat(differentHookRegistration).isNotEqualTo(defaultHookRegistration);
        assertThat(differentHookRegistration).isNotEqualTo(null);
    }

    @Test
    @DisplayName("Check that equals works correct")
    void testHashcode() {
        DefaultHookRegistration defaultHookRegistrationClone =
                new DefaultHookRegistration(hookRegistry, instanceWithHook, hookMethod, HookType.ONCE_PER_REQUEST);

        DefaultHookRegistration differentHookRegistration =
                new DefaultHookRegistration(hookRegistry, instanceWithHook, hookMethod, HookType.BEFORE_SHUTDOWN);

        assertThat(defaultHookRegistration.hashCode()).isEqualTo(defaultHookRegistrationClone.hashCode());

        assertThat(differentHookRegistration.hashCode()).isNotEqualTo(defaultHookRegistration.hashCode());
        assertThat(differentHookRegistration.hashCode()).isNotEqualTo(StringHelper.EMPTY.hashCode());
    }
}
