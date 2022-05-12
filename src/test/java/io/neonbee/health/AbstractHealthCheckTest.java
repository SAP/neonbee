package io.neonbee.health;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.health.AbstractHealthCheck.DEFAULT_RETENTION_TIME;
import static io.neonbee.test.helper.OptionsHelper.defaultOptions;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.neonbee.NeonBee;
import io.neonbee.NeonBeeMockHelper;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.healthchecks.Status;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class AbstractHealthCheckTest {
    private static final String DUMMY_ID = "dummy/check-0";

    private static Vertx vertxMock;

    private AbstractHealthCheck hc;

    @BeforeEach
    void setUp() {
        vertxMock = NeonBeeMockHelper.defaultVertxMock();
        NeonBeeMockHelper.registerNeonBeeMock(vertxMock, defaultOptions());
        hc = createDummyHealthCheck(true, true, null);
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("cannot get a health check result if health check was not registered to registry")
    void testResultFails(VertxTestContext testContext) {
        hc.result().onComplete(testContext.failing(t -> testContext.verify(() -> {
            assertThat(t.getMessage()).contains("must be registered");
            testContext.completeNow();
        })));
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("can register global checks")
    void testRegisterGlobalCheck(VertxTestContext testContext) throws HealthCheckException {
        HealthCheckRegistry registry = mock(HealthCheckRegistry.class);
        when(registry.registerGlobalCheck(anyString(), anyLong(), any(), any())).thenReturn(hc);

        hc.register(registry).onComplete(testContext.succeeding(check -> testContext.verify(() -> {
            assertThat(check.isGlobal()).isTrue();
            verify(registry).registerGlobalCheck(eq(DUMMY_ID), eq(DEFAULT_RETENTION_TIME), any(), eq(new JsonObject()));
            testContext.completeNow();
        })));
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("can register node-specific checks")
    void testRegisterNodeCheck(VertxTestContext testContext) throws HealthCheckException {
        HealthCheckRegistry registry = mock(HealthCheckRegistry.class);
        hc = createDummyHealthCheck(false, true, null);
        when(registry.registerNodeCheck(anyString(), anyLong(), any(), any())).thenReturn(hc);

        hc.register(registry).onComplete(testContext.succeeding(check -> testContext.verify(() -> {
            assertThat(check.isGlobal()).isFalse();
            verify(registry).registerNodeCheck(eq(DUMMY_ID), eq(DEFAULT_RETENTION_TIME), any(), eq(new JsonObject()));
            testContext.completeNow();
        })));
    }

    private static AbstractHealthCheck createDummyHealthCheck(boolean global, boolean ok, JsonObject data) {
        return new AbstractHealthCheck(NeonBee.get(vertxMock)) {
            @Override
            Function<NeonBee, Handler<Promise<Status>>> createProcedure() {
                return nb -> p -> p.complete(new Status().setOk(ok).setData(data));
            }

            @Override
            public String getId() {
                return DUMMY_ID;
            }

            @Override
            public boolean isGlobal() {
                return global;
            }
        };
    }
}
