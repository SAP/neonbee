package io.neonbee.entity;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.NeonBeeProfile.NO_WEB;
import static io.neonbee.entity.EntityModelManager.BUFFERED_MODELS;
import static io.neonbee.entity.EntityModelManager.EXTERNAL_MODEL_DEFINITIONS;
import static io.neonbee.entity.EntityModelManager.getBufferedModel;
import static io.neonbee.entity.EntityModelManager.getBufferedOData;
import static io.neonbee.entity.EntityModelManager.getSharedModel;
import static io.neonbee.entity.EntityModelManager.getSharedModels;
import static io.neonbee.entity.EntityModelManager.registerModels;
import static io.neonbee.entity.EntityModelManager.reloadModels;
import static io.neonbee.entity.EntityModelManager.unregisterModels;
import static io.neonbee.test.helper.OptionsHelper.defaultOptions;
import static io.neonbee.test.helper.ResourceHelper.TEST_RESOURCES;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.apache.olingo.server.api.OData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import io.neonbee.NeonBee;
import io.neonbee.NeonBeeMockHelper;
import io.neonbee.NeonBeeOptions;
import io.neonbee.test.base.NeonBeeTestBase;
import io.neonbee.test.helper.FileSystemHelper;
import io.neonbee.test.helper.WorkingDirectoryBuilder;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Vertx;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxTestContext;

public class EntityModelManagerTest extends NeonBeeTestBase { // NOPMD (public due to static methods)
    @Override
    protected void adaptOptions(TestInfo testInfo, NeonBeeOptions.Mutable options) {
        options.addActiveProfile(NO_WEB);
    }

    /**
     * Public test helper, to get the BUFFERED_MODELS from the EntityModelManager
     *
     * @param neonBee the NeonBee to get the models for
     * @return a map or null
     */
    public static Map<String, EntityModel> getBufferedModels(NeonBee neonBee) {
        return BUFFERED_MODELS.get(neonBee);
    }

    /**
     * Public test helper, to get the EXTERNAL_MODEL_DEFINITIONS from the EntityModelManager
     *
     * @param neonBee the NeonBee to get the models for
     * @return a set or null
     */
    public static Set<EntityModelDefinition> getExternalModelDefinitions(NeonBee neonBee) {
        return EXTERNAL_MODEL_DEFINITIONS.get(neonBee);
    }

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
    @DisplayName("lazy model loading: NeonBee should not load any model files after starting")
    void lazyModelLoadingTest(Vertx vertx, VertxTestContext testContext) throws Exception {
        Path workingDir = FileSystemHelper.createTempDirectory();
        WorkingDirectoryBuilder.hollow().addModel(TEST_RESOURCES.resolveRelated("TestService1.csn"))
                .addModel(TEST_RESOURCES.resolveRelated("TestService2.csn")).build(workingDir);

        NeonBee neonBee = NeonBeeMockHelper.registerNeonBeeMock(vertx,
                defaultOptions().clearActiveProfiles().setWorkingDirectory(workingDir));

        // assert that buffered models is null and stays empty when being retrieved w/ getBufferedModels
        assertThat(getBufferedModels(neonBee)).isNull();
        assertThat(getBufferedModel(neonBee, "io.neonbee.test1")).isNull();
        assertThat(BUFFERED_MODELS.get(neonBee)).isNull();

        getSharedModels(neonBee).onComplete(testContext.succeeding(models -> testContext.verify(() -> {
            // assert that all expected models have been loaded
            assertThat(models).isNotNull();
            assertThat(models).isNotEmpty();
            assertThat(models.get("io.neonbee.test1").getEdmxMetadata().getEdm().getEntityContainer().getNamespace())
                    .isEqualTo("io.neonbee.test1.TestService1");
            assertThat(models.get("io.neonbee.test2").getEdmxMetadata("io.neonbee.test2.TestService2Cars").getEdm()
                    .getEntityContainer().getNamespace()).isEqualTo("io.neonbee.test2.TestService2Cars");

            // assert that now the models are actually buffered
            assertThat(getBufferedModels(neonBee)).isNotNull();
            assertThat(getBufferedModel(neonBee, "io.neonbee.test1")).isNotNull();

            // assert that if calling getSharedModels a second time, we retrieve the same instance of models
            getSharedModels(neonBee).onComplete(testContext.succeeding(modelsAgain -> testContext.verify(() -> {
                assertThat(modelsAgain).isSameInstanceAs(models);

                // and assert that if now calling reloadModels, the model buffer is actually refreshed
                reloadModels(neonBee).onComplete(testContext.succeeding(reloadModels -> testContext.verify(() -> {
                    assertThat(reloadModels).isNotSameInstanceAs(models);

                    // and also the buffer got actually refreshed
                    assertThat(getBufferedModels(neonBee)).isSameInstanceAs(reloadModels);

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

        NeonBee neonBee = NeonBeeMockHelper.registerNeonBeeMock(vertx,
                defaultOptions().clearActiveProfiles().setWorkingDirectory(workingDir));
        CompositeFuture.all(getSharedModel(neonBee, "io.neonbee.test1"), getSharedModel(neonBee, "io.neonbee.test2"))
                .onComplete(testContext.succeeding(models -> testContext.verify(() -> {
                    assertThat(models.<EntityModel>resultAt(0).getEdmxMetadata().getEdm().getEntityContainer()
                            .getNamespace()).isEqualTo("io.neonbee.test1.TestService1");
                    assertThat(models.<EntityModel>resultAt(1).getEdmxMetadata("io.neonbee.test2.TestService2Cars")
                            .getEdm().getEntityContainer().getNamespace())
                                    .isEqualTo("io.neonbee.test2.TestService2Cars");

                    testContext.completeNow();
                })));
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("check if getting single non-existing model will fail for get shared edmx model")
    void getSingleNonExistingSharedModelTest(Vertx vertx, VertxTestContext testContext) {
        NeonBee neonBee = NeonBeeMockHelper.registerNeonBeeMock(vertx);
        getSharedModel(neonBee, "non-existing").onComplete(testContext.failing(throwable -> testContext.verify(() -> {
            assertThat(throwable).isInstanceOf(NoSuchElementException.class);
            testContext.completeNow();
        })));
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("register / unregister module models")
    void registerModuleModels(Vertx vertx, VertxTestContext testContext) throws IOException {
        Path workingDirectory = getNeonBee().getOptions().getWorkingDirectory();
        NeonBee neonBee = NeonBeeMockHelper.registerNeonBeeMock(vertx,
                defaultOptions().clearActiveProfiles().setWorkingDirectory(workingDirectory));

        // Create modelsbuildModelEntry
        Map.Entry<String, byte[]> referenceModel = buildModelEntry("ReferenceService.csn");
        Map<String, byte[]> models = Map.ofEntries(referenceModel);

        // Create extension models
        Map.Entry<String, byte[]> referenceExtModel = buildModelEntry("io.neonbee.reference.ReferenceService.edmx");
        Map<String, byte[]> extendedModels = Map.ofEntries(referenceExtModel);

        EntityModelDefinition modelDefinition = new EntityModelDefinition(models, extendedModels);
        registerModels(neonBee, modelDefinition).onComplete(testContext.succeeding(result -> testContext.verify(() -> {
            assertThat(getBufferedModel(neonBee, "io.neonbee.reference").getEdmxMetadata().getEdm().getEntityContainer()
                    .getNamespace()).isEqualTo("io.neonbee.reference.ReferenceService");
            unregisterModels(neonBee, modelDefinition)
                    .onComplete(testContext.succeeding(result2 -> testContext.verify(() -> {
                        assertThat(getBufferedModel(neonBee, "io.neonbee.reference")).isNull();
                        testContext.completeNow();
                    })));
        })));
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("register / unregister multiple modules to verify that changing the unmodifiable BUFFERED_MODELS is working correctly.")
    void registerMultipleModuleModels(Vertx vertx, VertxTestContext testContext) throws IOException {
        Path workingDirectory = getNeonBee().getOptions().getWorkingDirectory();
        NeonBee neonBee = NeonBeeMockHelper.registerNeonBeeMock(vertx,
                defaultOptions().clearActiveProfiles().setWorkingDirectory(workingDirectory));

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

        EntityModelDefinition definition = new EntityModelDefinition(referenceModels, referenceExtendedModels);
        EntityModelDefinition definition2 = new EntityModelDefinition(testModels, testExtendedModels);
        registerModels(neonBee, definition).compose(result -> registerModels(neonBee, definition2))
                .onComplete(testContext.succeeding(res -> testContext.verify(() -> {
                    assertThat(getBufferedModel(neonBee, "io.neonbee.reference").getEdmxMetadata().getEdm()
                            .getEntityContainer().getNamespace()).isEqualTo("io.neonbee.reference.ReferenceService");
                    assertThat(getBufferedModel(neonBee, "io.neonbee.test1").getEdmxMetadata().getEdm()
                            .getEntityContainer().getNamespace()).isEqualTo("io.neonbee.test1.TestService1");
                    unregisterModels(neonBee, definition).compose(res2 -> unregisterModels(neonBee, definition2))
                            .onComplete(testContext.succeeding(result2 -> testContext.verify(() -> {
                                assertThat(getBufferedModel(neonBee, "io.neonbee.reference")).isNull();
                                assertThat(getBufferedModel(neonBee, "AnnotatedService")).isNull();
                                testContext.completeNow();
                            })));
                })));
    }

    private Map.Entry<String, byte[]> buildModelEntry(String modelName) throws IOException {
        return Map.entry("models/" + modelName, TEST_RESOURCES.getRelated(modelName).getBytes());
    }

}
