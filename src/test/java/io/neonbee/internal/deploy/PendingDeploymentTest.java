package io.neonbee.internal.deploy;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.NeonBeeMockHelper.defaultVertxMock;
import static io.neonbee.NeonBeeMockHelper.registerNeonBeeMock;
import static io.neonbee.internal.deploy.DeploymentTest.newNeonBeeMockForDeployment;
import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import io.neonbee.NeonBee;
import io.neonbee.config.NeonBeeConfig;
import io.neonbee.internal.deploy.DeployableTest.DeployableThing;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;

class PendingDeploymentTest {
    @Test
    void undeployTest() {
        PendingDeployment unresolved = new TestPendingDeployment(Promise.<String>promise().future());
        assertThat(unresolved.undeploy().cause()).isInstanceOf(IllegalStateException.class);

        PendingDeployment failed = new TestPendingDeployment(failedFuture(""));
        assertThat(failed.undeploy().succeeded()).isTrue();

        TestPendingDeployment succeeded = new TestPendingDeployment(succeededFuture("deploymentId"));
        assertThat(succeeded.undeploy().succeeded()).isTrue();
        assertThat(succeeded.undeployDeploymentId).isEqualTo("deploymentId");
    }

    @Test
    void getDeploymentIdTest() {
        String deploymentId = "Hodor";
        PendingDeployment deployment = new TestPendingDeployment(succeededFuture(deploymentId));
        assertThat(deployment.getDeployment().result().getDeployable().getIdentifier()).isEqualTo(deploymentId);
    }

    @Test
    void deploymentTimeoutTest() {
        Vertx vertxMock = defaultVertxMock();
        // we have to disable / ignore timers, as otherwise the time-out would apply every time with the mock
        doAnswer(invocation -> -1L).when(vertxMock).setTimer(anyLong(), any());

        NeonBee neonBeeMock = registerNeonBeeMock(vertxMock, new NeonBeeConfig().setDeploymentTimeout(-1));
        new TestPendingDeployment(neonBeeMock, Promise.<String>promise().future());
        verify(vertxMock, times(0)).setTimer(anyLong(), any());

        reset(vertxMock);
        neonBeeMock = registerNeonBeeMock(vertxMock, new NeonBeeConfig().setDeploymentTimeout(42));
        new TestPendingDeployment(neonBeeMock, Promise.<String>promise().future());
        verify(vertxMock).setTimer(eq(42000L), any());

        reset(vertxMock);
        neonBeeMock = registerNeonBeeMock(vertxMock, new NeonBeeConfig().setDeploymentTimeout(1337));
        new TestPendingDeployment(neonBeeMock, "models", Promise.<String>promise().future());
        verify(vertxMock).setTimer(eq(1337000L), any());

        reset(vertxMock);
        neonBeeMock = registerNeonBeeMock(vertxMock, new NeonBeeConfig().setModelsDeploymentTimeout(48));
        new TestPendingDeployment(neonBeeMock, "models", Promise.<String>promise().future());
        verify(vertxMock).setTimer(eq(48000L), any());
        new TestPendingDeployment(neonBeeMock, "verticle", Promise.<String>promise().future());
        verify(vertxMock).setTimer(eq(TimeUnit.SECONDS.toMillis(NeonBeeConfig.DEFAULT_DEPLOYMENT_TIMEOUT)), any());

        reset(vertxMock);
        neonBeeMock = registerNeonBeeMock(vertxMock, new NeonBeeConfig().setModuleDeploymentTimeout(-1));
        new TestPendingDeployment(neonBeeMock, "module", Promise.<String>promise().future());
        verify(vertxMock, times(0)).setTimer(anyLong(), any());

        reset(vertxMock);
        neonBeeMock = registerNeonBeeMock(vertxMock, new NeonBeeConfig().setVerticleDeploymentTimeout(10)
                .setVerticleDeploymentTimeout(null));
        new TestPendingDeployment(neonBeeMock, "verticle", Promise.<String>promise().future());
        verify(vertxMock).setTimer(eq(TimeUnit.SECONDS.toMillis(NeonBeeConfig.DEFAULT_DEPLOYMENT_TIMEOUT)), any());

    }

    private static class TestPendingDeployment extends PendingDeployment {
        public String undeployDeploymentId;

        protected TestPendingDeployment(Future<String> deployFuture) {
            this(newNeonBeeMockForDeployment(), deployFuture);
        }

        protected TestPendingDeployment(NeonBee neonBee, Future<String> deployFuture) {
            this(neonBee, (String) null, deployFuture);
        }

        protected TestPendingDeployment(NeonBee neonBee, String deployableType, Future<String> deployFuture) {
            super(neonBee, new DeployableThing("Hodor") {
                @Override
                public String getType() {
                    return deployableType == null ? super.getType() : deployableType;
                }
            }, deployFuture);
        }

        @Override
        public Future<Void> undeploy(String deploymentId) {
            undeployDeploymentId = deploymentId;
            return succeededFuture();
        }
    }
}
