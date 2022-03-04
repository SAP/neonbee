package io.neonbee.helper;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.NeonBeeMockHelper.defaultVertxMock;
import static io.neonbee.NeonBeeMockHelper.registerNeonBeeMock;
import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.endsWith;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

import io.neonbee.NeonBeeOptions;
import io.neonbee.internal.helper.ConfigHelper;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.FileSystem;
import io.vertx.core.file.FileSystemException;
import io.vertx.core.json.JsonObject;

class ConfigHelperTest {
    @Test
    void testReadConfigNotFound() {
        Vertx vertxMock = defaultVertxMock();
        FileSystem fileSystemMock = vertxMock.fileSystem();
        registerNeonBeeMock(vertxMock, new NeonBeeOptions.Mutable().setWorkingDirectory(Path.of("")));

        when(fileSystemMock.readFile(any()))
                .thenReturn(failedFuture(new FileSystemException(new NoSuchFileException("file"))));

        assertThat(ConfigHelper.readConfig(vertxMock, "test").cause()).isInstanceOf(FileSystemException.class);

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
    void testReadConfigException() {
        Vertx vertxMock = defaultVertxMock();
        FileSystem fileSystemMock = vertxMock.fileSystem();
        registerNeonBeeMock(vertxMock, new NeonBeeOptions.Mutable().setWorkingDirectory(Path.of("")));

        when(fileSystemMock.readFile(any()))
                .thenReturn(failedFuture(new FileSystemException(new NoSuchFileException("file"))))
                .thenReturn(failedFuture("any other exception"));

        assertThat(ConfigHelper.readConfig(vertxMock, "test").cause()).hasMessageThat()
                .isEqualTo("any other exception");
        InOrder orderVerifier = inOrder(fileSystemMock);
        orderVerifier.verify(fileSystemMock)
                .readFile(Path.of("config").resolve("test.yaml").toAbsolutePath().toString());
        orderVerifier.verify(fileSystemMock)
                .readFile(Path.of("config").resolve("test.yml").toAbsolutePath().toString());
        verifyNoMoreInteractions(fileSystemMock);

        assertThat(ConfigHelper.readConfig(vertxMock, "test", new JsonObject()).cause()).hasMessageThat()
                .isEqualTo("any other exception");
    }

    @Test
    void testReadConfigDefault() {
        Vertx vertxMock = defaultVertxMock();
        FileSystem fileSystemMock = vertxMock.fileSystem();
        registerNeonBeeMock(vertxMock, new NeonBeeOptions.Mutable().setWorkingDirectory(Path.of("")));

        when(fileSystemMock.readFile(any()))
                .thenReturn(failedFuture(new FileSystemException(new NoSuchFileException("file"))));

        JsonObject defaultObject = new JsonObject();
        assertThat(ConfigHelper.readConfig(vertxMock, "test", defaultObject).result()).isSameInstanceAs(defaultObject);
    }

    @Test
    void testReadYamlConfig() {
        Vertx vertxMock = defaultVertxMock();
        FileSystem fileSystemMock = vertxMock.fileSystem();
        registerNeonBeeMock(vertxMock, new NeonBeeOptions.Mutable().setWorkingDirectory(Path.of("")));

        when(fileSystemMock.readFile(endsWith(".yaml"))).thenReturn(succeededFuture(Buffer.buffer("---\ntest: Foo")));

        assertThat(ConfigHelper.readConfig(vertxMock, "test").result()).isEqualTo(new JsonObject().put("test", "Foo"));
    }

    @Test
    void testReadYmlConfig() {
        Vertx vertxMock = defaultVertxMock();
        FileSystem fileSystemMock = vertxMock.fileSystem();
        registerNeonBeeMock(vertxMock, new NeonBeeOptions.Mutable().setWorkingDirectory(Path.of("")));

        when(fileSystemMock.readFile(any()))
                .thenReturn(failedFuture(new FileSystemException(new NoSuchFileException("file"))));
        when(fileSystemMock.readFile(endsWith(".yml"))).thenReturn(succeededFuture(Buffer.buffer("---\ntest: Foo")));

        assertThat(ConfigHelper.readConfig(vertxMock, "test").result()).isEqualTo(new JsonObject().put("test", "Foo"));
    }

    @Test
    void testReadJsonConfig() {
        Vertx vertxMock = defaultVertxMock();
        FileSystem fileSystemMock = vertxMock.fileSystem();
        registerNeonBeeMock(vertxMock, new NeonBeeOptions.Mutable().setWorkingDirectory(Path.of("")));

        when(fileSystemMock.readFile(any()))
                .thenReturn(failedFuture(new FileSystemException(new NoSuchFileException("file"))));
        when(fileSystemMock.readFile(endsWith(".json")))
                .thenReturn(succeededFuture(Buffer.buffer("{\"test\":\"Foo\"}")));

        assertThat(ConfigHelper.readConfig(vertxMock, "test").result()).isEqualTo(new JsonObject().put("test", "Foo"));
    }

    @Test
    void testRephraseConfigNames() {
        BiMap<String, String> rephraseMap = HashBiMap.create();
        rephraseMap.put("foo", "bar");
        rephraseMap.put("baz", "qux");

        assertThat(ConfigHelper.rephraseConfigNames(new JsonObject().put("foo", "test").put("baz", "test2"),
                HashBiMap.create(), false)).isEqualTo(new JsonObject().put("foo", "test").put("baz", "test2"));
        assertThat(ConfigHelper.rephraseConfigNames(new JsonObject().put("foo", "test").put("baz", "test2"),
                rephraseMap, false)).isEqualTo(new JsonObject().put("bar", "test").put("qux", "test2"));
        assertThat(ConfigHelper.rephraseConfigNames(new JsonObject().put("hello", "test").put("world", "test2"),
                rephraseMap, false)).isEqualTo(
                        new JsonObject().put("hello", "test").put("world", "test2").put("bar", null).put("qux", null));
        assertThat(ConfigHelper.rephraseConfigNames(new JsonObject().put("bar", "test").put("qux", "test2"),
                rephraseMap, true)).isEqualTo(new JsonObject().put("foo", "test").put("baz", "test2"));
    }

    @Test
    void testCollectAdditionalConfig() {
        JsonObject object = new JsonObject().put("foo", "test1").put("bar", new JsonObject().put("baz", "test2"))
                .put("baz", 3).put("additionalConfig", new JsonObject().put("qux", "test4"));
        assertThat(ConfigHelper.collectAdditionalConfig(object)).isEqualTo(
                new JsonObject().put("foo", "test1").put("bar", new JsonObject().put("baz", "test2")).put("baz", 3));
        assertThat(ConfigHelper.collectAdditionalConfig(object, "bar"))
                .isEqualTo(new JsonObject().put("foo", "test1").put("baz", 3));
        assertThat(ConfigHelper.collectAdditionalConfig(object, "qux")).isEqualTo(
                new JsonObject().put("foo", "test1").put("bar", new JsonObject().put("baz", "test2")).put("baz", 3));
        assertThat(ConfigHelper.collectAdditionalConfig(object, "foo", "bar"))
                .isEqualTo(new JsonObject().put("baz", 3));
        assertThat(ConfigHelper.collectAdditionalConfig(object, "foo", "bar", "baz")).isEqualTo(new JsonObject());
    }
}
