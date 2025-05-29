package io.neonbee.health;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.test.base.NeonBeeTestBase.LONG_RUNNING_TEST;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.neonbee.NeonBee;
import io.vertx.core.Vertx;
import io.vertx.ext.healthchecks.HealthChecks;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@Tag(LONG_RUNNING_TEST)
@ExtendWith(VertxExtension.class)
class RedisClusterHealthCheckTest {

    private NeonBee neonBee;

    private HealthChecks checks;

    private RedisClusterHealthCheck clusterHealthCheck;

    @BeforeEach
    void setUp(Vertx vertx) {
        checks = HealthChecks.create(vertx);
        clusterHealthCheck = new RedisClusterHealthCheck(NeonBee.get(vertx));
        neonBee = mock(NeonBee.class);
        when(neonBee.getVertx()).thenReturn(vertx);

        assertThat(clusterHealthCheck.isGlobal()).isTrue();
        assertThat(clusterHealthCheck.getId()).startsWith("cluster.");
    }

    @Test
    @DisplayName("should register and execute health check")
    void testCreateProcedure(VertxTestContext testContext) {
        checks.register(
                RedisClusterHealthCheck.NAME,
                clusterHealthCheck.createProcedure().apply(neonBee));
        checks
                .checkStatus(RedisClusterHealthCheck.NAME)
                .onComplete(
                        testContext.succeeding(result -> testContext.verify(() -> {
                            assertThat(result.getUp()).isNotNull();
                            testContext.completeNow();
                        })));
    }

    @Test
    @DisplayName("should have correct health check name")
    void testHealthCheckName() {
        assertThat(clusterHealthCheck.getId()).isEqualTo("cluster.redis");
    }
}
