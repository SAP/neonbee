package io.neonbee.internal.helper;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.internal.helper.HostHelper.CF_INSTANCE_INTERNAL_IP_ENV_KEY;
import static io.neonbee.internal.helper.HostHelper.getHostIp;
import static org.junit.jupiter.api.condition.OS.WINDOWS;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;

import io.neonbee.test.helper.ReflectionHelper;
import io.neonbee.test.helper.SystemHelper;

class HostHelperTest {

    @Test
    @DisabledOnOs(value = { WINDOWS }, disabledReason = "SystemHelper.setEnvironment has no effect on Windows")
    void verifyHostIp() throws Exception {
        Map<String, String> oldEnv = Map.copyOf(System.getenv());
        assertThat(getHostIp()).isNotEqualTo("someip");
        resetHostIp();
        SystemHelper.setEnvironment(Map.of(CF_INSTANCE_INTERNAL_IP_ENV_KEY, "someip"));
        assertThat(getHostIp()).isEqualTo("someip");
        resetHostIp();
        SystemHelper.setEnvironment(oldEnv);
    }

    private static void resetHostIp() throws Exception {
        ReflectionHelper.setValueOfPrivateStaticField(HostHelper.class, "currentIp", null);
    }
}
