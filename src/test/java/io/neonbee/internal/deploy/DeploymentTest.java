package io.neonbee.internal.deploy;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.NeonBeeMockHelper.defaultVertxMock;
import static io.neonbee.NeonBeeMockHelper.registerNeonBeeMock;
import static io.vertx.core.Future.succeededFuture;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.neonbee.NeonBee;
import io.neonbee.NeonBeeOptions;
import io.neonbee.config.NeonBeeConfig;
import io.neonbee.internal.deploy.DeployableTest.DeployableThing;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;

@ExtendWith(VertxExtension.class)
class DeploymentTest {

    @Test
    @DisplayName("should return the deployable")
    void getDeployableTest() {
        DeployableThing deployable = new DeployableThing("test");
        Deployment deployment = new TestDeployment(deployable);
        assertThat(deployment.getDeployable()).isSameInstanceAs(deployable);
    }

    @Test
    @DisplayName("test deployment ID")
    void getDeploymentIdTest() {
        DeployableThing deployable = new DeployableThing("test");
        Deployment deployment = new TestDeployment(deployable);
        assertThat(deployment.getDeploymentId()).isEqualTo("test@" + Integer.toHexString(deployment.hashCode()));
    }

    private static class TestDeployment extends Deployment {
        protected TestDeployment(Deployable deployable) {
            super(newNeonBeeMockForDeployment(), deployable);
        }

        @Override
        public Future<Void> undeploy() {
            return succeededFuture();
        }
    }

    public static NeonBee newNeonBeeMockForDeployment() {
        return newNeonBeeMockForDeployment(new NeonBeeOptions.Mutable());
    }

    public static NeonBee newNeonBeeMockForDeployment(NeonBeeOptions options) {
        Vertx vertxMock = defaultVertxMock();
        // we have to disable / ignore timers, as otherwise the time-out would apply every time with the mock
        doAnswer(invocation -> -1L).when(vertxMock).setTimer(anyLong(), any());

        return registerNeonBeeMock(vertxMock, options, new NeonBeeConfig().setDeploymentTimeout(-1));
    }
}
