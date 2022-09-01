package io.neonbee;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static io.neonbee.NeonBee.HAZELCAST_FACTORY;
import static io.neonbee.NeonBeeMockHelper.defaultVertxMock;
import static io.neonbee.NeonBeeMockHelper.registerNeonBeeMock;
import static io.neonbee.NeonBeeProfile.ALL;
import static io.neonbee.NeonBeeProfile.CORE;
import static io.neonbee.NeonBeeProfile.INCUBATOR;
import static io.neonbee.NeonBeeProfile.NO_WEB;
import static io.neonbee.NeonBeeProfile.STABLE;
import static io.neonbee.internal.helper.StringHelper.EMPTY;
import static io.neonbee.test.helper.DeploymentHelper.getDeployedVerticles;
import static io.neonbee.test.helper.OptionsHelper.defaultOptions;
import static io.neonbee.test.helper.ResourceHelper.TEST_RESOURCES;
import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static java.util.stream.Collectors.toUnmodifiableList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import io.neonbee.config.NeonBeeConfig;
import io.neonbee.health.DummyHealthCheck;
import io.neonbee.health.DummyHealthCheckProvider;
import io.neonbee.health.EventLoopHealthCheck;
import io.neonbee.health.HazelcastClusterHealthCheck;
import io.neonbee.health.HealthCheckProvider;
import io.neonbee.health.HealthCheckRegistry;
import io.neonbee.health.MemoryHealthCheck;
import io.neonbee.health.internal.HealthCheck;
import io.neonbee.internal.NeonBeeModuleJar;
import io.neonbee.internal.tracking.MessageDirection;
import io.neonbee.internal.tracking.TrackingDataLoggingStrategy;
import io.neonbee.internal.tracking.TrackingInterceptor;
import io.neonbee.internal.verticle.ConsolidationVerticle;
import io.neonbee.internal.verticle.HealthCheckVerticle;
import io.neonbee.internal.verticle.LoggerManagerVerticle;
import io.neonbee.internal.verticle.MetricsVerticle;
import io.neonbee.test.base.NeonBeeTestBase;
import io.neonbee.test.helper.WorkingDirectoryBuilder;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.eventbus.DeliveryContext;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxTestContext;
import io.vertx.test.fakecluster.FakeClusterManager;

class NeonBeeTest extends NeonBeeTestBase {
    private Vertx vertx;

    @Override
    protected void adaptOptions(TestInfo testInfo, NeonBeeOptions.Mutable options) {
        options.addActiveProfile(NO_WEB);
        switch (testInfo.getTestMethod().get().getName()) {
        case "testDeployCoreVerticlesFromClassPath":
            options.addActiveProfile(CORE);
            options.setIgnoreClassPath(false);
            break;
        case "testDeployModule":
            try {
                options.setModuleJarPaths(
                        List.of(NeonBeeModuleJar.create("testmodule").withVerticles().build().writeToTempPath()));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            break;
        default:
        }
    }

    @AfterEach
    void closeVertx(VertxTestContext testContext) {
        if (vertx != null) {
            // important, as otherwise the cluster won't be stopped!
            vertx.close().onComplete(testContext.succeedingThenComplete());
            vertx = null; // NOPMD
        } else {
            testContext.completeNow();
        }
    }

    @Override
    protected WorkingDirectoryBuilder provideWorkingDirectoryBuilder(TestInfo testInfo, VertxTestContext testContext) {
        switch (testInfo.getTestMethod().map(Method::getName).orElse(EMPTY)) {
        case "testStartWithNoWorkingDirectory":
            return WorkingDirectoryBuilder.none();
        case "testStartWithEmptyWorkingDirectory":
            return WorkingDirectoryBuilder.empty();
        default:
            return super.provideWorkingDirectoryBuilder(testInfo, testContext);
        }
    }

    @Test
    @Timeout(value = 4, timeUnit = TimeUnit.SECONDS)
    @DisplayName("NeonBee should start with default options / default working directory")
    void testStart(Vertx vertx) {
        assertThat(getNeonBee()).isNotNull();
        assertThat(NeonBee.get(vertx)).isNotNull();
    }

    @Test
    @Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
    @DisplayName("NeonBee should deploy all system verticles")
    void testDeploySystemVerticles(Vertx vertx) {
        assertThat(getDeployedVerticles(vertx)).containsExactly(MetricsVerticle.class, ConsolidationVerticle.class,
                LoggerManagerVerticle.class, HealthCheckVerticle.class);
    }

    @Test
    @Timeout(value = 30, timeUnit = TimeUnit.SECONDS)
    @DisplayName("NeonBee should deploy class path verticles (from NeonBeeExtensionBasedTest)")
    void testDeployCoreVerticlesFromClassPath(Vertx vertx) {
        assertThat(getDeployedVerticles(vertx)).contains(NeonBeeExtensionBasedTest.CoreDataVerticle.class);
    }

    @Test
    @Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
    @DisplayName("NeonBee should deploy module JAR")
    void testDeployModule(Vertx vertx) {
        assertThat(getDeployedVerticles(vertx).stream().map(Class::getName)).containsAtLeast("ClassA", "ClassB");
    }

    @Test
    @Disabled("If the working dir is deleted, it's not possible to override the HttpServerDefaultPort ...")
    @Timeout(value = 4, timeUnit = TimeUnit.SECONDS)
    @DisplayName("NeonBee should start with no working directory and create the working directory")
    void testStartWithNoWorkingDirectory() {
        assertThat(Files.isDirectory(getNeonBee().getOptions().getWorkingDirectory())).isTrue();
    }

    @Test
    @Disabled("If the working dir is empty, it's not possible to override the HttpServerDefaultPort ...")
    @Timeout(value = 4, timeUnit = TimeUnit.SECONDS)
    @DisplayName("NeonBee should start with an empty working directory and create the logs directory")
    void testStartWithEmptyWorkingDirectory() {
        assertThat(Files.isDirectory(getNeonBee().getOptions().getLogDirectory())).isTrue();
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("Vert.x should start in non-clustered mode")
    void testStandaloneInitialization(VertxTestContext testContext) {
        NeonBeeOptions options = defaultOptions().clearActiveProfiles();
        NeonBee.create((NeonBee.OwnVertxFactory) (vertxOptions) -> NeonBee.newVertx(vertxOptions, options),
                opts -> new FakeClusterManager(), options, null)
                .onComplete(testContext.succeeding(neonBee -> testContext.verify(() -> {
                    assertThat((vertx = neonBee.getVertx()).isClustered()).isFalse();
                    testContext.completeNow();
                })));
    }

    @Test
    @Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
    @DisplayName("Vert.x should start in clustered mode")
    void testClusterInitialization(VertxTestContext testContext) {
        NeonBeeOptions options = defaultOptions().setClustered(true);
        NeonBee.create((NeonBee.OwnVertxFactory) (vertxOptions) -> NeonBee.newVertx(vertxOptions, options),
                opts -> new FakeClusterManager(), options, null)
                .onComplete(testContext.succeeding(neonBee -> testContext.verify(() -> {
                    assertThat((vertx = neonBee.getVertx()).isClustered()).isTrue();
                    testContext.completeNow();
                })));
    }

    @Test
    @DisplayName("NeonBee should register and unregister local consumer correct.")
    void testRegisterAndUnregisterLocalConsumer() {
        String address = "DataVerticle1";
        assertThat(getNeonBee().isLocalConsumerAvailable(address)).isFalse();
        getNeonBee().registerLocalConsumer(address);
        assertThat(getNeonBee().isLocalConsumerAvailable(address)).isTrue();
        getNeonBee().unregisterLocalConsumer(address);
        assertThat(getNeonBee().isLocalConsumerAvailable(address)).isFalse();
    }

    @Test
    @DisplayName("NeonBee should register all default health checks")
    void testRegisterDefaultHealthChecks() {
        Map<String, HealthCheck> registeredChecks = getNeonBee().getHealthCheckRegistry().getHealthChecks();
        assertThat(registeredChecks.size()).isEqualTo(2);
        String nodePrefix = "node." + getNeonBee().getNodeId() + ".";
        assertThat(registeredChecks.containsKey(nodePrefix + EventLoopHealthCheck.NAME)).isTrue();
        assertThat(registeredChecks.containsKey(nodePrefix + MemoryHealthCheck.NAME)).isTrue();
    }

    @Test
    @DisplayName("NeonBee should register all cluster + default health checks if started clustered")
    void testRegisterClusterHealthChecks(VertxTestContext testContext) {
        NeonBeeOptions options = defaultOptions().setClustered(true).addActiveProfile(NO_WEB);
        NeonBee.create((NeonBee.OwnVertxFactory) (vertxOptions) -> NeonBee.newVertx(vertxOptions, options),
                HAZELCAST_FACTORY, options, null)
                .onComplete(testContext.succeeding(neonBee -> testContext.verify(() -> {
                    vertx = neonBee.getVertx(); // ensure new Vert.x instance gets closed in afterEach
                    Map<String, HealthCheck> registeredChecks = neonBee.getHealthCheckRegistry().getHealthChecks();
                    assertThat(registeredChecks.size()).isEqualTo(3);
                    assertThat(registeredChecks.containsKey(HazelcastClusterHealthCheck.NAME)).isTrue();
                    testContext.completeNow();
                })));
    }

    @Test
    @Timeout(value = 4, timeUnit = TimeUnit.SECONDS)
    @DisplayName("NeonBee should register all SPI-provided + default health checks")
    void testRegisterSpiAndDefaultHealthChecks(VertxTestContext testContext) {
        HealthCheckRegistry registry = getNeonBee().getHealthCheckRegistry();
        for (String checkId : registry.getHealthChecks().keySet().stream().collect(toUnmodifiableList())) {
            registry.unregister(checkId);
        }

        runWithMetaInfService(HealthCheckProvider.class, DummyHealthCheckProvider.class.getName(), testContext, () -> {
            getNeonBee().registerHealthChecks().onComplete(testContext.succeeding(v -> testContext.verify(() -> {
                Map<String, HealthCheck> registeredChecks = registry.getHealthChecks();
                assertThat(registeredChecks.size()).isEqualTo(3);
                assertThat(registeredChecks.containsKey(DummyHealthCheck.DUMMY_ID)).isTrue();
                testContext.completeNow();
            })));
        });
    }

    private void runWithMetaInfService(Class<?> service, String content, VertxTestContext context, Runnable runnable) {
        Path providerPath = TEST_RESOURCES.resolve("META-INF/services/" + service.getName());
        ClassLoader classLoader;
        try {
            Files.write(providerPath, content.getBytes(StandardCharsets.UTF_8));
            classLoader = new URLClassLoader(new URL[] { TEST_RESOURCES.resolve(".").toUri().toURL() });
        } catch (IOException e) {
            context.failNow(e);
            return;
        }
        Thread thread = Thread.currentThread();
        ClassLoader original = thread.getContextClassLoader();
        thread.setContextClassLoader(classLoader);

        try {
            runnable.run();
        } finally {
            thread.setContextClassLoader(original);
            try {
                Files.deleteIfExists(providerPath);
            } catch (IOException e) {
                context.failNow(e);
            }
        }
    }

    @Test
    @DisplayName("NeonBee should have a (unique) node id")
    void testGetNodeId() {
        assertThat(getNeonBee().getNodeId()).matches(Pattern.compile("[0-9a-zA-Z\\-]+"));
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("Vert.x should add eventbus interceptors.")
    void testDecorateEventbus() {
        Vertx vertx = defaultVertxMock();
        NeonBee neonBee = registerNeonBeeMock(vertx,
                new NeonBeeConfig(new JsonObject().put("trackingDataHandlingStrategy", "wrongvalue")));
        EventBus eventBus = mock(EventBus.class);
        when(vertx.eventBus()).thenReturn(eventBus);
        when(eventBus.addInboundInterceptor(Mockito.any(Handler.class))).thenReturn(eventBus);
        when(eventBus.addOutboundInterceptor(Mockito.any(Handler.class))).thenReturn(eventBus);
        ArgumentCaptor<Handler<DeliveryContext<Object>>> inboundHandlerCaptor = ArgumentCaptor.forClass(Handler.class);
        ArgumentCaptor<Handler<DeliveryContext<Object>>> outboundHandlerCaptor = ArgumentCaptor.forClass(Handler.class);
        neonBee.decorateEventBus();
        verify(eventBus).addInboundInterceptor(inboundHandlerCaptor.capture());
        verify(eventBus).addOutboundInterceptor(outboundHandlerCaptor.capture());
        TrackingInterceptor inboundHandler = (TrackingInterceptor) inboundHandlerCaptor.getValue();
        TrackingInterceptor outboundHandler = (TrackingInterceptor) outboundHandlerCaptor.getValue();
        assertThat(inboundHandler.getDirection()).isEqualTo(MessageDirection.INBOUND);
        assertThat(TrackingDataLoggingStrategy.class).isAssignableTo(inboundHandler.getHandler().getClass());
        assertThat(outboundHandler.getDirection()).isEqualTo(MessageDirection.OUTBOUND);
        assertThat(TrackingDataLoggingStrategy.class).isAssignableTo(outboundHandler.getHandler().getClass());
    }

    @Test
    void testFilterByProfile() {
        assertThat(NeonBee.filterByAutoDeployAndProfiles(CoreVerticle.class, List.of(CORE))).isTrue();
        assertThat(NeonBee.filterByAutoDeployAndProfiles(CoreVerticle.class, List.of(CORE, STABLE))).isTrue();
        assertThat(NeonBee.filterByAutoDeployAndProfiles(CoreVerticle.class, List.of(STABLE))).isFalse();
        assertThat(NeonBee.filterByAutoDeployAndProfiles(StableVerticle.class, List.of(STABLE))).isTrue();
        assertThat(NeonBee.filterByAutoDeployAndProfiles(StableVerticle.class, List.of(STABLE, CORE))).isTrue();
        assertThat(NeonBee.filterByAutoDeployAndProfiles(IncubatorVerticle.class, List.of(INCUBATOR))).isTrue();
        assertThat(NeonBee.filterByAutoDeployAndProfiles(SystemVerticle.class, List.of(CORE))).isFalse();
        assertThat(NeonBee.filterByAutoDeployAndProfiles(SystemVerticle.class, List.of(ALL))).isFalse();
    }

    static Stream<Arguments> arguments() {
        Arguments one = Arguments.of(
                "fail the boot, but close Vert.x fine and ensure a Vert.x that is NOT owned by the outside is closed",
                true, succeededFuture());

        Arguments two = Arguments.of(
                "fail the boot and assure that Vert.x is not closed for an instance that is provided from the outside",
                false, succeededFuture());

        Arguments three = Arguments.of("fail the boot and also the Vert.x close", true, failedFuture("ANY FAILURE!!"));

        return Stream.of(one, two, three);
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("arguments")
    @Timeout(timeUnit = TimeUnit.SECONDS, value = 10)
    @DisplayName("NeonBee should close only self-owned Vert.x instances if boot fails")
    @SuppressWarnings("PMD.UnusedFormalParameter")
    void checkTestCloseVertxOnError(String description, boolean ownVertx, Future<Void> result,
            VertxTestContext testContext) {
        try (MockedStatic<NeonBee> mocked = mockStatic(NeonBee.class)) {
            mocked.when(() -> NeonBee.loadConfig(any(), any()))
                    .thenReturn(failedFuture(new RuntimeException("Failing Vert.x!")));
            mocked.when(() -> NeonBee.create(any(), any(), any(), any())).thenCallRealMethod();

            Vertx failingVertxMock = mock(Vertx.class);
            when(failingVertxMock.close()).thenReturn(result);

            Function<VertxOptions, Future<Vertx>> vertxFunction;
            if (ownVertx) {
                vertxFunction = (NeonBee.OwnVertxFactory) (vertxOptions) -> succeededFuture(failingVertxMock);
            } else {
                vertxFunction = (vertxOptions) -> succeededFuture(failingVertxMock);
            }

            NeonBee.create(vertxFunction, HAZELCAST_FACTORY, defaultOptions().clearActiveProfiles(), null)
                    .onComplete(testContext.failing(throwable -> {
                        testContext.verify(() -> {
                            // assert that the original message why the boot failed to start is propagated
                            assertThat(throwable.getMessage()).isEqualTo("Failing Vert.x!");
                            verify(failingVertxMock, times(ownVertx ? 1 : 0)).close();
                            testContext.completeNow();
                        });
                    }));
        }
    }

    @NeonBeeDeployable(profile = CORE)
    private static class CoreVerticle extends AbstractVerticle {
        // empty class (comment needed as spotless formatter works different on windows)
    }

    @NeonBeeDeployable(profile = STABLE)
    private static class StableVerticle extends AbstractVerticle {
        // empty class (comment needed as spotless formatter works different on windows)
    }

    @NeonBeeDeployable(profile = INCUBATOR)
    private static class IncubatorVerticle extends AbstractVerticle {
        // empty class (comment needed as spotless formatter works different on windows)
    }

    @NeonBeeDeployable(profile = CORE, autoDeploy = false)
    private static class SystemVerticle extends AbstractVerticle {
        // empty class (comment needed as spotless formatter works different on windows)
    }
}
