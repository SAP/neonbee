package io.neonbee.internal.deploy;

import static com.google.common.truth.Truth.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

import io.neonbee.NeonBeeMockHelper;
import io.neonbee.NeonBeeOptions;
import io.neonbee.internal.DummyVerticleTemplate;
import io.neonbee.internal.NeonBeeModuleJar;
import io.neonbee.internal.SelfFirstClassLoader;
import io.neonbee.test.helper.MockitoHelper;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
public class DeploymentTest {
    private static final String CORRELATION_ID = "correlId";

    @Test
    @DisplayName("should return the identifier and correlationId correct")
    void getIdentifierTest() {
        String identifier = "myIdentifier";
        Deployment deployment = new TestDeployment(null, identifier, CORRELATION_ID);
        assertThat(deployment.getIdentifier()).isEqualTo(identifier);
        assertThat(deployment.correlationId).isEqualTo(CORRELATION_ID);
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("undeploy should be handled correct")
    void undeployTestUnit(VertxTestContext testContext) {
        Checkpoint succeededCheck = testContext.checkpoint();
        Checkpoint failedCheck = testContext.checkpoint();

        Vertx vertxMock = Mockito.mock(Vertx.class);
        Deployment deployment = new TestDeployment(vertxMock, "", CORRELATION_ID);

        Answer<Void> answerSucceeded = MockitoHelper.callHandlerAnswer(1, Future.succeededFuture(null));
        Mockito.doAnswer(answerSucceeded).when(vertxMock).undeploy(Mockito.any(), Mockito.any());
        deployment.undeploy().onComplete(testContext.succeeding(v -> succeededCheck.flag()));

        String expectedErrorMessage = "Lord Citrange";
        Answer<Void> answerFailed = MockitoHelper.callHandlerAnswer(1, Future.failedFuture(expectedErrorMessage));

        Mockito.reset(vertxMock);
        Mockito.doAnswer(answerFailed).when(vertxMock).undeploy(Mockito.any(), Mockito.any());
        deployment.undeploy().onComplete(testContext.failing(t -> {
            testContext.verify(() -> {
                assertThat(t).hasMessageThat().isEqualTo(expectedErrorMessage);
                failedCheck.flag();
            });
        }));
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("undeploy should undeploy a Deployment correct")
    void undeployTestComponent(Vertx vertx, VertxTestContext testContext) throws IOException, ClassNotFoundException {
        String identifierVerticleA = "AVerticle";
        String addressA = "addressA";
        String identifierVerticleB = "BVerticle";
        String addressB = "addressB";

        // Define checks
        Checkpoint responseA = testContext.checkpoint();
        Checkpoint responseB = testContext.checkpoint(2);
        Checkpoint undeployedA = testContext.checkpoint();
        Checkpoint undeployedB = testContext.checkpoint();

        NeonBeeMockHelper.registerNeonBeeMock(vertx, new NeonBeeOptions.Mutable());

        // Create Deployables
        DummyVerticleTemplate dummyVerticleA = new DummyVerticleTemplate(identifierVerticleA, addressA);
        DummyVerticleTemplate dummyVerticleB = new DummyVerticleTemplate(identifierVerticleB, addressB);
        NeonBeeModuleJar verticleJar = new NeonBeeModuleJar("testmodule", List.of(dummyVerticleA, dummyVerticleB));

        SelfFirstClassLoader cl =
                new SelfFirstClassLoader(verticleJar.writeToTempURL(), ClassLoader.getSystemClassLoader());
        Future<Deployable> deployableAFuture =
                Deployable.fromIdentifier(vertx, identifierVerticleA, cl, CORRELATION_ID, null);
        Future<Deployable> deployableBFuture =
                Deployable.fromIdentifier(vertx, identifierVerticleB, cl, CORRELATION_ID, null);

        List<Deployment> deployments = new ArrayList<>();

        // Deploy Deployables
        CompositeFuture.all(deployableAFuture, deployableBFuture).compose(compFuture -> {
            List<Deployable> deployables = compFuture.result().<Deployable>list();
            return CompositeFuture.all(deployables.stream().map(d -> d.deploy(vertx, CORRELATION_ID).future())
                    .collect(Collectors.toList()));
        }).compose(pendingDeployment -> {
            deployments.addAll(pendingDeployment.result().<Deployment>list());

            return Future.<Message<String>>future(fut -> {
                vertx.eventBus().<String>request(addressA, "", fut);
            });
        }).compose(response -> {
            testContext.verify(() -> {
                assertThat(response.body()).isEqualTo(dummyVerticleA.getExpectedResponse());
                responseA.flag();
            });
            return Future.<Message<String>>future(fut -> {
                vertx.eventBus().<String>request(addressB, "", fut);
            });
        }).compose(response -> {
            testContext.verify(() -> {
                assertThat(response.body()).isEqualTo(dummyVerticleB.getExpectedResponse());
                responseB.flag();
            });
            return deployments.get(0).undeploy();
        }).compose(v -> {
            return Future.<Message<String>>future(fut -> {
                vertx.eventBus().<String>request(addressA, "", fut);
            }).otherwise(t -> {
                testContext.verify(() -> {
                    assertThat(t).isInstanceOf(ReplyException.class);
                    assertThat(t).hasMessageThat().contains(addressA);
                    undeployedA.flag();
                });
                return null;
            }).compose(nothing -> {
                return Future.<Message<String>>future(fut -> {
                    vertx.eventBus().<String>request(addressB, "", fut);
                });
            }).compose(response -> {
                // Only "AVerticle is undeployed, BVerticle should still respond"
                testContext.verify(() -> {
                    assertThat(response.body()).isEqualTo(dummyVerticleB.getExpectedResponse());
                    responseB.flag();
                });
                return deployments.get(1).undeploy();
            });
        }).compose(nothing -> {
            return Future.<Message<String>>future(fut -> {
                vertx.eventBus().<String>request(addressB, "", fut);
            }).recover(t -> {
                testContext.verify(() -> {
                    assertThat(t).isInstanceOf(ReplyException.class);
                    assertThat(t).hasMessageThat().contains(addressB);
                    undeployedB.flag();
                });
                return Future.succeededFuture();
            });
        }).onComplete(testContext.succeeding(v -> {}));
    }

    private static class TestDeployment extends Deployment {

        protected TestDeployment(Vertx vertx, String identifier, String correlationId) {
            super(vertx, identifier, correlationId);
        }

        @Override
        public String getDeploymentId() {
            return null;
        }
    }
}
