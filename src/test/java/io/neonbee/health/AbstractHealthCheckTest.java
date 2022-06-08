package io.neonbee.health;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.health.AbstractHealthCheck.DEFAULT_RETENTION_TIME;
import static io.neonbee.test.helper.OptionsHelper.defaultOptions;
import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.neonbee.NeonBee;
import io.neonbee.NeonBeeMockHelper;
import io.neonbee.config.HealthConfig;
import io.neonbee.config.NeonBeeConfig;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.FileSystem;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.healthchecks.Status;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class AbstractHealthCheckTest {
    private static final String DUMMY_ID = "dummy/check-0";

    private static final JsonObject HEALTH_CONFIG = new JsonObject().put("enabled", true);

    private Vertx vertxMock;

    private AbstractHealthCheck hc;

    private FileSystem fsMock;

    @BeforeEach
    void setUp() {
        vertxMock = NeonBeeMockHelper.defaultVertxMock();
        fsMock = vertxMock.fileSystem();
        NeonBeeMockHelper.registerNeonBeeMock(vertxMock, defaultOptions(),
                new NeonBeeConfig().setHealthConfig(new HealthConfig().setEnabled(true)));
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
        when(registry.registerGlobalCheck(anyString(), anyLong(), any(), any(JsonObject.class))).thenReturn(hc);

        hc.register(registry).onComplete(testContext.succeeding(check -> testContext.verify(() -> {
            assertThat(check.isGlobal()).isTrue();
            verify(registry).registerGlobalCheck(eq(DUMMY_ID), eq(DEFAULT_RETENTION_TIME), any(), eq(HEALTH_CONFIG));
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
            verify(registry).registerNodeCheck(eq(DUMMY_ID), eq(DEFAULT_RETENTION_TIME), any(), eq(HEALTH_CONFIG));
            testContext.completeNow();
        })));
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("can merge a config from file in config directory with the default config")
    void testMergeConfigs(VertxTestContext testContext) {
        when(fsMock.readFile(anyString())).thenReturn(
                succeededFuture(new JsonObject().put("enabled", false).put("new-random-key", "something").toBuffer()));
        when(vertxMock.fileSystem()).thenReturn(fsMock);

        hc.mergeHealthCheckConfig().onComplete(testContext.succeeding(config -> testContext.verify(() -> {
            String path =
                    NeonBee.get(vertxMock).getOptions().getConfigDirectory().resolve(hc.getClass().getName()) + ".yaml";
            verify(fsMock).readFile(eq(path));
            assertThat(config.getBoolean("enabled")).isFalse();
            assertThat(config.getString("new-random-key")).isEqualTo("something");
            testContext.completeNow();
        })));
    }

    static Stream<Arguments> provideConfigArguments() {
        return Stream.of(Arguments.of(succeededFuture(new JsonObject().toBuffer()), "config from file is empty"),
                Arguments.of(succeededFuture(Buffer.buffer()), "config from file is an empty buffer"),
                Arguments.of(failedFuture("oops"), "retrieving config fails"));
    }

    @ParameterizedTest(name = "{index} => {1}")
    @MethodSource("provideConfigArguments")
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("should return default config, if")
    @SuppressWarnings("unused")
    void testConfigNotExisting(Future<Buffer> expected, String testCase, VertxTestContext testContext) {
        when(fsMock.readFile(anyString())).thenReturn(expected);
        when(vertxMock.fileSystem()).thenReturn(fsMock);

        hc.mergeHealthCheckConfig().onComplete(testContext.succeeding(config -> testContext.verify(() -> {
            String path =
                    NeonBee.get(vertxMock).getOptions().getConfigDirectory().resolve(hc.getClass().getName()) + ".yaml";
            verify(fsMock).readFile(eq(path));
            assertThat(config).isEqualTo(AbstractHealthCheck.DEFAULT_HEALTH_CHECK_CONFIG.mutableCopy());
            testContext.completeNow();
        })));
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("can merge a config with the default config")
    void testConfigRetrievalFails(VertxTestContext testContext) {
        when(fsMock.readFile(anyString())).thenReturn(failedFuture("oops"));
        when(vertxMock.fileSystem()).thenReturn(fsMock);

        hc.mergeHealthCheckConfig().onComplete(testContext.succeeding(config -> testContext.verify(() -> {
            String path =
                    NeonBee.get(vertxMock).getOptions().getConfigDirectory().resolve(hc.getClass().getName()) + ".yaml";
            verify(fsMock).readFile(eq(path));
            assertThat(config).isEqualTo(AbstractHealthCheck.DEFAULT_HEALTH_CHECK_CONFIG.mutableCopy());
            testContext.completeNow();
        })));
    }

    private AbstractHealthCheck createDummyHealthCheck(boolean global, boolean ok, JsonObject data) {
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
