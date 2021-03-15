package io.neonbee.helper;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.internal.helper.FileSystemHelper.createDirs;
import static io.neonbee.internal.helper.FileSystemHelper.deleteRecursive;
import static io.neonbee.internal.helper.FileSystemHelper.exists;
import static io.neonbee.internal.helper.FileSystemHelper.isDirectory;
import static io.neonbee.internal.helper.FileSystemHelper.openFile;
import static io.neonbee.internal.helper.FileSystemHelper.props;
import static io.neonbee.internal.helper.FileSystemHelper.readDir;
import static io.neonbee.internal.helper.FileSystemHelper.readFile;
import static io.neonbee.internal.helper.FileSystemHelper.writeFile;
import static io.neonbee.test.helper.FileSystemHelper.createTempDirectory;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.OpenOptions;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class FileSystemHelperTest {
    private Path tempDir;

    @BeforeEach
    void beforeEach() throws IOException {
        tempDir = createTempDirectory();
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("Test should read the content from the passed directory path")
    void testReadDir(Vertx vertx, VertxTestContext testContext) {
        Path subDir = tempDir.resolve("subDir");
        Path subFile = tempDir.resolve("subFile");

        CompositeFuture.all(createDirs(vertx, subDir), writeFile(vertx, subFile, Buffer.buffer()))
                .compose(v -> readDir(vertx, tempDir)).onComplete(testContext.succeeding(dirContent -> {
                    testContext.verify(() -> {
                        List<Path> tempDirContent = List.of(subFile.toRealPath(), subDir.toRealPath());
                        assertThat(dirContent).containsExactlyElementsIn(tempDirContent);
                    });
                    testContext.completeNow();
                }));
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("Test should read the content from the passed directory path based on the passed filter query")
    void testReadDirWithFilter(Vertx vertx, VertxTestContext testContext) {
        Path subDir = tempDir.resolve("subDir");
        Path subFile = tempDir.resolve("subFile.edmx");

        CompositeFuture.all(createDirs(vertx, subDir), writeFile(vertx, subFile, Buffer.buffer()))
                .compose(v -> readDir(vertx, tempDir, "(.+)(\\.edmx$)"))
                .onComplete(testContext.succeeding(dirContent -> {
                    testContext.verify(() -> assertThat(dirContent).containsExactly(subFile.toRealPath()));
                    testContext.completeNow();
                }));
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("Test should check if the paths are directories")
    void testIfFileIsDir(Vertx vertx, VertxTestContext testContext) {
        Path subFile = tempDir.resolve("subFile");

        isDirectory(vertx, tempDir).compose(isDir -> {
            assertThat(isDir).isTrue();
            return writeFile(vertx, subFile, Buffer.buffer());
        }).compose(v -> isDirectory(vertx, subFile)).onComplete(testContext.succeeding(isDir -> {
            assertThat(isDir).isFalse();
            testContext.completeNow();
        }));
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("Test should open a file")
    void testOpenFile(Vertx vertx, VertxTestContext testContext) throws IOException {
        Path subFile = tempDir.resolve("subFile");
        Buffer expectedContent = Buffer.buffer("lord citrange".getBytes(UTF_8));
        Files.write(subFile, "lord citrange".getBytes(UTF_8));
        Buffer gotBuffer = Buffer.buffer();
        openFile(vertx, new OpenOptions(), subFile)
                .compose(asyncFile -> Future
                        .<Buffer>future(promise -> asyncFile.read(gotBuffer, 0, 0L, expectedContent.length(), promise)))
                .onComplete(testContext.succeeding(buffer -> testContext.verify(() -> {
                    assertThat(buffer).isEqualTo(expectedContent);
                    testContext.completeNow();
                })));
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("Test should read a file")
    void testReadFile(Vertx vertx, VertxTestContext testContext) throws IOException {
        Path subFile = tempDir.resolve("subFile");
        Buffer expectedContent = Buffer.buffer("lord citrange".getBytes(UTF_8));

        Files.write(subFile, "lord citrange".getBytes(UTF_8));
        readFile(vertx, subFile).onComplete(testContext.succeeding(buffer -> {
            testContext.verify(() -> assertThat(buffer).isEqualTo(expectedContent));
            testContext.completeNow();
        }));
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("Test should write a file")
    void testWriteFile(Vertx vertx, VertxTestContext testContext) {
        Path subFile = tempDir.resolve("subFile");
        String expectedContent = "lord citrange";

        writeFile(vertx, subFile, Buffer.buffer("lord citrange".getBytes(UTF_8)))
                .onComplete(testContext.succeeding(v -> {
                    testContext.verify(() -> assertThat(Files.readString(subFile)).isEqualTo(expectedContent));
                    testContext.completeNow();
                }));
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("Test should delete recursively the content from the passed directory path")
    void testDeleteRecursive(Vertx vertx, VertxTestContext testContext) {
        Path subDir = tempDir.resolve("subDir");

        createDirs(vertx, subDir).compose(v -> deleteRecursive(vertx, tempDir)).compose(v -> exists(vertx, tempDir))
                .onComplete(testContext.succeeding(isExisting -> {
                    assertThat(isExisting).isFalse();
                    testContext.completeNow();
                }));
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("Test should check if a file exists")
    void testExistsFile(Vertx vertx, VertxTestContext testContext) {
        Path subFile = tempDir.resolve("subFile");

        exists(vertx, tempDir).compose(isExisting -> {
            assertThat(isExisting).isTrue();
            return exists(vertx, subFile);
        }).onComplete(testContext.succeeding(isExisting -> {
            assertThat(isExisting).isFalse();
            testContext.completeNow();
        }));
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("Test should create directories")
    void testCreateDirs(Vertx vertx, VertxTestContext testContext) {
        Path subDir = tempDir.resolve("subDir");
        Path subSubDir = subDir.resolve("subSubDir");

        createDirs(vertx, subSubDir).compose(v -> exists(vertx, subSubDir))
                .onComplete(testContext.succeeding(isExisting -> {
                    assertThat(isExisting).isTrue();
                    testContext.completeNow();
                }));
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("Test should identify the property of the file")
    void testProps(Vertx vertx, VertxTestContext testContext) {
        props(vertx, tempDir).onComplete(testContext.succeeding(property -> {
            assertThat(property.isDirectory()).isTrue();
            testContext.completeNow();
        }));
    }
}
