package io.neonbee.hook.internal;

import static com.google.common.truth.Truth.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.neonbee.hook.HookContext;
import io.neonbee.hook.HookType;

class DefaultHookContextTest {

    @Test
    void withoutParametersTest() {
        HookContext hookContext = DefaultHookContext.withoutParameters(HookType.AFTER_STARTUP);
        assertThat(hookContext.getHookType()).isEqualTo(HookType.AFTER_STARTUP);
    }

    @Test
    void ofTest() {
        Object hodor = new Object();
        HookContext hookContext = DefaultHookContext.of(HookType.AFTER_STARTUP, Map.of("hodor", hodor));
        assertThat(hookContext.getHookType()).isEqualTo(HookType.AFTER_STARTUP);
        assertThat(hookContext.<Object>get("hodor")).isEqualTo(hodor);
    }
}
