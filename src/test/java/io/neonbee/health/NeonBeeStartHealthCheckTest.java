package io.neonbee.health;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.neonbee.NeonBee;
import io.vertx.core.Promise;
import io.vertx.ext.healthchecks.Status;

class NeonBeeStartHealthCheckTest {
    @Test
    @DisplayName("Should return true if NeonBee was started")
    void testStarted() {
        NeonBee neonBeeMock = mock(NeonBee.class);
        NeonBeeStartHealthCheck healthCheck = new NeonBeeStartHealthCheck(neonBeeMock);

        when(neonBeeMock.isStarted()).thenReturn(false);
        Promise<Status> promise1 = Promise.promise();
        healthCheck.createProcedure().apply(neonBeeMock).handle(promise1);
        assertThat(promise1.future().result().isOk()).isFalse();

        when(neonBeeMock.isStarted()).thenReturn(true);
        Promise<Status> promise2 = Promise.promise();
        healthCheck.createProcedure().apply(neonBeeMock).handle(promise2);
        assertThat(promise2.future().result().isOk()).isTrue();
    }
}
