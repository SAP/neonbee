package io.neonbee.internal.deploy;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.internal.deploy.NeonBeeModule.NEONBEE_MODULE;
import static io.neonbee.test.helper.FileSystemHelper.createTempDirectory;
import static io.neonbee.test.helper.ResourceHelper.TEST_RESOURCES;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.google.common.truth.Correspondence;
import com.google.common.truth.Truth8;

import io.neonbee.entity.EntityModel;
import io.neonbee.entity.EntityModelManager;
import io.neonbee.internal.BasicJar;
import io.neonbee.internal.DummyVerticleTemplate;
import io.neonbee.internal.NeonBeeModuleJar;
import io.neonbee.test.base.NeonBeeTestBase;
import io.vertx.core.Future;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import io.vertx.core.impl.NoStackTraceThrowable;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxTestContext;

@SuppressWarnings("StreamResourceLeak") // Ignore in Errorprone -> Because it is just a test
class NeonBeeModuleTest extends NeonBeeTestBase {
    private static final String CORRELATION_ID = "correlId";

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("test getters")
    void gettersTest(Vertx vertx) {
        NeonBeeModule module =
                new NeonBeeModule(vertx, "testmodule", CORRELATION_ID, null, null, List.of(), Map.of(), Map.of());
        assertThat(module.getCorrelationId()).isEqualTo(CORRELATION_ID);
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("Parse JAR and create correct NeonBeeModule")
    void fromJarTest(Vertx vertx, VertxTestContext testContext) throws IOException {
        // Create verticle classes
        String classNameA = "ClassA";
        String classNameB = "ClassB";
        DummyVerticleTemplate verticleA = new DummyVerticleTemplate(classNameA, "doesn't matter");
        DummyVerticleTemplate verticleB = new DummyVerticleTemplate(classNameB, "doesn't matter");
        // Due to the fact that the classes will be generated at runtime, we can't use a real class reference here.
        List<String> expectedClassNames = List.of(classNameA, classNameB);

        // Create models
        String userModelContent = "userCsn";
        String productModelContent = "productCsn";
        Map.Entry<String, byte[]> userModel = Map.entry("models/User.csn", userModelContent.getBytes(UTF_8));
        Map.Entry<String, byte[]> productModel = Map.entry("models/Product.csn", productModelContent.getBytes(UTF_8));
        Map<String, byte[]> models = Map.ofEntries(userModel, productModel);

        String userExtModelContent = "userEdmx";
        String productExtModelContent = "productEdmx";
        Map.Entry<String, byte[]> userExtModel = Map.entry("models/User.edmx", userExtModelContent.getBytes(UTF_8));
        Map.Entry<String, byte[]> productExtModel =
                Map.entry("models/Product.edmx", productExtModelContent.getBytes(UTF_8));
        Map<String, byte[]> extModels = Map.ofEntries(userExtModel, productExtModel);

        // Create NeonBeeModuleJar
        NeonBeeModuleJar neonBeeModuleJar =
                new NeonBeeModuleJar("testmodule", List.of(verticleA, verticleB), models, extModels);

        NeonBeeModule.fromJar(vertx, neonBeeModuleJar.writeToTempPath(), CORRELATION_ID)
                .onComplete(testContext.succeeding(neonBeeModule -> {
                    testContext.verify(() -> {
                        @SuppressWarnings("PMD")
                        BiConsumer<Map<String, byte[]>, Map<String, byte[]>> isNotEqTo = (moduleModels, expModels) -> {
                            assertThat(moduleModels)
                                    .comparingValuesUsing(
                                            Correspondence.<byte[], byte[]>from(Arrays::equals, "is not equal to"))
                                    .containsAtLeastEntriesIn(expModels);
                        };

                        isNotEqTo.accept(neonBeeModule.models, models);
                        isNotEqTo.accept(neonBeeModule.extensionModels, extModels);
                        assertThat(neonBeeModule.getIdentifier()).isEqualTo("testmodule");
                        Truth8.assertThat(neonBeeModule.verticleClasses.stream().map(Class::getName))
                                .containsExactlyElementsIn(expectedClassNames);
                        testContext.completeNow();
                    });
                }));
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("Parse JAR should handle exceptions correct")
    void fromJarThrowTest(Vertx vertx, VertxTestContext testContext) throws IOException {
        Checkpoint checkIOException = testContext.checkpoint(3);

        Path nonExistingPath = createTempDirectory().resolve("pathdoesnotexist");
        NeonBeeModule.fromJar(vertx, nonExistingPath, CORRELATION_ID).onComplete(testContext.failing(t -> {
            testContext.verify(() -> {
                assertThat(t).isInstanceOf(IOException.class);
                assertThat(t).hasMessageThat().isEqualTo("JAR path does not exist: " + nonExistingPath.toString());
                checkIOException.flag();
            });
        }));

        BasicJar noNeonBeeModuleAttribute = new BasicJar(Map.of(), Map.of());
        NeonBeeModule.fromJar(vertx, noNeonBeeModuleAttribute.writeToTempPath(), CORRELATION_ID)
                .onComplete(testContext.failing(t -> {
                    testContext.verify(() -> {
                        assertThat(t).isInstanceOf(NoStackTraceThrowable.class);
                        assertThat(t).hasMessageThat()
                                .isEqualTo("Invalid NeonBee-Module: No " + NEONBEE_MODULE + "attribute found.");
                        checkIOException.flag();
                    });
                }));

        BasicJar brokenJar = new BasicJar(NeonBeeModuleJar.createManifest("testmodule", List.of("Hodor")), Map.of());
        NeonBeeModule.fromJar(vertx, brokenJar.writeToTempPath(), CORRELATION_ID).onComplete(testContext.failing(t -> {
            testContext.verify(() -> {
                assertThat(t).isInstanceOf(ClassNotFoundException.class);
                assertThat(t).hasMessageThat().isEqualTo("Hodor");
                checkIOException.flag();
            });
        }));
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("deploy and undeploy should work correct")
    @SuppressWarnings("unchecked")
    void deployUndeploy(Vertx vertx, VertxTestContext testContext) throws IOException, ClassNotFoundException {
        Checkpoint deployCheck = testContext.checkpoint();
        Checkpoint undeployCheck = testContext.checkpoint();

        // Create verticle classes
        String classNameA = "ClassA";
        String classNameB = "ClassB";
        DummyVerticleTemplate verticleA = new DummyVerticleTemplate(classNameA, "doesn't matter");
        DummyVerticleTemplate verticleB = new DummyVerticleTemplate(classNameB, "doesn't matter");
        List<String> classNames = List.of(classNameA, classNameB);

        // Create models
        Map.Entry<String, byte[]> productModel = buildModelEntry("ProductService.csn");
        Map.Entry<String, byte[]> multipleModel = buildModelEntry("MultipleService.csn");
        Map<String, byte[]> models = Map.ofEntries(productModel, multipleModel);

        // Create extension models
        Map.Entry<String, byte[]> productExtModel = buildModelEntry("io.neonbee.deploy.ProductService.edmx");
        Map.Entry<String, byte[]> carExtModel = buildModelEntry("io.neonbee.deploymultiple.CarService.edmx");
        Map.Entry<String, byte[]> userExtModel = buildModelEntry("io.neonbee.deploymultiple.UserService.edmx");
        Map<String, byte[]> extendedModels = Map.ofEntries(productExtModel, carExtModel, userExtModel);

        // Create NeonBeeModuleJar
        NeonBeeModuleJar neonBeeModuleJar =
                new NeonBeeModuleJar("testmodule", List.of(verticleA, verticleB), models, extendedModels);
        Path jarPath = createTempDirectory().resolve("neonbee-models.jar");

        // Extract classes
        URLClassLoader classLoader =
                Mockito.spy(new URLClassLoader(neonBeeModuleJar.writeToTempURL(), ClassLoader.getSystemClassLoader()));
        Class<Verticle> verticleClassA = (Class<Verticle>) classLoader.loadClass(classNameA);
        Class<Verticle> verticleClassB = (Class<Verticle>) classLoader.loadClass(classNameB);

        NeonBeeModule module = new NeonBeeModule(vertx, "testmodule", CORRELATION_ID, jarPath, classLoader,
                List.of(verticleClassA, verticleClassB), models, extendedModels);

        module.deploy().compose(neonBeeModule -> {
            List<String> deploymentIds =
                    module.succeededDeployments.stream().map(Deployment::getDeploymentId).collect(Collectors.toList());

            testContext.verify(() -> {
                // Check if verticle deployed correctly
                Truth8.assertThat(module.succeededDeployments.stream().map(Deployment::getIdentifier))
                        .containsExactlyElementsIn(classNames);
                assertThat(vertx.deploymentIDs()).containsAtLeastElementsIn(deploymentIds);

                BiConsumer<String, Integer> checkEntityModel = (schemaNamespace, expectedSize) -> {
                    EntityModel entityModel = EntityModelManager.getBufferedModel(vertx, schemaNamespace);
                    assertThat(entityModel).isNotNull();
                    assertThat(entityModel.getEdmxes()).hasSize(expectedSize);
                };
                checkEntityModel.accept("io.neonbee.deploy", 1);
                checkEntityModel.accept("io.neonbee.deploymultiple", 2);
            });
            deployCheck.flag();
            return module.undeploy().compose(v1 -> Future.succeededFuture(deploymentIds));
        }).onComplete(testContext.succeeding(deploymentIds -> {
            testContext.verify(() -> {
                Consumer<String> checkModelIsNull = schemaNamespace -> {
                    assertThat(EntityModelManager.getBufferedModel(vertx, schemaNamespace)).isNull();
                };
                checkModelIsNull.accept("io.neonbee.deploy");
                checkModelIsNull.accept("io.neonbee.deploymultiple");

                // Check that verticle are undeployed
                assertThat(vertx.deploymentIDs()).containsNoneIn(deploymentIds);

                // Check that the class loader was closed
                Mockito.verify(classLoader).close();

                undeployCheck.flag();
            });
            testContext.completeNow();
        }));
    }

    private Map.Entry<String, byte[]> buildModelEntry(String modelName) throws IOException {
        return Map.entry("models/" + modelName, TEST_RESOURCES.getRelated(modelName).getBytes());
    }
}
