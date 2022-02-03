package io.neonbee.internal.deploy;

import static com.google.common.truth.Truth.assertThat;
import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.function.Function;

import org.junit.jupiter.api.Test;

import io.neonbee.internal.deploy.DeployableTest.DeployableThing;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.impl.future.FutureInternal;

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
        assertThat(deployment.getDeploymentId()).isEqualTo(deploymentId);
    }

    @Test
    void setHandlerTest() {
        Promise<String> deployPromise = Promise.promise();
        PendingDeployment deployment = new TestPendingDeployment(deployPromise.future());
        assertThat(deployment.isComplete()).isFalse();
        deployPromise.complete("test");
        assertThat(deployment.isComplete()).isTrue();
        assertThat(deployment.succeeded()).isTrue();
        assertThat(deployment.result().getDeploymentId()).isEqualTo("test");
    }

    @Test
    @SuppressWarnings("unchecked")
    void testFutureInterface() {
        FutureInternal<String> futureMock = mock(FutureInternal.class);
        doReturn(futureMock).when(futureMock).map((Function<String, ?>) any());
        doReturn(futureMock).when(futureMock).map((Deployable) any());
        doReturn(futureMock).when(futureMock).onSuccess(any());
        doReturn(futureMock).when(futureMock).onFailure(any());

        PendingDeployment deployment = new TestPendingDeployment(futureMock);
        verify(futureMock).map((Function<String, ?>) any());
        verify(futureMock).onSuccess(any());
        verify(futureMock).onFailure(any());

        deployment.isComplete();
        verify(futureMock).isComplete();

        deployment.onComplete(null);
        verify(futureMock).onComplete(any());

        deployment.result();
        verify(futureMock).succeeded();
        clearInvocations(futureMock);

        deployment.cause();
        verify(futureMock).cause();

        deployment.succeeded();
        verify(futureMock).succeeded();

        deployment.failed();
        verify(futureMock).failed();

        deployment.compose(null);
        verify(futureMock).compose(any(), any());

        deployment.transform(null);
        verify(futureMock).transform(any());

        deployment.eventually(null);
        verify(futureMock).eventually(any());

        clearInvocations(futureMock);
        deployment.map((Function<String, ?>) null);
        verify(futureMock, atLeastOnce()).map(any(Object.class));

        clearInvocations(futureMock);
        deployment.map((String) null);
        verify(futureMock, atLeastOnce()).map(any(Object.class));

        deployment.otherwise((Function<Throwable, Deployment>) null);
        verify(futureMock).otherwise((Function<Throwable, String>) any());

        deployment.otherwise((Deployment) null);
        verify(futureMock).otherwise((String) any());

        deployment.context();
        verify(futureMock).context();

        deployment.addListener(null);
        verify(futureMock).addListener(any());
    }

    private static class TestPendingDeployment extends PendingDeployment {
        public String undeployDeploymentId;

        protected TestPendingDeployment(Future<String> deployFuture) {
            super(null, new DeployableThing("foo"), deployFuture);
        }

        @Override
        public Future<Void> undeploy(String deploymentId) {
            undeployDeploymentId = deploymentId;
            return succeededFuture();
        }
    }
}
