package io.neonbee.internal.verticle;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.internal.helper.FileSystemHelper.createDirs;
import static io.neonbee.internal.helper.FileSystemHelper.deleteRecursive;
import static io.neonbee.internal.helper.FileSystemHelper.writeFile;
import static io.neonbee.internal.verticle.WatchVerticle.WATCH_LOGIC_KEY;
import static io.neonbee.internal.verticle.WatchVerticle.WATCH_LOGIC_OPTION_COPY;
import static io.neonbee.test.helper.FileSystemHelper.createTempDirectory;
import static io.neonbee.test.helper.FileSystemHelper.deleteRecursiveBlocking;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;

import io.neonbee.test.helper.ConcurrentHelper;
import io.neonbee.test.helper.DeploymentHelper;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.junit5.VertxTestContext.ExecutionBlock;

@ExtendWith(VertxExtension.class)
public class WatchVerticleTest {
    private Path watchDir;

    @BeforeEach
    void beforeEach() throws IOException {
        watchDir = createTempDirectory();
    }

    @AfterEach
    void afterEach() throws IOException {
        deleteRecursiveBlocking(watchDir);
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("Constructor should set / calculate interval correct")
    void testConstrcutor() {
        WatchVerticle watchVerticle = new WatchVerticle(watchDir);
        assertThat(watchVerticle.watchPeriodMillis).isEqualTo(500);
        watchVerticle = new WatchVerticle(watchDir, 2, TimeUnit.SECONDS, false, false);
        assertThat(watchVerticle.watchPeriodMillis).isEqualTo(2000);
    }

    @Test
    @DisplayName("isCopyLogic should behave correct")
    void isCopyLogicTest() {
        assertThat(WatchVerticle.isCopyLogic(null)).isFalse();
        assertThat(WatchVerticle.isCopyLogic(new JsonObject())).isFalse();

        JsonObject noCopyLogic = new JsonObject().put(WATCH_LOGIC_KEY, "something else which is not copy");
        assertThat(WatchVerticle.isCopyLogic(noCopyLogic)).isFalse();

        JsonObject copyLogic = new JsonObject().put(WATCH_LOGIC_KEY, WATCH_LOGIC_OPTION_COPY);
        assertThat(WatchVerticle.isCopyLogic(copyLogic)).isTrue();
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("Upcoming check intervals should be ignored, if processing of a predecessor interval is still in progress")
    @DisabledOnOs(OS.MAC)
    // Disabled on macOS until issues with File Watching Service on macOS are solved. We need a cross-platform Java
    // recursive directory watcher, that works well with macOS.
    void testBlocking(Vertx vertx, VertxTestContext testCtx) throws InterruptedException {
        AtomicReference<Promise<Void>> waitFutureReference = new AtomicReference<>(Promise.promise());
        WatchVerticle watchVerticleSpy =
                Mockito.spy(new WatchVerticle(watchDir, 1, TimeUnit.MILLISECONDS, false, false));
        Mockito.doAnswer(invocation -> waitFutureReference.get()).when(watchVerticleSpy).checkForChanges();

        DeploymentHelper.deployVerticle(vertx, watchVerticleSpy).compose(deploymentId ->
        // Wait 10 milliseconds to ensure that interval theoretically could triggered more then once
        ConcurrentHelper.waitFor(vertx, 100).compose(v -> {
            testCtx.verify(() -> Mockito.verify(watchVerticleSpy, Mockito.times(1)).checkForChanges());
            // Check if still no other interval was started
            // Resolve waitFuture to enter a new interval
            waitFutureReference.getAndSet(Promise.<Void>promise()).complete();
            // Wait 10 milliseconds to ensure that WatchVerticle enters a new interval
            return ConcurrentHelper.waitFor(vertx, 100);
        }).onComplete(testCtx.succeeding(v -> {
            // CHeck that WatchVerticle entered a new interval
            testCtx.verify(() -> Mockito.verify(watchVerticleSpy, Mockito.times(2)).checkForChanges());
            testCtx.completeNow();
        })));
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("Upcoming check intervals should not be ignored, if processing of a predecessor interval is still in progress")
    @DisabledOnOs(OS.MAC)
    // Disabled on macOS until issues with File Watching Service on macOS are solved. We need a cross-platform Java
    // recursive directory watcher, that works well with macOS.
    void testParallelProcessing(Vertx vertx, VertxTestContext testCtx) throws InterruptedException {
        WatchVerticle watchVerticleSpy =
                Mockito.spy(new WatchVerticle(watchDir, 10, TimeUnit.MILLISECONDS, true, false));
        Mockito.doAnswer(invocation -> Promise.promise()).when(watchVerticleSpy).checkForChanges();

        DeploymentHelper.deployVerticle(vertx, watchVerticleSpy).compose(deploymentId ->
        // Assumption that the watchVerticle has been called at least 10 times after 100 ms
        Future.future(promise -> vertx.setTimer(110, l -> promise.complete()))).onComplete(testCtx.succeeding(v -> {
            // Check that WatchVerticle has been called at least 10 times
            testCtx.verify(() -> Mockito.verify(watchVerticleSpy, Mockito.atLeast(10)).checkForChanges());
            testCtx.completeNow();
        }));
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("CycleTest: WatchVerticle should detect that a file was created, modified and deleted")
    @DisabledOnOs(OS.MAC)
    // Disabled on macOS until issues with File Watching Service on macOS are solved. We need a cross-platform Java
    // recursive directory watcher, that works well with macOS.
    void test(Vertx vertx, VertxTestContext testCtx) throws InterruptedException {
        WatchVerticle watchVerticleSpy = Mockito.spy(new WatchVerticle(watchDir, 10, TimeUnit.MINUTES, false, false));
        Path watchedFile = watchDir.resolve("watchedFile");

        DeploymentHelper.deployVerticle(vertx, watchVerticleSpy)
                .compose(s -> writeFile(vertx, watchedFile, Buffer.buffer()))
                .compose(v -> verifyFileEvent(testCtx, watchVerticleSpy,
                        () -> Mockito.verify(watchVerticleSpy, Mockito.times(1))
                                .observedCreate(Mockito.eq(watchedFile))))
                .compose(v -> writeFile(vertx, watchedFile, Buffer.buffer(toByte("Lord Citrange")))
                        .compose(innerVoid -> verifyFileEvent(testCtx, watchVerticleSpy,
                                () -> Mockito.verify(watchVerticleSpy).observedModify(Mockito.eq(watchedFile)))))
                .compose(v -> deleteRecursive(vertx, watchedFile)
                        .compose(innerVoid -> verifyFileEvent(testCtx, watchVerticleSpy,
                                () -> Mockito.verify(watchVerticleSpy).observedDelete(Mockito.eq(watchedFile)))))
                .onComplete(testCtx.succeedingThenComplete());
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("WatchVerticle should detect files which already existed in the watchDir before it was started.")
    // Disabled on macOS until issues with File Watching Service on macOS are solved. We need a cross-platform Java
    // recursive directory watcher, that works well with macOS.
    @DisabledOnOs(OS.MAC)
    void testExisting(Vertx vertx, VertxTestContext testCtx) throws InterruptedException {

        WatchVerticle watchVerticleSpy = Mockito.spy(new WatchVerticle(watchDir, 10, TimeUnit.MINUTES, false, true));
        Path watchedFile = watchDir.resolve("watchedFile");

        writeFile(vertx, watchedFile, Buffer.buffer())
                .compose(v -> DeploymentHelper.deployVerticle(vertx, watchVerticleSpy)).compose(s -> {
                    testCtx.verify(() -> verifyCreateModifyFile(watchVerticleSpy, watchedFile));
                    return Future.succeededFuture();
                }).onComplete(testCtx.succeedingThenComplete());
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("CycleTest: WatchVerticle should detect recursivly that a file was created, modified and deleted")
    @DisabledOnOs(OS.MAC)
    // Disabled on macOS until issues with File Watching Service on macOS are solved. We need a cross-platform Java
    // recursive directory watcher, that works well with macOS.
    void testRecursivly(Vertx vertx, VertxTestContext testCtx) throws InterruptedException {

        WatchVerticle watchVerticleSpy = Mockito.spy(new WatchVerticle(watchDir, 10, TimeUnit.MINUTES, false, true));
        Path watchedSubDir = watchDir.resolve("subDir");
        Path watchedSubSubDir = watchedSubDir.resolve("subSubDir");
        Path watchedFileInSubDir = watchedSubDir.resolve("watchedFile");
        Path watchedFileInSubSubDir = watchedSubSubDir.resolve("watchedSubFile");

        DeploymentHelper.deployVerticle(vertx, watchVerticleSpy)
                // Create subdirectory and test events
                .compose(
                        s -> createDirs(vertx, watchedSubDir)
                                .compose(v -> verifyFileEvent(testCtx, watchVerticleSpy,
                                        () -> Mockito.verify(watchVerticleSpy)
                                                .observedCreate(Mockito.eq(watchedSubDir))))
                                .compose(v -> writeFile(vertx, watchedFileInSubDir, Buffer.buffer()))
                                .compose(v -> verifyFileEvent(testCtx, watchVerticleSpy,
                                        () -> Mockito.verify(watchVerticleSpy)
                                                .observedCreate(Mockito.eq(watchedFileInSubDir))))
                                .compose(v -> writeFile(vertx, watchedFileInSubDir,
                                        Buffer.buffer(toByte("Lord Citrange"))))
                                .compose(v -> verifyFileEvent(testCtx, watchVerticleSpy,
                                        () -> Mockito.verify(watchVerticleSpy)
                                                .observedModify(Mockito.eq(watchedFileInSubDir))))
                                .compose(v -> deleteRecursive(vertx, watchedFileInSubDir))
                                .compose(v -> verifyFileEvent(testCtx, watchVerticleSpy,
                                        () -> Mockito.verify(watchVerticleSpy)
                                                .observedDelete(Mockito.eq(watchedFileInSubDir)))))

                // Create sub subdirectory and test events
                .compose(
                        v -> createDirs(vertx, watchedSubSubDir)
                                .compose(innerVoid -> verifyFileEvent(testCtx, watchVerticleSpy,
                                        () -> Mockito.verify(watchVerticleSpy)
                                                .observedCreate(Mockito.eq(watchedSubSubDir))))
                                .compose(innerVoid -> writeFile(vertx, watchedFileInSubSubDir, Buffer.buffer()))
                                .compose(innerVoid -> verifyFileEvent(testCtx, watchVerticleSpy,
                                        () -> Mockito.verify(watchVerticleSpy)
                                                .observedCreate(Mockito.eq(watchedFileInSubSubDir)))))

                // DELETE subdirectory and check if the mapped watchKey is deleted
                .compose(
                        v -> deleteRecursive(vertx, watchedSubDir)
                                .compose(
                                        innerVoid -> verifyFileEvent(testCtx, watchVerticleSpy,
                                                () -> Mockito.verify(watchVerticleSpy)
                                                        .observedDelete(Mockito.eq(watchedSubDir))))
                                .compose(innerVoid -> {
                                    testCtx.verify(() -> assertThat(watchVerticleSpy.watchKeys)
                                            .doesNotContainKey(watchedSubDir));
                                    return Future.succeededFuture();
                                })
                                // check if the subdirectories/files of the deleted directory are deleted as well
                                .compose(
                                        innerVoid -> verifyFileEvent(testCtx, watchVerticleSpy,
                                                () -> Mockito.verify(watchVerticleSpy)
                                                        .observedDelete(Mockito.eq(watchedSubSubDir))))
                                .compose(innerVoid -> {
                                    testCtx.verify(() -> assertThat(watchVerticleSpy.watchKeys)
                                            .doesNotContainKey(watchedSubSubDir));
                                    return Future.succeededFuture();
                                }))
                .onComplete(testCtx.succeedingThenComplete());
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("CycleTest: WatchVerticle should detect existiting files recursivly that a file was created and modified")
    @DisabledOnOs(OS.MAC)
    // Disabled on macOS until issues with File Watching Service on macOS are solved. We need a cross-platform Java
    // recursive directory watcher, that works well with macOS.
    void testHandleExistingRecursivly(Vertx vertx, VertxTestContext testCtx) throws InterruptedException {
        WatchVerticle watchVerticleSpy = Mockito.spy(new WatchVerticle(watchDir, 10, TimeUnit.MINUTES, false, true));
        // Root Directory Content
        Path watchedFile = watchDir.resolve("watchedFile");
        Path watchedSubDir = watchDir.resolve("watchedSubDir");
        // Subdirectory Content
        Path watchedFileInSubDir = watchedSubDir.resolve("watchedFileInSubDir");
        Path watchedFileTwoInSubDir = watchedSubDir.resolve("watchedFileTwoInSubDir");
        Path watchedDirInSubDir = watchedSubDir.resolve("watchedDirInSubDir");
        // Sub-subdirectory Content
        Path watchedFileInSubSubDir = watchedDirInSubDir.resolve("watchedFile");

        Future<Void> createFileStructure =
                createDirs(vertx, watchedDirInSubDir).compose(v -> writeFile(vertx, watchedFile, Buffer.buffer()))
                        .compose(v -> writeFile(vertx, watchedFileInSubDir, Buffer.buffer()))
                        .compose(v -> writeFile(vertx, watchedFileTwoInSubDir, Buffer.buffer()))
                        .compose(v -> writeFile(vertx, watchedFileInSubSubDir, Buffer.buffer()));

        createFileStructure.compose(v -> DeploymentHelper.deployVerticle(vertx, watchVerticleSpy))
                .onComplete(testCtx.succeeding(s -> {
                    testCtx.verify(() -> {
                        verifyCreateModifyFile(watchVerticleSpy, watchedFile);
                        verifyCreateModifyFile(watchVerticleSpy, watchedFileInSubDir);
                        verifyCreateModifyFile(watchVerticleSpy, watchedFileTwoInSubDir);
                        verifyCreateModifyFile(watchVerticleSpy, watchedDirInSubDir);
                        verifyCreateModifyFile(watchVerticleSpy, watchedFileInSubSubDir);
                    });
                    testCtx.completeNow();
                }));
    }

    private static Future<Void> verifyFileEvent(VertxTestContext testCtx, WatchVerticle watchVerticleSpy,
            ExecutionBlock eBlock) {
        return watchVerticleSpy.checkForChanges().compose(s -> {
            testCtx.verify(eBlock);
            return Future.succeededFuture();
        });
    }

    private static void verifyCreateModifyFile(WatchVerticle watchVerticleSpy, Path watchedFile) {
        Mockito.verify(watchVerticleSpy).observedCreate(Mockito.eq(watchedFile));
        Mockito.verify(watchVerticleSpy).observedModify(Mockito.eq(watchedFile));
    }

    private static byte[] toByte(String string) {
        return string.getBytes(UTF_8);
    }
}
