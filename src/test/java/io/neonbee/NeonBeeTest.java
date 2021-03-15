package io.neonbee;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.NeonBeeProfile.ALL;
import static io.neonbee.NeonBeeProfile.CORE;
import static io.neonbee.NeonBeeProfile.INCUBATOR;
import static io.neonbee.NeonBeeProfile.STABLE;
import static io.neonbee.internal.Helper.EMPTY;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import io.neonbee.internal.tracking.MessageDirection;
import io.neonbee.internal.tracking.TrackingDataLoggingStrategy;
import io.neonbee.internal.tracking.TrackingInterceptor;
import io.neonbee.test.base.NeonBeeTestBase;
import io.neonbee.test.helper.WorkingDirectoryBuilder;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryContext;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxTestContext;

class NeonBeeTest extends NeonBeeTestBase {

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
        assertThat(NeonBee.instance(vertx)).isNotNull();
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
        NeonBee.initVertx(new NeonBeeOptions.Mutable()).onComplete(testContext.succeeding(vertx -> {
            assertThat(vertx.isClustered()).isFalse();
            testContext.completeNow();
        }));
    }

    @Test
    @Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
    @DisplayName("Vert.x should start in clustered mode.")
    void testClusterInitialization(VertxTestContext testContext) {
        NeonBee.initVertx(
                new NeonBeeOptions.Mutable().setClustered(true).setClusterConfigResource("hazelcast-local.xml"))
                .onComplete(testContext.succeeding(vertx -> {
                    assertThat(vertx.isClustered()).isTrue();
                    testContext.completeNow();
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
    void testDecorateEventbus() throws Exception {
        Vertx vertx = NeonBeeMockHelper.defaultVertxMock();
        NeonBee neonBee = NeonBeeMockHelper.registerNeonBeeMock(vertx, new NeonBeeOptions.Mutable(),
                new NeonBeeConfig(new JsonObject().put("trackingDataHandlingStrategy", "wrongvalue")));
        EventBus eventBus = mock(EventBus.class);
        when(vertx.eventBus()).thenReturn(eventBus);
        when(eventBus.addInboundInterceptor(Mockito.any(Handler.class))).thenReturn(eventBus);
        when(eventBus.addOutboundInterceptor(Mockito.any(Handler.class))).thenReturn(eventBus);
        ArgumentCaptor<Handler<DeliveryContext<Object>>> inboundHandlerCaptor = ArgumentCaptor.forClass(Handler.class);
        ArgumentCaptor<Handler<DeliveryContext<Object>>> outboundHandlerCaptor = ArgumentCaptor.forClass(Handler.class);
        NeonBee.decorateEventBus(neonBee);
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
        assertThat(NeonBee.filterByAutoDeployAndProfiles(CoreVerticle.class, List.<NeonBeeProfile>of(CORE))).isTrue();
        assertThat(NeonBee.filterByAutoDeployAndProfiles(CoreVerticle.class, List.<NeonBeeProfile>of(CORE, STABLE)))
                .isTrue();
        assertThat(NeonBee.filterByAutoDeployAndProfiles(CoreVerticle.class, List.<NeonBeeProfile>of(STABLE)))
                .isFalse();
        assertThat(NeonBee.filterByAutoDeployAndProfiles(StableVerticle.class, List.<NeonBeeProfile>of(STABLE)))
                .isTrue();
        assertThat(NeonBee.filterByAutoDeployAndProfiles(StableVerticle.class, List.<NeonBeeProfile>of(STABLE, CORE)))
                .isTrue();
        assertThat(NeonBee.filterByAutoDeployAndProfiles(IncubatorVerticle.class, List.<NeonBeeProfile>of(INCUBATOR)))
                .isTrue();
        assertThat(NeonBee.filterByAutoDeployAndProfiles(SystemVerticle.class, List.<NeonBeeProfile>of(CORE)))
                .isFalse();
        assertThat(NeonBee.filterByAutoDeployAndProfiles(SystemVerticle.class, List.<NeonBeeProfile>of(ALL))).isFalse();
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
