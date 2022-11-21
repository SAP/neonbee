package io.neonbee.entity;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.NeonBeeProfile.NO_WEB;
import static io.neonbee.entity.EntityModelLoader.createServiceMetadata;
import static io.neonbee.entity.EntityModelLoader.getSchemaNamespace;
import static io.neonbee.test.helper.ResourceHelper.TEST_RESOURCES;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.xml.stream.XMLStreamException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import com.sap.cds.reflect.CdsModel;

import io.neonbee.NeonBeeOptions;
import io.neonbee.test.base.NeonBeeTestBase;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxTestContext;

class EntityModelLoaderTest extends NeonBeeTestBase {
    private static final Path TEST_SERVICE_1_MODEL_PATH = TEST_RESOURCES.resolveRelated("TestService1.csn");

    private static final Path TEST_SERVICE_2_MODEL_PATH = TEST_RESOURCES.resolveRelated("TestService2.csn");

    private static final Path REFERENCE_SERVICE_MODEL_PATH = TEST_RESOURCES.resolveRelated("ReferenceService.csn");

    @Override
    protected void adaptOptions(TestInfo testInfo, NeonBeeOptions.Mutable options) {
        options.addActiveProfile(NO_WEB);
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("check if loading a non-existent model fails")
    void loadNonExistingModelTest(Vertx vertx, VertxTestContext testContext) {
        new EntityModelLoader(vertx).loadModel(Path.of("not.existing.Service.csn"))
                .onComplete(testContext.failing(result -> testContext.completeNow()));
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("check if multiple models can be loaded from files")
    void loadEDMXModelsTest(Vertx vertx, VertxTestContext testContext) {
        EntityModelLoader loader = new EntityModelLoader(vertx);
        CompositeFuture
                .all(loader.loadModel(TEST_SERVICE_1_MODEL_PATH), loader.loadModel(TEST_SERVICE_2_MODEL_PATH),
                        loader.loadModel(REFERENCE_SERVICE_MODEL_PATH))
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    assertThat(loader.models.get("io.neonbee.test1").getEdmxMetadata().getEdm().getEntityContainer()
                            .getNamespace()).isEqualTo("io.neonbee.test1.TestService1");
                    assertThat(
                            loader.models.get("io.neonbee.test2").getEdmxMetadata("io.neonbee.test2.TestService2Cars")
                                    .getEdm().getEntityContainer().getNamespace())
                                            .isEqualTo("io.neonbee.test2.TestService2Cars");
                    assertThat(loader.models.get("io.neonbee.reference").getEdmxMetadata().getEdm().getEntityContainer()
                            .getNamespace()).isEqualTo("io.neonbee.reference.ReferenceService");

                    testContext.completeNow();
                })));
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("check if models from file system can be loaded")
    void loadModelsFileSystemTest(Vertx vertx, VertxTestContext testContext) {
        EntityModelLoader loader = new EntityModelLoader(vertx);
        CompositeFuture
                .all(loader.loadModel(TEST_SERVICE_1_MODEL_PATH), loader.loadModel(TEST_SERVICE_2_MODEL_PATH),
                        loader.loadModel(REFERENCE_SERVICE_MODEL_PATH))
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    EntityModel model = loader.models.get("io.neonbee.test1");
                    assertThat(model.getAllEdmxMetadata()).hasSize(1);
                    model = loader.models.get("io.neonbee.test2");
                    assertThat(model.getAllEdmxMetadata()).hasSize(2);
                    model = loader.models.get("io.neonbee.reference");
                    assertThat(model.getAllEdmxMetadata()).hasSize(1);

                    testContext.completeNow();
                })));
    }

    @Test
    @Timeout(value = 20, timeUnit = TimeUnit.SECONDS)
    @DisplayName("check if the models from class path can be loaded ")
    void loadFromClassPathTest(Vertx vertx, VertxTestContext testContext) {
        EntityModelLoader loader = new EntityModelLoader(vertx);
        loader.scanClassPath().onComplete(testContext.succeeding(result -> testContext.verify(() -> {
            assertThat(loader.models.get("io.neonbee.test1").getEdmxMetadata().getEdm().getEntityContainer()
                    .getNamespace()).isEqualTo("io.neonbee.test1.TestService1");
            assertThat(loader.models.get("io.neonbee.test2").getEdmxMetadata("io.neonbee.test2.TestService2Cars")
                    .getEdm().getEntityContainer().getNamespace()).isEqualTo("io.neonbee.test2.TestService2Cars");

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

        EntityModelLoader loader = new EntityModelLoader(vertx);
        loader.parseModel("models/ReferenceService.csn", referenceModel.getValue(), extendedModels)
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    assertThat(loader.models.get("io.neonbee.reference").getEdmxMetadata().getEdm().getEntityContainer()
                            .getNamespace()).isEqualTo("io.neonbee.reference.ReferenceService");

                    testContext.completeNow();
                })));
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("check if getting CSN Model works")
    void getCSNModelTest(Vertx vertx, VertxTestContext testContext) {
        EntityModelLoader loader = new EntityModelLoader(vertx);
        Future<CdsModel> csnModelFuture = loader.readCsnModel(TEST_SERVICE_1_MODEL_PATH);
        csnModelFuture.compose(v -> loader.loadModel(TEST_SERVICE_1_MODEL_PATH))
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    CdsModel expectedCsnModel = csnModelFuture.result();
                    EntityModel model = loader.models.get("io.neonbee.test1");
                    assertThat(model.getCsnModel()).isEqualTo(expectedCsnModel);
                    testContext.completeNow();
                })));
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("loading models doesn't fail for non-existing working directories")
    void dontFailModelLoadingOnNonExistingWorkingDir(Vertx vertx, VertxTestContext testContext) {
        new EntityModelLoader(vertx).scanDir(Path.of("non-existing")).onComplete(testContext.succeeding(models -> {
            assertThat(models).isNull();
            testContext.completeNow();
        }));
    }

    @Test
    @DisplayName("get schema namespace")
    void testGetSchemaNamespace() throws XMLStreamException, IOException {
        Buffer withEntityContainer = TEST_RESOURCES.getRelated("withEntityContainer.edmx");
        Buffer withoutEntityContainer = TEST_RESOURCES.getRelated("withoutEntityContainer.edmx");
        assertThat(getSchemaNamespace(createServiceMetadata(withEntityContainer))).isEqualTo("Test.Service");
        assertThat(getSchemaNamespace(createServiceMetadata(withoutEntityContainer))).isEqualTo("Test.Service");
    }

    private Map.Entry<String, byte[]> buildModelEntry(String modelName) throws IOException {
        return Map.entry("models/" + modelName, TEST_RESOURCES.getRelated(modelName).getBytes());
    }
}
