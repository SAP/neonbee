package io.neonbee.internal.deploy;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.condition.OS.WINDOWS;

import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import io.neonbee.internal.DummyVerticleTemplate;
import io.neonbee.internal.NeonBeeModuleJar;
import io.neonbee.internal.verticle.MetricsVerticle;
import io.neonbee.internal.verticle.ServerVerticle;
import io.neonbee.test.base.NeonBeeTestBase;
import io.neonbee.test.helper.MockitoHelper;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxTestContext;

public class DeployableTest extends NeonBeeTestBase {
    private static final String IDENTIFIER_COMPANY_1 = "io.verticle.Company1Verticle";

    private static final String IDENTIFIER_COMPANY_2 = "io.verticle.Company2Verticle";

    private static final String IDENTIFIER_COMPANY_3 = "io.verticle.Company3Verticle";

    private static final String CORRELATION_ID = "correlId";

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("readVerticleConfig should return DeploymentOptions correct")
    public void testReadVerticleConfig(Vertx vertx, VertxTestContext testContext) throws IOException {
        Checkpoint notExistingCheckpoint = testContext.checkpoint();
        Checkpoint existingYAMLCheckpoint = testContext.checkpoint();
        Checkpoint existingJSONCheckpoint = testContext.checkpoint();
        Checkpoint badJSONCheckpoint = testContext.checkpoint();

        Path configDir = getNeonBee().getOptions().getConfigDirectory();
        Path company1ConfigPath = configDir.resolve(IDENTIFIER_COMPANY_1 + ".yaml");
        Path company2ConfigPath = configDir.resolve(IDENTIFIER_COMPANY_2 + ".json");
        Path company3ConfigPath = configDir.resolve(IDENTIFIER_COMPANY_3 + ".json");
        DeploymentOptions expected = new DeploymentOptions().setHa(true).setMaxWorkerExecuteTime(1234);

        vertx.fileSystem().writeFileBlocking(company1ConfigPath.toString(), Buffer.buffer(
                new YAMLMapper().writeValueAsString(new ObjectMapper().readTree(expected.toJson().toString()))));
        vertx.fileSystem().writeFileBlocking(company2ConfigPath.toString(), expected.toJson().toBuffer());
        vertx.fileSystem().writeFileBlocking(company3ConfigPath.toString(), Buffer.buffer("{abc"));

        Deployable.readVerticleConfig(vertx, "ads", CORRELATION_ID, null).compose(emptyOpts -> {
            testContext.verify(() -> {
                assertThat(emptyOpts.toJson()).isEqualTo(new DeploymentOptions().toJson());
                notExistingCheckpoint.flag();
            });
            return Deployable.readVerticleConfig(vertx, IDENTIFIER_COMPANY_1, CORRELATION_ID, null);
        }).compose(company1Options -> {
            testContext.verify(() -> {
                assertThat(company1Options.toJson()).isEqualTo(expected.toJson());
                existingYAMLCheckpoint.flag();
            });
            return Deployable.readVerticleConfig(vertx, IDENTIFIER_COMPANY_2, CORRELATION_ID, null);
        }).compose(company2Options -> {
            testContext.verify(() -> {
                assertThat(company2Options.toJson()).isEqualTo(expected.toJson());
                existingJSONCheckpoint.flag();
            });
            return Deployable.readVerticleConfig(vertx, IDENTIFIER_COMPANY_3, CORRELATION_ID, null).otherwise(t -> {
                testContext.verify(() -> {
                    assertThat(t).isInstanceOf(DecodeException.class);
                    badJSONCheckpoint.flag();
                });
                return null;
            });
        }).onComplete(testContext.succeeding(v -> {}));
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("readVerticleConfig should add default options to DeploymentOptions")
    public void testReadVerticleDefaultConfig(Vertx vertx, VertxTestContext testContext) throws IOException {
        Path configPath = getNeonBee().getOptions().getConfigDirectory().resolve(IDENTIFIER_COMPANY_1 + ".json");

        JsonObject defaultOptions = new JsonObject().put("instances", 42).put("maxWorkerExecuteTime", 9001)// over 9000!
                .put("config", new JsonObject().put("test1", "foo").put("test_merge", new JsonObject()
                        .put("only_this_one", "should be overridden").put("and_this", "should also be overridden")));
        JsonObject persistedOptions =
                new JsonObject().put("ha", true).put("maxWorkerExecuteTime", 1337).put("config", new JsonObject()
                        .put("test2", "bar").put("test_merge", new JsonObject().put("only_this_one", "is expected")));

        DeploymentOptions expected = new DeploymentOptions().setHa(true).setMaxWorkerExecuteTime(1337).setInstances(42)
                .setConfig(new JsonObject().put("test1", "foo").put("test2", "bar").put("test_merge",
                        new JsonObject().put("only_this_one", "is expected")));

        vertx.fileSystem().writeFileBlocking(configPath.toString(), persistedOptions.toBuffer());

        Deployable.readVerticleConfig(vertx, IDENTIFIER_COMPANY_1, CORRELATION_ID, defaultOptions)
                .onComplete(testContext.succeeding(opts -> {
                    // toJson is required, because DeploymentOptions doesn't implement equals
                    testContext.verify(() -> assertThat(opts.toJson()).isEqualTo(expected.toJson()));
                    testContext.completeNow();
                }));

    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("deploy should fail if deployment failed")
    void deployTestUnit(VertxTestContext testContext) throws IOException {
        Checkpoint failedCheck = testContext.checkpoint();

        Vertx vertxMock = Mockito.mock(Vertx.class);
        Deployable deployable = new Deployable(ServerVerticle.class, null);

        String expectedErrorMessage = "Lord Citrange";
        Answer<Void> answerFailed = MockitoHelper.callHandlerAnswer(2, Future.failedFuture(expectedErrorMessage));

        Mockito.doAnswer(answerFailed).when(vertxMock).deployVerticle(ArgumentMatchers.<Class<? extends Verticle>>any(),
                Mockito.any(), Mockito.any());
        deployable.deploy(vertxMock, CORRELATION_ID).future().onComplete(testContext.failing(t -> {
            testContext.verify(() -> {
                assertThat(t).hasMessageThat().isEqualTo(expectedErrorMessage);
                failedCheck.flag();
            });
        }));
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("deploy should deploy all verticle in a Deployable correct")
    @DisabledOnOs(WINDOWS)
    public void deployTestComponent(Vertx vertx, VertxTestContext testContext)
            throws IOException, ClassNotFoundException {
        String identifierVerticleA = "AVerticle";
        String addressA = "addressA";
        String identifierVerticleB = "BVerticle";
        String addressB = "addressB";

        // Define checks
        Checkpoint responseA = testContext.checkpoint();
        Checkpoint responseB = testContext.checkpoint();

        // Create deployables
        DummyVerticleTemplate dummyVerticleA = new DummyVerticleTemplate(identifierVerticleA, addressA);
        DummyVerticleTemplate dummyVerticleB = new DummyVerticleTemplate(identifierVerticleB, addressB);
        NeonBeeModuleJar verticleJar = new NeonBeeModuleJar("testmodule", List.of(dummyVerticleA, dummyVerticleB));

        URLClassLoader cl = new URLClassLoader(verticleJar.writeToTempURL(), ClassLoader.getSystemClassLoader());
        Future<Deployable> deployableAFuture =
                Deployable.fromIdentifier(vertx, identifierVerticleA, cl, CORRELATION_ID, null);
        Future<Deployable> deployableBFuture =
                Deployable.fromIdentifier(vertx, identifierVerticleB, cl, CORRELATION_ID, null);

        // Deploy Deployables
        CompositeFuture.all(deployableAFuture, deployableBFuture).compose(compFuture -> {
            List<Deployable> deployables = compFuture.result().<Deployable>list();
            return CompositeFuture.all(deployables.stream().map(d -> d.deploy(vertx, CORRELATION_ID).future())
                    .collect(Collectors.toList()));
        }).compose(pendingDeployments -> {
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
            return Future.succeededFuture();
        }).onComplete(testContext.succeeding(v -> {}));
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("fromIdentifier should create Deployable correct")
    void fromIdentifierTest(Vertx vertx, VertxTestContext testContext) {
        String identifier = MetricsVerticle.class.getName();
        Deployable.fromIdentifier(vertx, identifier, ClassLoader.getSystemClassLoader(), CORRELATION_ID, null)
                .onComplete(testContext.succeeding(deployable -> {
                    testContext.verify(() -> assertThat(deployable.getIdentifier()).isEqualTo(identifier));
                    testContext.completeNow();
                }));
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("deploy should also work with passed verticle instance")
    void deployWithInstanceTest(Vertx vertx, VertxTestContext testContext) {
        Checkpoint deployed = testContext.checkpoint();

        class DummyVerticle extends AbstractVerticle {

            @Override
            public void start(Promise<Void> startFuture) throws Exception {
                deployed.flag();
            }
        }

        Deployable.fromVerticle(vertx, new DummyVerticle(), CORRELATION_ID, null)
                .compose(deployable -> deployable.deploy(vertx, CORRELATION_ID).future())
                .onComplete(testContext.succeeding(v -> {}));
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("fromVerticle should create Deployable correct")
    void fromVerticleTest(Vertx vertx, VertxTestContext testContext) {
        Verticle dummy = new AbstractVerticle() {};

        Deployable.fromVerticle(vertx, dummy, CORRELATION_ID, null).onComplete(testContext.succeeding(deployable -> {
            testContext.verify(() -> assertThat(deployable.verticleInstance).isSameInstanceAs(dummy));
            testContext.completeNow();
        }));
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("fromIdentifier should fail if class is not found")
    void fromIdentifierThrowTest(Vertx vertx, VertxTestContext testContext) throws ClassNotFoundException {
        ClassLoader classLoaderMock = Mockito.mock(ClassLoader.class);
        Mockito.doThrow(ClassNotFoundException.class).when(classLoaderMock).loadClass(Mockito.any());
        Deployable.fromIdentifier(vertx, "a class name", classLoaderMock, CORRELATION_ID, null)
                .onComplete(testContext.failing(t -> {
                    testContext.verify(() -> assertThat(t).isInstanceOf(ClassNotFoundException.class));
                    testContext.completeNow();
                }));
    }
}
