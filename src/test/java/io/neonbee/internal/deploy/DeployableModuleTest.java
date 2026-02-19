package io.neonbee.internal.deploy;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.internal.deploy.DeploymentTest.newNeonBeeMockForDeployment;
import static io.neonbee.test.helper.DummyVerticleHelper.DUMMY_VERTICLE;
import static io.neonbee.test.helper.FileSystemHelper.createTempDirectory;
import static io.vertx.core.Future.succeededFuture;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.google.common.truth.Correspondence;

import io.neonbee.NeonBee;
import io.neonbee.internal.BasicJar;
import io.neonbee.internal.NeonBeeModuleJar;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;

class DeployableModuleTest {
    @Test
    @DisplayName("test instantiation")
    void testInstantiation() {
        assertThrows(NullPointerException.class, () -> new DeployableModule(null, null, null));

        DeployableModule deployable1 = new DeployableModule("moduleA", null, List.of());
        assertThat(deployable1.moduleName).isEqualTo("moduleA");
        assertThat(deployable1.moduleClassLoader).isNull();

        URLClassLoader classLoader = mock(URLClassLoader.class);
        DeployableModule deployable2 = new DeployableModule("moduleB", classLoader, List.of());
        assertThat(deployable2.moduleName).isEqualTo("moduleB");
        assertThat(deployable2.moduleClassLoader).isSameInstanceAs(classLoader);

        IllegalStateException exception =
                assertThrows(IllegalStateException.class, () -> new DeployableModule("moduleC", null,
                        List.of(new DeployableVerticle(DUMMY_VERTICLE,
                                new DeploymentOptions()))));
        assertThat(exception).hasMessageThat()
                .isEqualTo("Missing module class loader for provided deployable verticle(s)");
    }

    @Test
    @DisplayName("test getIdentifier")
    void testGetIdentifier() {
        assertThat(new DeployableModule("moduleA", null, List.of()).getIdentifier()).isEqualTo("moduleA");
        assertThat(new DeployableModule("moduleB", null, List.of()).getIdentifier()).isEqualTo("moduleB");
    }

    @Test
    @DisplayName("test getDeployables is unmodifiable (even if the original list was modifiable)")
    void testGetDeployablesIsUnmodifiable() {
        assertThrows(UnsupportedOperationException.class,
                () -> new DeployableModule("module", null, new ArrayList<>()).getDeployables()
                        .add(null));
    }

    @Test
    @DisplayName("test that undeploy closes the module class loader")
    void testUndeployClosesModuleClassLoader() throws IOException {
        NeonBee neonBeeMock = newNeonBeeMockForDeployment();

        URLClassLoader classLoaderMock = mock(URLClassLoader.class);
        PendingDeployment deployment = new DeployableModule("module", classLoaderMock, List.of())
                .deploy(neonBeeMock);
        assertThat(deployment.getDeployment().succeeded()).isTrue();
        assertThat(deployment.undeploy().succeeded()).isTrue();
        verify(classLoaderMock).close();
    }

    @Test
    @DisplayName("test fromJar")
    void testFromJar() throws IOException {
        NeonBeeModuleJar moduleJar = NeonBeeModuleJar.create("testmodule").withVerticles().withModels().build();
        Path moduleJarPath = moduleJar.writeToTempPath();

        NeonBee neonBeeMock = newNeonBeeMockForDeployment();
        Vertx vertxMock = neonBeeMock.getVertx();

        Future<DeployableModule> deployableFuture = DeployableModule.fromJar(vertxMock, moduleJarPath);
        assertThat(deployableFuture.cause()).isNull();
        assertThat(deployableFuture.succeeded()).isTrue();

        DeployableModule deployable = deployableFuture.result();
        assertThat(deployable.moduleName).isEqualTo("testmodule");
        assertThat(deployable.moduleClassLoader).isNotNull();
        assertThat(deployable.moduleClassLoader.getURLs()).asList()
                .containsExactly(moduleJarPath.toUri().toURL());
        assertThat(deployable.keepPartialDeployment).isFalse();

        List<Deployable> deployables = deployable.getDeployables();
        List<DeployableModels> deployableModels =
                deployables.stream().filter(DeployableModels.class::isInstance)
                        .map(DeployableModels.class::cast).toList();
        assertThat(deployableModels).hasSize(1);
        assertThat(deployableModels.get(0).modelDefinition.getCSNModelDefinitions())
                .comparingValuesUsing(
                        Correspondence.<byte[], byte[]>from(Arrays::equals, "is not equal to"))
                .containsExactlyEntriesIn(NeonBeeModuleJar.DUMMY_MODELS);
        assertThat(deployableModels.get(0).modelDefinition.getAssociatedModelDefinitions())
                .comparingValuesUsing(
                        Correspondence.<byte[], byte[]>from(Arrays::equals, "is not equal to"))
                .containsExactlyEntriesIn(NeonBeeModuleJar.DUMMY_EXTENSION_MODELS);
        assertThat(deployables.stream().filter(DeployableVerticle.class::isInstance)
                .map(DeployableVerticle.class::cast)
                .map(deployableVerticle -> deployableVerticle.verticleClass).map(Class::getName))
                        .containsExactlyElementsIn(NeonBeeModuleJar.DUMMY_VERTICLES);
    }

    @Test
    @DisplayName("test fromJar without verticles")
    void testFromJarWithoutVerticles() throws IOException {
        NeonBeeModuleJar moduleJar = NeonBeeModuleJar.create("testmodule").withModels().build();
        Path moduleJarPath = moduleJar.writeToTempPath();

        NeonBee neonBeeMock = newNeonBeeMockForDeployment();
        Vertx vertxMock = neonBeeMock.getVertx();

        Future<DeployableModule> deployableFuture = DeployableModule.fromJar(vertxMock, moduleJarPath);
        assertThat(deployableFuture.cause()).isNull();
        assertThat(deployableFuture.succeeded()).isTrue();

        DeployableModule deployable = deployableFuture.result();
        assertThat(deployable.moduleName).isEqualTo("testmodule");
        assertThat(deployable.moduleClassLoader).isNull();
        assertThat(deployable.keepPartialDeployment).isFalse();

        List<Deployable> deployables = deployable.getDeployables();
        List<DeployableModels> deployableModels =
                deployables.stream().filter(DeployableModels.class::isInstance)
                        .map(DeployableModels.class::cast).toList();
        assertThat(deployableModels).hasSize(1);
        assertThat(deployableModels.get(0).modelDefinition.getCSNModelDefinitions())
                .comparingValuesUsing(
                        Correspondence.<byte[], byte[]>from(Arrays::equals, "is not equal to"))
                .containsExactlyEntriesIn(NeonBeeModuleJar.DUMMY_MODELS);
        assertThat(deployableModels.get(0).modelDefinition.getAssociatedModelDefinitions())
                .comparingValuesUsing(
                        Correspondence.<byte[], byte[]>from(Arrays::equals, "is not equal to"))
                .containsExactlyEntriesIn(NeonBeeModuleJar.DUMMY_EXTENSION_MODELS);
        assertThat(deployables.stream().filter(DeployableVerticle.class::isInstance)).isEmpty();
    }

    @Test
    @DisplayName("test fromJar exceptions")
    void testFromJarExceptions() throws IOException {
        NeonBee neonBeeMock = newNeonBeeMockForDeployment();
        Vertx vertxMock = neonBeeMock.getVertx();

        BasicJar noModuleAttribute = new BasicJar(Map.of(), Map.of());
        Throwable noModuleAttributeException =
                DeployableModule.fromJar(vertxMock, noModuleAttribute.writeToTempPath()).cause();
        // assertThat(noModuleAttributeException).isInstanceOf(NoStackTraceThrowable.class);
        assertThat(noModuleAttributeException).hasMessageThat().isEqualTo("No NeonBee-Module attribute found");

        BasicJar brokenJar =
                new BasicJar(NeonBeeModuleJar.createManifest("testmodule", List.of("Hodor")), Map.of());
        Throwable brokenJarException = DeployableModule.fromJar(vertxMock, brokenJar.writeToTempPath()).cause();
        assertThat(brokenJarException).isInstanceOf(ClassNotFoundException.class);
        assertThat(brokenJarException).hasMessageThat().isEqualTo("Hodor");

        when(vertxMock.fileSystem().exists(any())).thenReturn(succeededFuture(false));
        Path nonExistingPath = createTempDirectory().resolve("pathdoesnotexist");
        Throwable nonExistingPathException = DeployableModule.fromJar(vertxMock, nonExistingPath).cause();
        assertThat(nonExistingPathException).isInstanceOf(NoSuchFileException.class);
        assertThat(nonExistingPathException).hasMessageThat()
                .isEqualTo("JAR path does not exist: " + nonExistingPath.toString());

    }
}
