package io.neonbee.internal.deploy;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.NeonBeeMockHelper.defaultVertxMock;
import static io.neonbee.NeonBeeMockHelper.registerNeonBeeMock;
import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.google.common.truth.Correspondence;

import io.neonbee.NeonBee;
import io.neonbee.NeonBeeOptions;
import io.neonbee.entity.EntityModelDefinition;
import io.neonbee.internal.NeonBeeModuleJar;
import io.neonbee.internal.scanner.ClassPathScanner;
import io.neonbee.test.helper.ReflectionHelper;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

class DeployableModelsTest {
    @Test
    @DisplayName("test instantiation")
    void testInstantiation() {
        EntityModelDefinition definition = new EntityModelDefinition(Map.of(), Map.of());
        DeployableModels deployable = new DeployableModels(definition);
        assertThat(deployable.modelDefinition).isSameInstanceAs(definition);
    }

    @Test
    @DisplayName("test getIdentifier")
    void testGetIdentifier() {
        EntityModelDefinition definition = new EntityModelDefinition(Map.of(), Map.of());
        DeployableModels deployable = new DeployableModels(definition);
        assertThat(deployable.getIdentifier()).isEqualTo(definition.toString());
    }

    @Test
    @DisplayName("test deploy and undeploy")
    void testDeployUndeploy() throws NoSuchFieldException, IllegalAccessException {
        EntityModelDefinition definition = new EntityModelDefinition(
                Map.of("okay", new JsonObject().put("namespace", "test").toBuffer().getBytes()), Map.of());
        DeployableModels deployable = new DeployableModels(definition);

        Vertx vertxMock = defaultVertxMock();
        NeonBee neonBee = registerNeonBeeMock(vertxMock, new NeonBeeOptions.Mutable().setIgnoreClassPath(true));

        PendingDeployment deployment = deployable.deploy(neonBee);
        assertThat(deployment.succeeded()).isTrue();
        Set<EntityModelDefinition> definitions =
                ReflectionHelper.getValueOfPrivateField(neonBee.getModelManager(), "externalModelDefinitions");
        assertThat(definitions).contains(definition);

        assertThat(deployment.undeploy().succeeded()).isTrue();
        assertThat(definitions).doesNotContain(definition);
    }

    @Test
    @DisplayName("test deploy failed")
    void testDeployFailed() {
        EntityModelDefinition definition = new EntityModelDefinition(Map.of(), Map.of());
        DeployableModels deployable = new DeployableModels(definition);

        Vertx vertxMock = defaultVertxMock();
        when(vertxMock.fileSystem().readDir(any())).thenReturn(failedFuture("any failure"));
        NeonBee neonBee = registerNeonBeeMock(vertxMock, new NeonBeeOptions.Mutable().setIgnoreClassPath(true));

        PendingDeployment deployment = deployable.deploy(neonBee);
        assertThat(deployment.failed()).isTrue();
        assertThat(deployment.cause()).hasMessageThat().isEqualTo("any failure");
        assertThat(deployment.undeploy().succeeded()).isTrue();
    }

    @Test
    @DisplayName("test read model payloads")
    void testReadModelPayloads() {
        Vertx vertxMock = defaultVertxMock();

        ClassLoader classLoaderMock = mock(ClassLoader.class);
        when(classLoaderMock.getResourceAsStream(any())).thenAnswer(invocation -> {
            return new ByteArrayInputStream(invocation.<String>getArgument(0).getBytes(UTF_8));
        });

        Future<Map<String, byte[]>> result =
                DeployableModels.readModelPayloads(vertxMock, classLoaderMock, List.of("foo", "bar"));
        assertThat(result.succeeded()).isTrue();
        assertThat(result.result().keySet()).containsExactly("foo", "bar");
    }

    @Test
    @DisplayName("test scan class path")
    void testScanClassPath() {
        Vertx vertxMock = defaultVertxMock();

        ClassPathScanner classPathScannerMock = mock(ClassPathScanner.class);
        when(classPathScannerMock.scanManifestFiles(any(), any())).thenReturn(succeededFuture(List.of("entry")));

        ClassLoader classLoaderMock = mock(ClassLoader.class);
        when(classPathScannerMock.getClassLoader()).thenReturn(classLoaderMock); // NOPMD
        when(classLoaderMock.getResourceAsStream(any())).thenReturn(new ByteArrayInputStream(new byte[] { 1, 2, 3 }));

        Future<DeployableModels> deployable = DeployableModels.scanClassPath(vertxMock, classPathScannerMock);
        assertThat(deployable.succeeded()).isTrue();
        verify(classPathScannerMock).scanManifestFiles(vertxMock, "NeonBee-Models");
        verify(classPathScannerMock).scanManifestFiles(vertxMock, "NeonBee-Model-Extensions");
        verify(classLoaderMock, times(2)).getResourceAsStream("entry");

        EntityModelDefinition definition = deployable.result().modelDefinition;
        assertThat(definition.getCSNModelDefinitions().keySet()).containsExactly("entry");
        assertThat(definition.getAssociatedModelDefinitions().keySet()).containsExactly("entry");
    }

    @Test
    @DisplayName("test from JAR")
    void testFromJar() throws IOException {
        NeonBeeModuleJar moduleJar = NeonBeeModuleJar.create("testmodule").withModels().build();
        Future<DeployableModels> deployable = DeployableModels.fromJar(defaultVertxMock(), moduleJar.writeToTempPath());
        assertThat(deployable.succeeded()).isTrue();
        assertThat(deployable.result().modelDefinition.getCSNModelDefinitions())
                .comparingValuesUsing(Correspondence.<byte[], byte[]>from(Arrays::equals, "is not equal to"))
                .containsExactlyEntriesIn(NeonBeeModuleJar.DUMMY_MODELS);
        assertThat(deployable.result().modelDefinition.getAssociatedModelDefinitions())
                .comparingValuesUsing(Correspondence.<byte[], byte[]>from(Arrays::equals, "is not equal to"))
                .containsExactlyEntriesIn(NeonBeeModuleJar.DUMMY_EXTENSION_MODELS);
    }
}
