package io.neonbee.internal.deploy;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.internal.deploy.DeploymentTest.newNeonBeeMockForDeployment;
import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.neonbee.NeonBee;
import io.vertx.core.Future;

class DeployableTest {
    @Test
    @DisplayName("test Deployable getters")
    void testGetters() {
        Deployable deployable1 = new DeployableThing("foo");
        assertThat(deployable1.getIdentifier()).isEqualTo("foo");
        assertThat(deployable1.getType()).isEqualTo("Thing");

        Deployable deployable2 = new DeployableThing("foo");
        assertThat(deployable2.getIdentifier()).isEqualTo("foo");
        assertThat(deployable2.getType()).isEqualTo("Thing");
    }

    @Test
    @DisplayName("test toString of Deployable")
    void testToString() {
        assertThat(new DeployableThing("foo").toString()).isEqualTo("Thing(foo)");
        assertThat(new DeployableThing("bar").toString()).isEqualTo("Thing(bar)");
    }

    @Test
    @DisplayName("test deploy/undeploy of Deployable")
    void testDeployUndeploy() {
        DeployableThing deployable = new DeployableThing("foo");
        assertThat(deployable.deploy().getDeployment().succeeded()).isTrue();
        assertThat(deployable.deploy().undeploy().succeeded()).isTrue();

        deployable.undeployFuture = failedFuture("foo");
        assertThat(deployable.deploy().getDeployment().succeeded()).isTrue();
        assertThat(deployable.deploy().undeploy().failed()).isTrue();

        deployable.deployFuture = failedFuture("foo");
        assertThat(deployable.deploy().getDeployment().failed()).isTrue();
    }

    static class DeployableThing extends Deployable {
        public String identifier;

        public Future<String> deployFuture;

        public Future<Void> undeployFuture;

        public boolean deploying;

        public boolean undeploying;

        DeployableThing(String identifier) {
            super();
            this.identifier = identifier;
            reset();
        }

        public final void reset() {
            this.deploying = this.undeploying = false;
            this.deployFuture = succeededFuture();
            this.undeployFuture = succeededFuture();
        }

        @Override
        public String getIdentifier() {
            return identifier;
        }

        public PendingDeployment deploy() {
            return deploy(newNeonBeeMockForDeployment());
        }

        @Override
        public PendingDeployment deploy(NeonBee neonBee) {
            deploying = true;
            return new PendingDeployment(neonBee, this, deployFuture) {
                @Override
                protected Future<Void> undeploy(String deploymentId) {
                    undeploying = true;
                    return undeployFuture;
                }
            };
        }
    }
}
