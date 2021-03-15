package io.neonbee.entity;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.entity.EntityModelManager.BUFFERED_MODELS;
import static io.neonbee.entity.EntityModelManager.getBufferedModel;
import static io.neonbee.entity.EntityModelManager.getBufferedModels;
import static io.neonbee.entity.EntityModelManager.getBufferedOData;
import static io.neonbee.entity.EntityModelManager.getSharedModel;
import static io.neonbee.entity.EntityModelManager.getSharedModels;
import static io.neonbee.entity.EntityModelManager.registerModels;
import static io.neonbee.entity.EntityModelManager.reloadModels;
import static io.neonbee.entity.EntityModelManager.unregisterModels;
import static io.neonbee.test.helper.ResourceHelper.TEST_RESOURCES;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.apache.olingo.server.api.OData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.sap.cds.reflect.CdsModel;

import io.neonbee.NeonBeeMockHelper;
import io.neonbee.NeonBeeOptions;
import io.neonbee.entity.EntityModelManager.Loader;
import io.neonbee.test.base.NeonBeeTestBase;
import io.neonbee.test.helper.FileSystemHelper;
import io.neonbee.test.helper.WorkingDirectoryBuilder;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxTestContext;

class EntityModelManagerTest extends NeonBeeTestBase {
    private static final Path TEST_SERVICE_1_MODEL_PATH = TEST_RESOURCES.resolveRelated("TestService1.csn");

    private static final Path TEST_SERVICE_2_MODEL_PATH = TEST_RESOURCES.resolveRelated("TestService2.csn");

    private static final Path REFERENCE_SERVICE_MODEL_PATH = TEST_RESOURCES.resolveRelated("ReferenceService.csn");

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("should return the same odata instance for one thread")
    void validateODataBufferInSameThread() {
        OData odata1 = getBufferedOData();
        OData odata2 = getBufferedOData();
        assertThat(odata1).isSameInstanceAs(odata2);
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("should return different odata instances for multiple threads")
    void validateODataBufferInDifferentThreads() {
        CompletableFuture<OData> odataFuture1 = new CompletableFuture<>();
        CompletableFuture<OData> odataFuture2 = new CompletableFuture<>();

        new Thread(() -> odataFuture1.complete(getBufferedOData())).start();
        new Thread(() -> odataFuture2.complete(getBufferedOData())).start();

        try {
            assertThat(odataFuture1.get()).isNotSameInstanceAs(odataFuture2.get());
        } catch (InterruptedException | ExecutionException e) {
            fail("future interrupted exception");
        }
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("check if loading a non-existent model fails")
    void loadNonExistingModelTest(Vertx vertx, VertxTestContext testContext) {
        new EntityModelManager.Loader(vertx).loadModel(Path.of("not.existing.Service.csn"))
                .onComplete(testContext.failing(result -> testContext.completeNow()));
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("check if multiple models can be loaded from files")
    void loadEDMXModelsTest(Vertx vertx, VertxTestContext testContext) {
        Loader loader = new EntityModelManager.Loader(vertx);
        CompositeFuture
                .all(loader.loadModel(TEST_SERVICE_1_MODEL_PATH), loader.loadModel(TEST_SERVICE_2_MODEL_PATH),
                        loader.loadModel(REFERENCE_SERVICE_MODEL_PATH))
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    assertThat(loader.models.get("io.neonbee.test1").getEdmx().getEdm().getEntityContainer()
                            .getNamespace()).isEqualTo("io.neonbee.test1.TestService1");
                    assertThat(loader.models.get("io.neonbee.test2").getEdmx("io.neonbee.test2.TestService2Cars")
                            .getEdm().getEntityContainer().getNamespace())
                                    .isEqualTo("io.neonbee.test2.TestService2Cars");
                    assertThat(loader.models.get("io.neonbee.reference").getEdmx().getEdm().getEntityContainer()
                            .getNamespace()).isEqualTo("io.neonbee.reference.ReferenceService");

                    testContext.completeNow();
                })));
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("check if models from file system can be loaded")
    void loadModelsFileSystemTest(Vertx vertx, VertxTestContext testContext) {
        Loader loader = new EntityModelManager.Loader(vertx);
        CompositeFuture
                .all(loader.loadModel(TEST_SERVICE_1_MODEL_PATH), loader.loadModel(TEST_SERVICE_2_MODEL_PATH),
                        loader.loadModel(REFERENCE_SERVICE_MODEL_PATH))
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    EntityModel model = loader.models.get("io.neonbee.test1");
                    assertThat(model.getEdmxes()).hasSize(1);
                    model = loader.models.get("io.neonbee.test2");
                    assertThat(model.getEdmxes()).hasSize(2);
                    model = loader.models.get("io.neonbee.reference");
                    assertThat(model.getEdmxes()).hasSize(1);

                    testContext.completeNow();
                })));
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("check if the models from classpath can be loaded ")
    void loadFromClassPathTest(Vertx vertx, VertxTestContext testContext) throws IOException {
        Loader loader = new EntityModelManager.Loader(vertx);
        loader.scanClassPath().onComplete(testContext.succeeding(result -> testContext.verify(() -> {
            assertThat(loader.models.get("io.neonbee.test1").getEdmx().getEdm().getEntityContainer().getNamespace())
                    .isEqualTo("io.neonbee.test1.TestService1");
            assertThat(loader.models.get("io.neonbee.test2").getEdmx("io.neonbee.test2.TestService2Cars").getEdm()
                    .getEntityContainer().getNamespace()).isEqualTo("io.neonbee.test2.TestService2Cars");

            testContext.completeNow();
        })));
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("check if the models from module can be loaded ")
    void loadFromModuleTest(Vertx vertx, VertxTestContext testContext) throws IOException {
        // Create models
        Map.Entry<String, byte[]> referenceModel = buildModelEntry("ReferenceService.csn");

        // Create extension models
        Map.Entry<String, byte[]> referenceExtModel = buildModelEntry("io.neonbee.reference.ReferenceService.edmx");
        Map<String, byte[]> extendedModels = Map.ofEntries(referenceExtModel);

        Loader loader = new EntityModelManager.Loader(vertx);
        loader.loadModel("models/ReferenceService.csn", referenceModel.getValue(), extendedModels)
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    assertThat(loader.models.get("io.neonbee.reference").getEdmx().getEdm().getEntityContainer()
                            .getNamespace()).isEqualTo("io.neonbee.reference.ReferenceService");

                    testContext.completeNow();
                })));
    }

    private Map.Entry<String, byte[]> buildModelEntry(String modelName) throws IOException {
        return Map.entry("models/" + modelName, TEST_RESOURCES.getRelated(modelName).getBytes());
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("test if loading models doesn't fail for non-existing working directories")
    void dontFailModelLoadingOnNonExistingWorkingDir(Vertx vertx, VertxTestContext testContext) {
        new EntityModelManager.Loader(vertx).loadDir(Path.of("non-existing"))
                .onComplete(testContext.succeeding(models -> testContext.completeNow()));
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("lazy model loading: NeonBee should not load any model files after starting")
    void lazyModelLoadingTest(Vertx vertx, VertxTestContext testContext) throws Exception {
        Path workingDir = FileSystemHelper.createTempDirectory();
        WorkingDirectoryBuilder.hollow().addModel(TEST_RESOURCES.resolveRelated("TestService1.csn"))
                .addModel(TEST_RESOURCES.resolveRelated("TestService2.csn")).build(workingDir);

        NeonBeeMockHelper.registerNeonBeeMock(vertx,
                new NeonBeeOptions.Mutable().setIgnoreClassPath(true).setWorkingDirectory(workingDir));

        // assert that buffered models is null and stays empty when being retrieved w/ getBufferedModels
        assertThat(getBufferedModels(vertx)).isNull();
        assertThat(getBufferedModel(vertx, "io.neonbee.test1")).isNull();
        assertThat(BUFFERED_MODELS.get(vertx)).isNull();

        getSharedModels(vertx).onComplete(testContext.succeeding(models -> testContext.verify(() -> {
            // assert that all expected models have been loaded
            assertThat(models).isNotNull();
            assertThat(models).isNotEmpty();
            assertThat(models.get("io.neonbee.test1").getEdmx().getEdm().getEntityContainer().getNamespace())
                    .isEqualTo("io.neonbee.test1.TestService1");
            assertThat(models.get("io.neonbee.test2").getEdmx("io.neonbee.test2.TestService2Cars").getEdm()
                    .getEntityContainer().getNamespace()).isEqualTo("io.neonbee.test2.TestService2Cars");

            // assert that now the models are actually buffered
            assertThat(getBufferedModels(vertx)).isNotNull();
            assertThat(getBufferedModel(vertx, "io.neonbee.test1")).isNotNull();

            // assert that if calling getSharedModels a second time, we retrieve the same instance of models
            getSharedModels(vertx).onComplete(testContext.succeeding(modelsAgain -> testContext.verify(() -> {
                assertThat(modelsAgain).isSameInstanceAs(models);

                // and assert that if now calling reloadModels, the model buffer is actually refreshed
                reloadModels(vertx).onComplete(testContext.succeeding(reloadModels -> testContext.verify(() -> {
                    assertThat(reloadModels).isNotSameInstanceAs(models);

                    // and also the buffer got actually refreshed
                    assertThat(getBufferedModels(vertx)).isSameInstanceAs(reloadModels);

                    testContext.completeNow();
                })));
            })));
        })));
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("check if getting single shared models will work for get shared edmx model")
    void getSingleSharedModelsTest(Vertx vertx, VertxTestContext testContext) throws Exception {
        Path workingDir = FileSystemHelper.createTempDirectory();
        WorkingDirectoryBuilder.hollow().addModel(TEST_RESOURCES.resolveRelated("TestService1.csn"))
                .addModel(TEST_RESOURCES.resolveRelated("TestService2.csn")).build(workingDir);

        NeonBeeMockHelper.registerNeonBeeMock(vertx,
                new NeonBeeOptions.Mutable().setIgnoreClassPath(true).setWorkingDirectory(workingDir));
        CompositeFuture.all(getSharedModel(vertx, "io.neonbee.test1"), getSharedModel(vertx, "io.neonbee.test2"))
                .onComplete(testContext.succeeding(models -> testContext.verify(() -> {
                    assertThat(models.<EntityModel>resultAt(0).getEdmx().getEdm().getEntityContainer().getNamespace())
                            .isEqualTo("io.neonbee.test1.TestService1");
                    assertThat(models.<EntityModel>resultAt(1).getEdmx("io.neonbee.test2.TestService2Cars").getEdm()
                            .getEntityContainer().getNamespace()).isEqualTo("io.neonbee.test2.TestService2Cars");

                    testContext.completeNow();
                })));
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("check if getting single non-existing model will fail for get shared edmx model")
    void getSingleNonExistingSharedModelTest(Vertx vertx, VertxTestContext testContext) {
        getSharedModel(vertx, "non-existing").onComplete(testContext.failing(throwable -> testContext.verify(() -> {
            assertThat(throwable).isInstanceOf(NoSuchElementException.class);
            testContext.completeNow();
        })));
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("check if getting CSN Model works")
    void getCSNModelTest(Vertx vertx, VertxTestContext testContext) {
        Loader loader = new EntityModelManager.Loader(vertx);
        Future<CdsModel> csnModelFuture = loader.loadCsnModel(TEST_SERVICE_1_MODEL_PATH);
        csnModelFuture.compose(v -> loader.loadModel(TEST_SERVICE_1_MODEL_PATH))
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    CdsModel expectedCsnModel = csnModelFuture.result();
                    EntityModel model = loader.models.get("io.neonbee.test1");
                    assertThat(model.getCsn()).isEqualTo(expectedCsnModel);
                    testContext.completeNow();
                })));
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("register / unregister module models")
    void registerModuleModels(Vertx vertx, VertxTestContext testContext) throws IOException {
        Path workingDirectory = getNeonBee().getOptions().getWorkingDirectory();
        NeonBeeMockHelper.registerNeonBeeMock(vertx,
                new NeonBeeOptions.Mutable().setIgnoreClassPath(true).setWorkingDirectory(workingDirectory));

        // Create models
        Map.Entry<String, byte[]> referenceModel = buildModelEntry("ReferenceService.csn");
        Map<String, byte[]> models = Map.ofEntries(referenceModel);

        // Create extension models
        Map.Entry<String, byte[]> referenceExtModel = buildModelEntry("io.neonbee.reference.ReferenceService.edmx");
        Map<String, byte[]> extendedModels = Map.ofEntries(referenceExtModel);

        registerModels(vertx, "referencemodule", models, extendedModels)
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    assertThat(getBufferedModel(vertx, "io.neonbee.reference").getEdmx().getEdm().getEntityContainer()
                            .getNamespace()).isEqualTo("io.neonbee.reference.ReferenceService");
                    unregisterModels(vertx, "referencemodule");
                    assertThat(getBufferedModel(vertx, "io.neonbee.reference")).isNull();
                    testContext.completeNow();
                })));
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("register / unregister multiple modules to verify that changing the unmodifiable BUFFERED_MODELS is working correctly.")
    void registerMultipleModuleModels(Vertx vertx, VertxTestContext testContext) throws IOException {
        Path workingDirectory = getNeonBee().getOptions().getWorkingDirectory();
        NeonBeeMockHelper.registerNeonBeeMock(vertx,
                new NeonBeeOptions.Mutable().setIgnoreClassPath(true).setWorkingDirectory(workingDirectory));

        // Create 1st models
        Map.Entry<String, byte[]> referenceModel = buildModelEntry("ReferenceService.csn");
        Map<String, byte[]> referenceModels = Map.ofEntries(referenceModel);

        // Create 1st extension models
        Map.Entry<String, byte[]> referenceExtModel = buildModelEntry("io.neonbee.reference.ReferenceService.edmx");
        Map<String, byte[]> referenceExtendedModels = Map.ofEntries(referenceExtModel);

        // Create 2nd models
        Map.Entry<String, byte[]> testModel = buildModelEntry("TestService1.csn");
        Map<String, byte[]> testModels = Map.ofEntries(testModel);

        // Create 2nd extension models
        Map.Entry<String, byte[]> testExtModel = buildModelEntry("io.neonbee.test1.TestService1.edmx");
        Map<String, byte[]> testExtendedModels = Map.ofEntries(testExtModel);

        registerModels(vertx, "referencemodule", referenceModels, referenceExtendedModels)
                .compose(result -> registerModels(vertx, "testmodule", testModels, testExtendedModels))
                .onComplete(testContext.succeeding(res -> testContext.verify(() -> {
                    assertThat(getBufferedModel(vertx, "io.neonbee.reference").getEdmx().getEdm().getEntityContainer()
                            .getNamespace()).isEqualTo("io.neonbee.reference.ReferenceService");
                    assertThat(getBufferedModel(vertx, "io.neonbee.test1").getEdmx().getEdm().getEntityContainer()
                            .getNamespace()).isEqualTo("io.neonbee.test1.TestService1");
                    unregisterModels(vertx, "referencemodule");
                    assertThat(getBufferedModel(vertx, "io.neonbee.reference")).isNull();
                    unregisterModels(vertx, "testmodule");
                    assertThat(getBufferedModel(vertx, "AnnotatedService")).isNull();
                    testContext.completeNow();
                })));
    }
}
