package io.neonbee.test.helper;

import static com.google.common.truth.Truth.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.vertx.junit5.VertxTestContext.ExecutionBlock;

public class ReflectionHelperTest {
    private static final Integer NUMBER = 2;

    @Test
    @DisplayName("Check that final fields can be modified")
    public void testSetValueOfPrivateStaticField() throws Throwable {
        assertThat(NUMBER).isEqualTo(2);
        ExecutionBlock reset = ReflectionHelper.setValueOfPrivateStaticField(getClass(), "NUMBER", 3);
        assertThat(NUMBER).isEqualTo(3);
        reset.apply();
        assertThat(NUMBER).isEqualTo(2);
    }
}
