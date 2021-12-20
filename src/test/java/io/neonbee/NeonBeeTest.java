package io.neonbee;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.NeonBeeMockHelper.defaultVertxMock;
import static io.neonbee.NeonBeeMockHelper.registerNeonBeeMock;
import static io.neonbee.NeonBeeProfile.ALL;
import static io.neonbee.NeonBeeProfile.CORE;
import static io.neonbee.NeonBeeProfile.INCUBATOR;
import static io.neonbee.NeonBeeProfile.NO_WEB;
import static io.neonbee.NeonBeeProfile.STABLE;
import static io.neonbee.internal.helper.StringHelper.EMPTY;
import static io.neonbee.test.helper.OptionsHelper.defaultOptions;
import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
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
import org.mockito.MockedConstruction;
import org.mockito.Mockito;

import io.neonbee.config.NeonBeeConfig;
import io.neonbee.internal.tracking.MessageDirection;
import io.neonbee.internal.tracking.TrackingDataLoggingStrategy;
import io.neonbee.internal.tracking.TrackingInterceptor;
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

class NeonBeeTest extends NeonBeeTestBase {
    private Vertx vertx;

    @Override
    protected void adaptOptions(TestInfo testInfo, NeonBeeOptions.Mutable options) {
        options.addActiveProfile(NO_WEB);
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
    @DisplayName("Vert.x should start in non-clustered mode. ")
    void testStandaloneInitialization(VertxTestContext testContext) {
        NeonBee.newVertx(new VertxOptions(), defaultOptions().clearActiveProfiles())
                .onComplete(testContext.succeeding(vertx -> {
                    testContext.verify(() -> {
                        assertThat((this.vertx = vertx).isClustered()).isFalse();
                        testContext.completeNow();
                    });
                }));
    }

    @Test
    @Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
    @DisplayName("Vert.x should start in clustered mode.")
    void testClusterInitialization(VertxTestContext testContext) {
        NeonBee.newVertx(new VertxOptions(), defaultOptions().clearActiveProfiles().setClustered(true)
                .setClusterConfigResource("hazelcast-local.xml")).onComplete(testContext.succeeding(vertx -> {
                    testContext.verify(() -> {
                        assertThat((this.vertx = vertx).isClustered()).isTrue();
                        testContext.completeNow();
                    });
                }));
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
        try (MockedConstruction<NeonBee> mocked =
                mockConstruction(NeonBee.class, (mock, context) -> when(mock.loadConfig())
                        .thenReturn(failedFuture(new RuntimeException("Failing Vert.x!"))))) {
            Vertx failingVertxMock = mock(Vertx.class);
            when(failingVertxMock.close()).thenReturn(result);

            Function<VertxOptions, Future<Vertx>> vertxFunction;
            if (ownVertx) {
                vertxFunction = (NeonBee.OwnVertxFactory) (vertxOptions) -> succeededFuture(failingVertxMock);
            } else {
                vertxFunction = (vertxOptions) -> succeededFuture(failingVertxMock);
            }

            NeonBee.create(vertxFunction, defaultOptions().clearActiveProfiles())
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
