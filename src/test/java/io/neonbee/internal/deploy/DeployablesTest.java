package io.neonbee.internal.deploy;

import static com.google.common.truth.Truth.assertThat;
import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.junit.Assert.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.neonbee.internal.deploy.DeployableTest.DeployableThing;
import io.vertx.core.Future;

class DeployablesTest {
    @Test
    @DisplayName("test instantiation")
    void testInstantiation() {
        assertThrows(NullPointerException.class, () -> new Deployables(null));
        Deployables deployables = new Deployables(List.of());
        assertThat(deployables.keepPartialDeployment).isFalse();
    }

    @Test
    @DisplayName("test getIdentifier")
    void testGetIdentifier() {
        assertThat(new Deployables(List.of(new DeployableThing("foo"), new DeployableThing("bar"))).getIdentifier())
                .isEqualTo("[Thing(foo),Thing(bar)]");
        assertThat(new Deployables(List.of()).getIdentifier()).isEqualTo("[]");
    }

    @Test
    @DisplayName("test getType")
    void testGetType() {
        assertThat(new Deployables(List.of()).getType()).isEqualTo("Deployables");
    }

    @Test
    @DisplayName("test getDeployables")
    void testGetDeployables() {
        List<Deployable> deployablesList = List.of(new DeployableThing("foo"), new DeployableThing("bar"));
        Deployables deployables = new Deployables(deployablesList);
        assertThat(deployables.getDeployables()).isEqualTo(deployablesList);
        assertThat(deployables.getDeployables()).isNotSameInstanceAs(deployablesList);
    }

    @Test
    @DisplayName("test getDeployables is modifiable (even if the original list was unmodifiable)")
    void testGetDeployablesIsModifiable() {
        assertDoesNotThrow(() -> new Deployables(List.of()).getDeployables().add(new DeployableThing("baz")));
    }

    @Test
    @DisplayName("test toString")
    void testToString() {
        Deployables deployables = new Deployables(List.of(new DeployableThing("foo"), new DeployableThing("bar")));
        assertThat(deployables.toString()).isEqualTo("Deployables([Thing(foo),Thing(bar)])");
    }

    @Test
    @DisplayName("test keepPartialDeployments")
    void testKeepPartialDeployments() {
        Deployables deployables = new Deployables(List.of());
        assertThat(deployables.keepPartialDeployment).isFalse();
        assertThat(deployables.keepPartialDeployment().keepPartialDeployment).isTrue();
        assertThat(deployables.keepPartialDeployment).isTrue();
    }

    @Test
    @DisplayName("test deploy/undeploy")
    void testDeployUndeploy() {
        DeployableThing deployable1 = new DeployableThing("foo");
        DeployableThing deployable2 = new DeployableThing("bar");
        List<DeployableThing> deployableThings = List.of(deployable1, deployable2);
        Deployables deployables = new Deployables(deployableThings);
        PendingDeployment deployment = deployables.deploy(null);

        // regular deploying and undeploying works
        assertThat(deployment.succeeded()).isTrue();
        assertThat(deployable1.deploying).isTrue();
        assertThat(deployable2.deploying).isTrue();
        assertThat(deployable1.undeploying).isFalse();
        assertThat(deployable2.undeploying).isFalse();
        assertThat(deployment.undeploy().succeeded()).isTrue();
        assertThat(deployable1.undeploying).isTrue();
        assertThat(deployable2.undeploying).isTrue();

        // if any deployment fails, the stuff that is already deployed gets undeployed
        deployableThings.forEach(DeployableThing::reset);
        deployable1.deployFuture = failedFuture("fail");
        deployment = deployables.deploy(null);
        assertThat(deployment.succeeded()).isFalse();
        assertThat(deployable1.deploying).isTrue();
        assertThat(deployable2.deploying).isTrue();
        assertThat(deployable1.undeploying).isFalse(); // the deployment of 1 failed, so no need to undeploy
        assertThat(deployable2.undeploying).isTrue();

        // if any deployment fails, the stuff that is already deployed gets undeployed (other way around)
        deployableThings.forEach(DeployableThing::reset);
        deployable2.deployFuture = failedFuture("fail");
        deployment = deployables.deploy(null);
        assertThat(deployment.succeeded()).isFalse();
        assertThat(deployable1.deploying).isTrue();
        assertThat(deployable2.deploying).isTrue();
        assertThat(deployable1.undeploying).isTrue();
        assertThat(deployable2.undeploying).isFalse(); // the deployment of 2 failed, so no need to undeploy

        // undeployments can fail too, but at least we have to attempt them
        deployableThings.forEach(DeployableThing::reset);
        deployable1.undeployFuture = failedFuture("fail");
        deployment = deployables.deploy(null);
        assertThat(deployment.succeeded()).isTrue();
        assertThat(deployable1.deploying).isTrue();
        assertThat(deployable2.deploying).isTrue();
        assertThat(deployable1.undeploying).isFalse();
        assertThat(deployable2.undeploying).isFalse();
        assertThat(deployment.undeploy().failed()).isTrue();
        assertThat(deployable1.undeploying).isTrue();
        assertThat(deployable2.undeploying).isTrue();

        // the original deployment failure should get propagated
        deployableThings.forEach(DeployableThing::reset);
        deployable1.deployFuture = failedFuture("deployfail");
        deployable2.undeployFuture = failedFuture("undeployfail");
        deployment = deployables.deploy(null);
        assertThat(deployment.succeeded()).isFalse();
        assertThat(deployable1.deploying).isTrue();
        assertThat(deployable2.deploying).isTrue();
        assertThat(deployable1.undeploying).isFalse(); // because it failed in the first place
        assertThat(deployable2.undeploying).isTrue();
        assertThat(deployment.cause().getMessage()).isEqualTo("deployfail");

        // undeploy hook should be called and be able to influence the result on success
        deployableThings.forEach(DeployableThing::reset);
        deployment = deployables.deploy(null, undeploymentResult -> {
            return failedFuture("testfailed");
        });
        assertThat(deployment.succeeded()).isTrue();
        assertThat(deployment.undeploy().cause().getMessage()).isEqualTo("testfailed");

        // undeploy hook should be called in any case, but not influence the original result on failure
        deployableThings.forEach(DeployableThing::reset);
        deployable1.undeployFuture = failedFuture("undeployfailed");
        deployment = deployables.deploy(null, undeploymentResult -> {
            return succeededFuture();
        });
        assertThat(deployment.succeeded()).isTrue();
        assertThat(deployment.undeploy().cause().getMessage()).isEqualTo("undeployfailed");
        deployment = deployables.deploy(null, undeploymentResult -> {
            return failedFuture("testfailed");
        });
        assertThat(deployment.succeeded()).isTrue();
        assertThat(deployment.undeploy().cause().getMessage()).isEqualTo("undeployfailed");

        // keep partial deployments
        deployableThings.forEach(DeployableThing::reset);
        deployable1.deployFuture = failedFuture("fail");
        deployables.keepPartialDeployment();
        deployment = deployables.deploy(null);
        assertThat(deployment.succeeded()).isFalse();
        assertThat(deployable1.deploying).isTrue();
        assertThat(deployable2.deploying).isTrue();
        assertThat(deployable1.undeploying).isFalse();
        assertThat(deployable2.undeploying).isFalse();
        assertThat(deployment.undeploy().succeeded()).isTrue();
        assertThat(deployable1.undeploying).isFalse(); // because it failed in the first place
        assertThat(deployable2.undeploying).isTrue();
        deployables.keepPartialDeployment = false;
    }

    @Test
    @DisplayName("test fromDeployables")
    void testFromDeployables() {
        List<Deployable> deployablesList = List.of(new DeployableThing("foo"), new DeployableThing("bar"));
        Deployables deployables = new Deployables(deployablesList);
        assertThat(deployables.keepPartialDeployment).isFalse();
        assertThat(Deployables
                .fromDeployables(deployablesList.stream().map(Future::succeededFuture).collect(Collectors.toList()))
                .result().getDeployables()).isEqualTo(deployablesList);
        assertThat(Deployables.fromDeployables(List.of(succeededFuture(), failedFuture("foo"))).failed()).isTrue();
    }
}
