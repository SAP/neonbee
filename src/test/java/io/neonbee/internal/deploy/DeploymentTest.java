package io.neonbee.internal.deploy;

import static com.google.common.truth.Truth.assertThat;
import static io.vertx.core.Future.succeededFuture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.neonbee.NeonBee;
import io.neonbee.internal.deploy.DeployableTest.DeployableThing;
import io.vertx.core.Future;
import io.vertx.junit5.VertxExtension;

@ExtendWith(VertxExtension.class)
class DeploymentTest {
    @Test
    @DisplayName("should return the deployable")
    void getDeployableTest() {
        DeployableThing deployable = new DeployableThing("test");
        Deployment deployment = new TestDeployment(null, deployable);
        assertThat(deployment.getDeployable()).isSameInstanceAs(deployable);
    }

    @Test
    @DisplayName("test deployment ID")
    void getDeploymentIdTest() {
        DeployableThing deployable = new DeployableThing("test");
        Deployment deployment = new TestDeployment(null, deployable);
        assertThat(deployment.getDeploymentId()).isEqualTo("test@" + Integer.toHexString(deployment.hashCode()));
    }

    private static class TestDeployment extends Deployment {
        protected TestDeployment(NeonBee neonBee, Deployable deployable) {
            super(neonBee, deployable);
        }

        @Override
        public Future<Void> undeploy() {
            return succeededFuture();
        }
    }
}
