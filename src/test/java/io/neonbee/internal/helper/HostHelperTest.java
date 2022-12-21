package io.neonbee.internal.helper;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.internal.helper.HostHelper.CF_INSTANCE_INTERNAL_IP_ENV_KEY;
import static io.neonbee.internal.helper.HostHelper.getHostIp;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import io.neonbee.test.helper.ReflectionHelper;
import io.neonbee.test.helper.SystemHelper;

@Isolated
class HostHelperTest {
    @Test
    void verifyHostIp() throws Exception {
        assertThat(getHostIp()).isNotEqualTo("someip");
        resetHostIp();
        SystemHelper.withEnvironment(Map.of(CF_INSTANCE_INTERNAL_IP_ENV_KEY, "someip"), () -> {
            assertThat(getHostIp()).isEqualTo("someip");
        });
        resetHostIp();
    }

    private static void resetHostIp() throws Exception {
        ReflectionHelper.setValueOfPrivateStaticField(HostHelper.class, "currentIp", null);
    }
}
