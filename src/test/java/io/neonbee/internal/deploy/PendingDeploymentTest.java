package io.neonbee.internal.deploy;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class PendingDeploymentTest {
    private static final String CORRELATION_ID = "correlId";

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    void undeployTest(VertxTestContext testContext) {
        Checkpoint checkUnresolved = testContext.checkpoint();
        Checkpoint checkFailed = testContext.checkpoint();
        Checkpoint checkSucceeded = testContext.checkpoint();

        PendingDeployment unresolved =
                new PendingDeployment(null, "", CORRELATION_ID, Promise.<String>promise().future());
        unresolved.undeploy().onComplete(testContext.failing(t -> {
            testContext.verify(() -> assertThat(t).isInstanceOf(IllegalStateException.class));
            checkUnresolved.flag();
        }));

        PendingDeployment failed = new PendingDeployment(null, "", CORRELATION_ID, Future.failedFuture(""));
        failed.undeploy().onComplete(testContext.succeeding(response -> checkFailed.flag()));

        Vertx vertxSpy = Mockito.spy(Vertx.class);
        PendingDeployment succeeded =
                new PendingDeployment(vertxSpy, "", CORRELATION_ID, Future.succeededFuture("deploymentId"));
        Mockito.doNothing().when(vertxSpy).undeploy(Mockito.anyString(), Mockito.any());
        succeeded.undeploy();
        // Will work, because at the moment undeploy is complete synchronous
        testContext.verify(() -> {
            Mockito.verify(vertxSpy).undeploy(Mockito.anyString(), Mockito.any());
            checkSucceeded.flag();
        });
    }

    @Test
    void getDeploymentIdTest() {
        String deploymentId = "Hodor";
        PendingDeployment deployment =
                new PendingDeployment(null, "", CORRELATION_ID, Future.succeededFuture(deploymentId));
        assertThat(deployment.getDeploymentId()).isEqualTo(deploymentId);
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    void setHandlerTest(VertxTestContext testContext) {
        Promise<String> deployPromise = Promise.promise();
        new PendingDeployment(null, "", CORRELATION_ID, deployPromise.future()).future()
                .onComplete(testContext.succeedingThenComplete());
        deployPromise.complete();
    }

    @Test
    @DisplayName("test complete, succeeded, isComplete and tryComplete methods")
    void completeMethodsTest() {
        PendingDeployment deployment = new PendingDeployment(null, "", CORRELATION_ID, Future.succeededFuture());
        assertThat(deployment.future().isComplete()).isTrue();
        assertThat(deployment.future().succeeded()).isTrue();

        String errorMessage = "Cannot complete pending deployments.";

        Throwable error = assertThrows(IllegalStateException.class, () -> deployment.complete(null));
        assertThat(error).hasMessageThat().isEqualTo(errorMessage);

        error = assertThrows(IllegalStateException.class, () -> deployment.complete());
        assertThat(error).hasMessageThat().isEqualTo(errorMessage);

        error = assertThrows(IllegalStateException.class, () -> deployment.tryComplete(null));
        assertThat(error).hasMessageThat().isEqualTo(errorMessage);

        error = assertThrows(IllegalStateException.class, () -> deployment.tryComplete());
        assertThat(error).hasMessageThat().isEqualTo(errorMessage);
    }

    @Test
    @DisplayName("test fail, failed and tryFail methods")
    void failMethodsTest() {

        PendingDeployment deployment = new PendingDeployment(null, "", CORRELATION_ID, Future.failedFuture("Hodor"));
        assertThat(deployment.future().failed()).isTrue();
        assertThat(deployment.future().cause()).hasMessageThat().isEqualTo("Hodor");

        String errorMessage = "Cannot fail pending deployments.";

        Throwable error = assertThrows(IllegalStateException.class, () -> deployment.fail((Throwable) null));
        assertThat(error).hasMessageThat().isEqualTo(errorMessage);

        error = assertThrows(IllegalStateException.class, () -> deployment.fail((String) null));
        assertThat(error).hasMessageThat().isEqualTo(errorMessage);

        error = assertThrows(IllegalStateException.class, () -> deployment.tryFail((Throwable) null));
        assertThat(error).hasMessageThat().isEqualTo(errorMessage);

        error = assertThrows(IllegalStateException.class, () -> deployment.tryFail((String) null));
        assertThat(error).hasMessageThat().isEqualTo(errorMessage);
    }

    @Test
    void resultTest() {
        PendingDeployment failed = new PendingDeployment(null, "", CORRELATION_ID, Future.failedFuture("Hodor"));
        assertThat(failed.future().result()).isNull();

        PendingDeployment succeeded = new PendingDeployment(null, "", CORRELATION_ID, Future.succeededFuture());
        assertThat(succeeded.future().result()).isSameInstanceAs(succeeded);
    }

    @Test
    void handleTest() {
        PendingDeployment deployment = new PendingDeployment(null, "", CORRELATION_ID, Future.succeededFuture());
        Throwable error = assertThrows(IllegalStateException.class, () -> deployment.handle(null));
        assertThat(error).hasMessageThat().isEqualTo("Cannot handle pending deployments.");
    }
}
