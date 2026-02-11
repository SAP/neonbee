package io.neonbee.internal.deploy;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.internal.deploy.DeploymentTest.newNeonBeeMockForDeployment;
import static io.neonbee.test.helper.DummyVerticleHelper.DUMMY_VERTICLE;
import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.endsWith;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import io.neonbee.NeonBee;
import io.neonbee.NeonBeeOptions;
import io.neonbee.internal.NeonBeeModuleJar;
import io.neonbee.internal.scanner.ClassPathScanner;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.ThreadingModel;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.FileSystem;
import io.vertx.core.file.FileSystemException;
import io.vertx.core.json.JsonObject;

class DeployableVerticleTest {
    @Test
    @DisplayName("test instantiation")
    void testInstantiation() {
        DeploymentOptions options = new DeploymentOptions();

        DeployableVerticle deployable1 = new DeployableVerticle(DUMMY_VERTICLE, options);
        assertThat(deployable1.verticleClass).isNull();
        assertThat(deployable1.verticleInstance).isSameInstanceAs(DUMMY_VERTICLE);
        assertThat(deployable1.options).isEqualTo(options);

        DeployableVerticle deployable2 = new DeployableVerticle(DUMMY_VERTICLE.getClass(), options);
        assertThat(deployable2.verticleClass).isEqualTo(DUMMY_VERTICLE.getClass());
        assertThat(deployable2.verticleInstance).isNull();
        assertThat(deployable2.options).isEqualTo(options);
    }

    @Test
    @DisplayName("test getIdentifier")
    void testGetIdentifier() {
        DeploymentOptions options = new DeploymentOptions();

        DeployableVerticle deployable1 = new DeployableVerticle(DUMMY_VERTICLE, options);
        assertThat(deployable1.getIdentifier()).isEqualTo(DUMMY_VERTICLE.getClass().getName());

        DeployableVerticle deployable2 = new DeployableVerticle(DUMMY_VERTICLE.getClass(), options);
        assertThat(deployable2.getIdentifier()).isEqualTo(DUMMY_VERTICLE.getClass().getName());
    }

    @Test
    @DisplayName("test deploy and undeploy")
    void testDeployUndeploy() {
        DeploymentOptions options = new DeploymentOptions();

        NeonBee neonBeeMock = newNeonBeeMockForDeployment(new NeonBeeOptions.Mutable().setIgnoreClassPath(true));
        Vertx vertxMock = neonBeeMock.getVertx();

        DeployableVerticle deployable1 = new DeployableVerticle(DUMMY_VERTICLE, options);
        assertThat(deployable1.getIdentifier()).isEqualTo(DUMMY_VERTICLE.getClass().getName());

        PendingDeployment deployment1 = deployable1.deploy(neonBeeMock);
        assertThat(deployment1.getDeployment().succeeded()).isTrue();
        verify(vertxMock).deployVerticle(DUMMY_VERTICLE, deployable1.options);

        assertThat(deployment1.undeploy().succeeded()).isTrue();
        verify(vertxMock).undeploy(any());

        DeployableVerticle deployable2 = new DeployableVerticle(DUMMY_VERTICLE.getClass(), options);
        assertThat(deployable2.getIdentifier()).isEqualTo(DUMMY_VERTICLE.getClass().getName());

        PendingDeployment deployment2 = deployable2.deploy(neonBeeMock);
        assertThat(deployment2.getDeployment().succeeded()).isTrue();
        verify(vertxMock).deployVerticle(DUMMY_VERTICLE.getClass(), deployable2.options);

        assertThat(deployment2.undeploy().succeeded()).isTrue();
        verify(vertxMock, times(2)).undeploy(any());
    }

    @Test
    @DisplayName("test deploy failed")
    void testDeployFailed() {
        NeonBee neonBeeMock = newNeonBeeMockForDeployment(new NeonBeeOptions.Mutable().setIgnoreClassPath(true));
        Vertx vertxMock = neonBeeMock.getVertx();
        when(vertxMock.deployVerticle(any(Verticle.class), any(DeploymentOptions.class)))
                .thenReturn(failedFuture("any failure"));

        DeployableVerticle deployable = new DeployableVerticle(DUMMY_VERTICLE, new DeploymentOptions());

        PendingDeployment deployment = deployable.deploy(neonBeeMock);
        assertThat(deployment.getDeployment().failed()).isTrue();
        assertThat(deployment.getDeployment().cause()).hasMessageThat().isEqualTo("any failure");
        assertThat(deployment.undeploy().succeeded()).isTrue();
    }

    @Test
    @DisplayName("test read not found verticle config")
    void testReadVerticleConfigNotFound() throws IOException {
        NeonBee neonBeeMock = newNeonBeeMockForDeployment(
                new NeonBeeOptions.Mutable().setWorkingDirectory(Path.of("")));
        Vertx vertxMock = neonBeeMock.getVertx();
        FileSystem fileSystemMock = vertxMock.fileSystem();

        when(fileSystemMock.readFile(any()))
                .thenReturn(failedFuture(new FileSystemException(new NoSuchFileException("file"))));

        assertThat(DeployableVerticle.readVerticleConfig(vertxMock, "test", null).result().toJson())
                .isEqualTo(new DeploymentOptions().toJson());
        InOrder orderVerifier = inOrder(fileSystemMock);
        orderVerifier.verify(fileSystemMock)
                .readFile(Path.of("config").resolve("test.yaml").toAbsolutePath().toString());
        orderVerifier.verify(fileSystemMock)
                .readFile(Path.of("config").resolve("test.yml").toAbsolutePath().toString());
        orderVerifier.verify(fileSystemMock)
                .readFile(Path.of("config").resolve("test.json").toAbsolutePath().toString());
        verifyNoMoreInteractions(fileSystemMock);
    }

    @Test
    @DisplayName("test read not found verticle config not found with default")
    void testReadVerticleConfigNotFoundWithDefault() throws IOException {
        NeonBee neonBeeMock = newNeonBeeMockForDeployment(
                new NeonBeeOptions.Mutable().setWorkingDirectory(Path.of("")));
        Vertx vertxMock = neonBeeMock.getVertx();
        FileSystem fileSystemMock = vertxMock.fileSystem();

        when(fileSystemMock.readFile(any()))
                .thenReturn(failedFuture(new FileSystemException(new NoSuchFileException("file"))));

        JsonObject defaultObject = new JsonObject().put("ha", true);
        assertThat(DeployableVerticle.readVerticleConfig(vertxMock, "test", defaultObject).result().toJson())
                .isEqualTo(new DeploymentOptions().setHa(true).toJson());
    }

    @Test
    @DisplayName("test read verticle config")
    void testReadVerticleConfig() throws IOException {
        NeonBee neonBeeMock = newNeonBeeMockForDeployment(
                new NeonBeeOptions.Mutable().setWorkingDirectory(Path.of("")));
        Vertx vertxMock = neonBeeMock.getVertx();
        FileSystem fileSystemMock = vertxMock.fileSystem();

        when(fileSystemMock.readFile(any()))
                .thenReturn(failedFuture(new FileSystemException(new NoSuchFileException("file"))));
        when(fileSystemMock.readFile(endsWith(".yml")))
                .thenReturn(succeededFuture(Buffer.buffer("---\nha: true\nworkerPoolName: Test")));

        assertThat(DeployableVerticle.readVerticleConfig(vertxMock, "test", null).result().toJson())
                .isEqualTo(new DeploymentOptions().setHa(true).setWorkerPoolName("Test").toJson());
    }

    @Test
    @DisplayName("test read verticle config with default")
    void testReadVerticleConfigWithDefault() throws IOException {
        NeonBee neonBeeMock = newNeonBeeMockForDeployment(
                new NeonBeeOptions.Mutable().setWorkingDirectory(Path.of("")));
        Vertx vertxMock = neonBeeMock.getVertx();
        FileSystem fileSystemMock = vertxMock.fileSystem();

        when(fileSystemMock.readFile(any()))
                .thenReturn(failedFuture(new FileSystemException(new NoSuchFileException("file"))));
        when(fileSystemMock.readFile(endsWith(".yml")))
                .thenReturn(succeededFuture(Buffer.buffer("---\nha: true\nworkerPoolName: Test")));

        JsonObject defaultObject = new JsonObject().put("ha", false).put("instances", 1337);
        assertThat(DeployableVerticle.readVerticleConfig(vertxMock, "test", defaultObject).result().toJson())
                .isEqualTo(new DeploymentOptions().setHa(true).setWorkerPoolName("Test").setInstances(1337).toJson());
    }

    @Test
    @DisplayName("test read verticle config failure")
    void testReadVerticleConfigFailure() throws IOException {
        NeonBee neonBeeMock = newNeonBeeMockForDeployment(
                new NeonBeeOptions.Mutable().setWorkingDirectory(Path.of("")));
        Vertx vertxMock = neonBeeMock.getVertx();
        FileSystem fileSystemMock = vertxMock.fileSystem();

        when(fileSystemMock.readFile(any()))
                .thenReturn(failedFuture(new FileSystemException(new NoSuchFileException("file"))));
        when(fileSystemMock.readFile(endsWith(".yml"))).thenReturn(failedFuture("test"));

        JsonObject defaultObject = new JsonObject().put("ha", false).put("instances", 1337);
        assertThat(DeployableVerticle.readVerticleConfig(vertxMock, "test", defaultObject).cause()).hasMessageThat()
                .isEqualTo("test");
    }

    @Test
    @DisplayName("test read verticle config threading model")
    void testReadVerticleConfigThreadingModel() throws IOException {
        NeonBee neonBeeMock = newNeonBeeMockForDeployment(
                new NeonBeeOptions.Mutable().setWorkingDirectory(Path.of("")));
        Vertx vertxMock = neonBeeMock.getVertx();
        FileSystem fileSystemMock = vertxMock.fileSystem();

        when(fileSystemMock.readFile(any()))
                .thenReturn(failedFuture(new FileSystemException(new NoSuchFileException("file"))));
        when(fileSystemMock.readFile(endsWith(".yml")))
                .thenReturn(succeededFuture(Buffer.buffer("---\nthreadingModel: WORKER")));

        JsonObject defaultObject = new JsonObject().put("threadingModel", "EVENT_LOOP");
        assertThat(DeployableVerticle.readVerticleConfig(vertxMock, "test", defaultObject).result().toJson())
                .isEqualTo(new DeploymentOptions().setThreadingModel(ThreadingModel.WORKER).toJson());
    }

    @Test
    @DisplayName("test scan class path")
    @SuppressWarnings("rawtypes")
    void testScanClassPath() throws ClassNotFoundException {
        NeonBee neonBeeMock = newNeonBeeMockForDeployment();
        Vertx vertxMock = neonBeeMock.getVertx();

        ClassPathScanner classPathScannerMock = mock(ClassPathScanner.class);
        when(classPathScannerMock.scanManifestFiles(any(), any()))
                .thenReturn(succeededFuture(List.of("entry", "entry2")));

        ClassLoader classLoaderMock = mock(ClassLoader.class);
        when(classPathScannerMock.getClassLoader()).thenReturn(classLoaderMock); // NOPMD
        when((Class) classLoaderMock.loadClass(any())).thenReturn(DUMMY_VERTICLE.getClass());

        Future<Collection<DeployableVerticle>> deployable =
                DeployableVerticle.scanClassPath(vertxMock, classPathScannerMock, classLoaderMock);
        assertThat(deployable.cause()).isNull();
        assertThat(deployable.succeeded()).isTrue();
        verify(classPathScannerMock).scanManifestFiles(vertxMock, "NeonBee-Deployables");
        verify(classLoaderMock).loadClass("entry");
        verify(classLoaderMock).loadClass("entry2");

        List<DeployableVerticle> verticles = new ArrayList<>(deployable.result());
        assertThat(verticles).hasSize(2);
        assertThat(verticles.get(0).verticleClass).isEqualTo(DUMMY_VERTICLE.getClass());
        assertThat(verticles.get(1).verticleClass).isEqualTo(DUMMY_VERTICLE.getClass());
    }

    @Test
    @DisplayName("test from JAR")
    void testFromJar() throws IOException {
        NeonBee neonBeeMock = newNeonBeeMockForDeployment();
        Vertx vertxMock = neonBeeMock.getVertx();

        NeonBeeModuleJar moduleJar = NeonBeeModuleJar.create("testmodule").withVerticles().build();
        Path moduleJarPath = moduleJar.writeToTempPath();
        URLClassLoader classLoader =
                new URLClassLoader(new URL[] { moduleJarPath.toUri().toURL() }, ClassLoader.getSystemClassLoader());
        Future<Collection<DeployableVerticle>> deployable =
                DeployableVerticle.fromJar(vertxMock, moduleJarPath, classLoader);
        assertThat(deployable.cause()).isNull();
        assertThat(deployable.succeeded()).isTrue();

        List<DeployableVerticle> verticles = new ArrayList<>(deployable.result());
        assertThat(verticles.stream().map(deployableVerticle -> deployableVerticle.verticleClass)
                .map(Class::getSimpleName)).containsExactly("ClassA", "ClassB");
    }

    @Test
    @DisplayName("test from class name")
    @SuppressWarnings("rawtypes")
    void testFromClassName() throws ClassNotFoundException {
        NeonBee neonBeeMock = newNeonBeeMockForDeployment();
        Vertx vertxMock = neonBeeMock.getVertx();

        JsonObject config = new JsonObject().put("config", new JsonObject().put("foo", "bar"));

        Future<DeployableVerticle> deployable1 =
                DeployableVerticle.fromClassName(vertxMock, DUMMY_VERTICLE.getClass().getName());
        assertThat(deployable1.succeeded()).isTrue();
        assertThat(deployable1.result().verticleClass).isEqualTo(DUMMY_VERTICLE.getClass());

        Future<DeployableVerticle> deployable2 =
                DeployableVerticle.fromClassName(vertxMock, DUMMY_VERTICLE.getClass().getName(), config);
        assertThat(deployable2.succeeded()).isTrue();
        assertThat(deployable2.result().verticleClass).isEqualTo(DUMMY_VERTICLE.getClass());
        assertThat(deployable2.result().options.getConfig()).isEqualTo(config.getJsonObject("config"));

        ClassLoader classLoaderMock = mock(ClassLoader.class);
        when((Class) classLoaderMock.loadClass(any())).thenReturn(DUMMY_VERTICLE.getClass());

        Future<DeployableVerticle> deployable3 = DeployableVerticle.fromClassName(vertxMock, "test", classLoaderMock);
        assertThat(deployable3.succeeded()).isTrue();
        assertThat(deployable3.result().verticleClass).isEqualTo(DUMMY_VERTICLE.getClass());

        Future<DeployableVerticle> deployable4 =
                DeployableVerticle.fromClassName(vertxMock, "test", classLoaderMock, config);
        assertThat(deployable4.succeeded()).isTrue();
        assertThat(deployable4.result().verticleClass).isEqualTo(DUMMY_VERTICLE.getClass());
        assertThat(deployable4.result().options.getConfig()).isEqualTo(config.getJsonObject("config"));
    }

    @Test
    @DisplayName("test from wrong class name")
    void testFromWrongClassName() throws ClassNotFoundException {
        NeonBee neonBeeMock = newNeonBeeMockForDeployment();
        Vertx vertxMock = neonBeeMock.getVertx();

        ClassLoader classLoaderMock = mock(ClassLoader.class);
        when(classLoaderMock.loadClass(any())).thenThrow(ClassNotFoundException.class);

        Future<DeployableVerticle> deployable =
                DeployableVerticle.fromClassName(vertxMock, "a wrong class name", classLoaderMock);
        assertThat(deployable.failed()).isTrue();
        assertThat(deployable.cause()).isInstanceOf(ClassNotFoundException.class);
    }

    @Test
    @DisplayName("test from class")
    void testFromClass() {
        NeonBee neonBeeMock = newNeonBeeMockForDeployment();
        Vertx vertxMock = neonBeeMock.getVertx();

        JsonObject config = new JsonObject().put("config", new JsonObject().put("foo", "bar"));

        Future<DeployableVerticle> deployable1 = DeployableVerticle.fromClass(vertxMock, DUMMY_VERTICLE.getClass());
        assertThat(deployable1.succeeded()).isTrue();
        assertThat(deployable1.result().verticleClass).isEqualTo(DUMMY_VERTICLE.getClass());

        Future<DeployableVerticle> deployable2 =
                DeployableVerticle.fromClass(vertxMock, DUMMY_VERTICLE.getClass(), config);
        assertThat(deployable2.succeeded()).isTrue();
        assertThat(deployable2.result().verticleClass).isEqualTo(DUMMY_VERTICLE.getClass());
        assertThat(deployable2.result().options.getConfig()).isEqualTo(config.getJsonObject("config"));
    }

    @Test
    @DisplayName("test from verticle")
    void testFromVerticle() {
        NeonBee neonBeeMock = newNeonBeeMockForDeployment();
        Vertx vertxMock = neonBeeMock.getVertx();

        JsonObject config = new JsonObject().put("config", new JsonObject().put("foo", "bar"));

        Future<DeployableVerticle> deployable1 = DeployableVerticle.fromVerticle(vertxMock, DUMMY_VERTICLE);
        assertThat(deployable1.succeeded()).isTrue();
        assertThat(deployable1.result().verticleInstance).isSameInstanceAs(DUMMY_VERTICLE);

        Future<DeployableVerticle> deployable2 = DeployableVerticle.fromVerticle(vertxMock, DUMMY_VERTICLE, config);
        assertThat(deployable2.succeeded()).isTrue();
        assertThat(deployable2.result().verticleInstance).isSameInstanceAs(DUMMY_VERTICLE);
        assertThat(deployable2.result().options.getConfig()).isEqualTo(config.getJsonObject("config"));
    }
}
