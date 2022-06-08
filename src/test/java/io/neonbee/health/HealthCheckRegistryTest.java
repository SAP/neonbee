package io.neonbee.health;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.test.helper.OptionsHelper.defaultOptions;
import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.endsWith;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;

import io.neonbee.NeonBee;
import io.neonbee.NeonBeeMockHelper;
import io.neonbee.NeonBeeOptions;
import io.neonbee.config.HealthConfig;
import io.neonbee.config.NeonBeeConfig;
import io.neonbee.health.internal.HealthCheck;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.FileSystem;
import io.vertx.core.file.FileSystemException;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.healthchecks.HealthChecks;
import io.vertx.ext.healthchecks.Status;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class HealthCheckRegistryTest {

    private static final String DUMMY_ID = "dummy-group/check-0";

    private static final String NODE_ID = "this-is-a-fake-uuid";

    private static final long RETENTION_TIME = 12L;

    private Vertx vertxMock;

    private AbstractHealthCheck dummyCheck;

    private static class DummyCheck extends AbstractHealthCheck {
        DummyCheck(NeonBee neonBee) {
            super(neonBee);
        }

        @Override
        Function<NeonBee, Handler<Promise<Status>>> createProcedure() {
            return nb -> p -> p.complete(new Status().setOK());
        }

        @Override
        public String getId() {
            return DUMMY_ID;
        }

        @Override
        public boolean isGlobal() {
            return true;
        }
    }

    @BeforeEach
    void setUp() {
        vertxMock = NeonBeeMockHelper.defaultVertxMock();
        NeonBee neonBee = NeonBeeMockHelper.registerNeonBeeMock(vertxMock, defaultOptions(),
                new NeonBeeConfig().setHealthConfig(new HealthConfig().setEnabled(true).setTimeout(2)));

        dummyCheck = new DummyCheck(neonBee);
    }

    @Test
    @DisplayName("it can list all health checks")
    void getHealthChecks() {
        HealthCheckRegistry registry = new HealthCheckRegistry(vertxMock);

        assertThat(registry.getHealthChecks()).isEmpty();
        registry.checks.put("check-1", mock(HealthCheck.class));
        assertThat(registry.getHealthChecks().size()).isEqualTo(1);
    }

    @Test
    @DisplayName("it can register global checks")
    void registerGlobalCheck() throws HealthCheckException {
        NeonBee neonBeeMock = mock(NeonBee.class);
        when(neonBeeMock.getNodeId()).thenReturn(NODE_ID);
        when(neonBeeMock.getConfig()).thenReturn(new NeonBeeConfig().setHealthConfig(new HealthConfig()));

        try (MockedStatic<NeonBee> mocked = mockStatic(NeonBee.class)) {
            mocked.when(() -> NeonBee.get(any(Vertx.class))).thenReturn(neonBeeMock);

            HealthCheckRegistry registry = new HealthCheckRegistry(vertxMock);
            HealthCheck healthCheck = registry.registerGlobalCheck(DUMMY_ID, RETENTION_TIME,
                    nb -> p -> p.complete(new Status()), new JsonObject());

            assertThat(healthCheck.getId()).contains(DUMMY_ID);
            assertThat(healthCheck.getRetentionTime()).isEqualTo(RETENTION_TIME);
            assertThat(registry.getHealthChecks().size()).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("it can register node checks")
    void registerNodeCheck() throws HealthCheckException {
        NeonBee neonBeeMock = mock(NeonBee.class);
        when(neonBeeMock.getNodeId()).thenReturn(NODE_ID);
        when(neonBeeMock.getConfig()).thenReturn(new NeonBeeConfig().setHealthConfig(new HealthConfig()));

        try (MockedStatic<NeonBee> mocked = mockStatic(NeonBee.class)) {
            mocked.when(() -> NeonBee.get(any(Vertx.class))).thenReturn(neonBeeMock);

            HealthCheckRegistry registry = new HealthCheckRegistry(vertxMock);
            HealthCheck healthCheck = registry.registerNodeCheck(DUMMY_ID, RETENTION_TIME,
                    nb -> p -> p.complete(new Status()), new JsonObject());

            assertThat(healthCheck.getId()).matches(Pattern.compile("node/" + NODE_ID + "/" + DUMMY_ID));
            assertThat(healthCheck.getRetentionTime()).isEqualTo(RETENTION_TIME);
            assertThat(registry.getHealthChecks().size()).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("it can only register health checks with unique names")
    void register(VertxTestContext testContext) {
        Checkpoint cp = testContext.checkpoint(3);

        HealthCheckRegistry registry = new HealthCheckRegistry(vertxMock);
        registry.register(dummyCheck).onComplete(testContext.succeeding(hc -> testContext.verify(() -> {
            assertThat(registry.checks.size()).isEqualTo(1);
            assertThat(registry.checks.get(DUMMY_ID).getId()).isEqualTo(DUMMY_ID);
            cp.flag();

            registry.register(dummyCheck).onComplete(testContext.failing(t -> testContext.verify(() -> {
                assertThat(t.getMessage()).isEqualTo("HealthCheck '" + DUMMY_ID + "' already registered.");
                assertThat(t).isInstanceOf(HealthCheckException.class);
                assertThat(registry.checks.size()).isEqualTo(1);
                cp.flag();

                AbstractHealthCheck otherCheck = new AbstractHealthCheck(NeonBee.get(vertxMock)) {
                    @Override
                    Function<NeonBee, Handler<Promise<Status>>> createProcedure() {
                        return neonBee -> p -> p.complete(new Status().setOK());
                    }

                    @Override
                    public String getId() {
                        return "other-dummy";
                    }

                    @Override
                    public boolean isGlobal() {
                        return true;
                    }
                };

                registry.register(otherCheck).onComplete(testContext.succeeding(v -> testContext.verify(() -> {
                    assertThat(registry.checks.size()).isEqualTo(2);
                    cp.flag();
                })));
            })));
        })));
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("should prefer disabling of health checks from health check config of config folder")
    void testCustomConfigEnabled(VertxTestContext testContext) {
        NeonBeeMockHelper.registerNeonBeeMock(vertxMock, new NeonBeeOptions.Mutable().setWorkingDirectory(Path.of("")),
                new NeonBeeConfig().setHealthConfig(new HealthConfig().setEnabled(true)));
        HealthCheckRegistry registry = new HealthCheckRegistry(vertxMock);
        FileSystem fileSystemMock = vertxMock.fileSystem();

        when(fileSystemMock.readFile(any()))
                .thenReturn(failedFuture(new FileSystemException(new NoSuchFileException("file"))));
        when(fileSystemMock.readFile(endsWith(".yml")))
                .thenReturn(succeededFuture(Buffer.buffer("---\nenabled: false")));

        dummyCheck.register(registry).onComplete(testContext.succeeding(check -> testContext.verify(() -> {
            String path =
                    NeonBee.get(vertxMock).getOptions().getConfigDirectory().resolve(dummyCheck.getClass().getName())
                            + ".yaml";
            verify(fileSystemMock).readFile(eq(path));
            assertThat(check).isNull();
            testContext.completeNow();
        })));
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("should prefer enablement of health checks and timeout from health check config of config folder")
    void testCustomConfigDisabled(VertxTestContext testContext) {
        NeonBeeMockHelper.registerNeonBeeMock(vertxMock, new NeonBeeOptions.Mutable().setWorkingDirectory(Path.of("")),
                new NeonBeeConfig().setHealthConfig(new HealthConfig().setEnabled(false).setTimeout(2)));
        HealthCheckRegistry registry = new HealthCheckRegistry(vertxMock);
        HealthChecks mockedChecks = mock(HealthChecks.class);

        when(mockedChecks.register(anyString(), anyLong(), any())).thenReturn(mockedChecks);
        registry.healthChecks = mockedChecks;
        FileSystem fileSystemMock = vertxMock.fileSystem();
        when(fileSystemMock.readFile(any()))
                .thenReturn(failedFuture(new FileSystemException(new NoSuchFileException("file"))));
        when(fileSystemMock.readFile(endsWith(".yml")))
                .thenReturn(succeededFuture(Buffer.buffer("---\nenabled: true\ntimeout: 3")));

        dummyCheck.register(registry).onComplete(testContext.succeeding(check -> testContext.verify(() -> {
            String path =
                    NeonBee.get(vertxMock).getOptions().getConfigDirectory().resolve(dummyCheck.getClass().getName())
                            + ".yaml";
            verify(fileSystemMock).readFile(eq(path));
            verify(registry.healthChecks).register(eq(DUMMY_ID), eq(3000L), any());
            assertThat(registry.checks.size()).isEqualTo(1);
            testContext.completeNow();
        })));
    }

    @Test
    @DisplayName("it can unregister health checks by health object and id")
    void testUnregister() {
        HealthCheckRegistry registry = new HealthCheckRegistry(vertxMock);
        HealthChecks mockedChecks = mock(HealthChecks.class);
        when(mockedChecks.register(anyString(), anyLong(), any())).thenReturn(mockedChecks);
        registry.healthChecks = mockedChecks;

        registry.register(dummyCheck);
        assertThat(registry.checks.size()).isEqualTo(1);

        registry.unregister(dummyCheck);
        assertThat(registry.checks.size()).isEqualTo(0);

        registry.register(dummyCheck);
        assertThat(registry.checks.size()).isEqualTo(1);

        registry.unregister(DUMMY_ID);
        assertThat(registry.checks.size()).isEqualTo(0);

        verify(mockedChecks, times(2)).register(eq(DUMMY_ID), eq(2000L), any());
        verify(mockedChecks, times(2)).unregister(eq(DUMMY_ID));
    }
}
