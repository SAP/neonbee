package io.neonbee.internal.verticle;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.NeonBeeMockHelper.defaultVertxMock;
import static io.neonbee.NeonBeeMockHelper.registerNeonBeeMock;
import static io.neonbee.NeonBeeProfile.NO_WEB;
import static io.neonbee.internal.helper.FileSystemHelper.createDirs;
import static io.neonbee.internal.helper.FileSystemHelper.deleteRecursive;
import static io.neonbee.internal.helper.FileSystemHelper.writeFile;
import static io.neonbee.internal.verticle.WatchVerticle.WATCH_LOGIC_KEY;
import static io.neonbee.internal.verticle.WatchVerticle.WATCH_LOGIC_OPTION_COPY;
import static io.neonbee.test.helper.ConcurrentHelper.waitFor;
import static io.neonbee.test.helper.FileSystemHelper.createTempDirectory;
import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import io.neonbee.NeonBeeOptions;
import io.neonbee.test.base.NeonBeeTestBase;
import io.neonbee.test.helper.DeploymentHelper;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxTestContext;
import io.vertx.junit5.VertxTestContext.ExecutionBlock;

class WatchVerticleTest extends NeonBeeTestBase {
    private Path watchDir;

    @Override
    protected void adaptOptions(TestInfo testInfo, NeonBeeOptions.Mutable options) {
        options.setDoNotWatchFiles(false);
        options.addActiveProfile(NO_WEB);
    }

    @BeforeEach
    void beforeEach() throws IOException {
        watchDir = createTempDirectory();
    }

    @AfterEach
    void afterEach(Vertx vertx, VertxTestContext testContext) throws IOException {
        deleteRecursive(vertx, watchDir).recover(throwable -> {
            if (throwable.getCause() instanceof DirectoryNotEmptyException) {
                // especially on windows machines, open file handles sometimes cause an issue that the directory cannot
                // be deleted, wait a little and try again afterwards
                return waitFor(vertx, 250).compose(nothing -> deleteRecursive(vertx, watchDir));
            } else {
                return failedFuture(throwable);
            }
        }).onComplete(testContext.succeedingThenComplete());
    }

    @Test
    @DisplayName("Constructor should set / calculate interval correct")
    void testConstructor() {
        WatchVerticle watchVerticle = new WatchVerticle(watchDir);
        assertThat(watchVerticle.watchPeriodMillis).isEqualTo(500);
        watchVerticle = new WatchVerticle(watchDir, 2, TimeUnit.SECONDS, false, false);
        assertThat(watchVerticle.watchPeriodMillis).isEqualTo(2000);
    }

    @Test
    @DisplayName("Test to do not watch files")
    @SuppressWarnings("unchecked")
    void testDontWatchFiles() {
        Vertx vertxMock = defaultVertxMock();
        registerNeonBeeMock(vertxMock, new NeonBeeOptions.Mutable().setDoNotWatchFiles(true));

        WatchVerticle watchVerticle = new WatchVerticle(watchDir);
        watchVerticle.init(vertxMock, vertxMock.getOrCreateContext()); // set the Vert.x instance

        Promise<Void> promiseMock = mock(Promise.class);
        watchVerticle.start(promiseMock);
        verify(promiseMock).complete();

        verify(vertxMock).undeploy(anyString());
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
    @DisplayName("Upcoming check intervals should be ignored, if processing of a predecessor interval is still in progress")
    @DisabledIfEnvironmentVariable(named = "CI", matches = "true")
    @DisabledOnOs(value = { OS.MAC },
            disabledReason = "Issues with File Watching Service on macOS. We need a cross-platform Java recursive directory watcher, that works well with macOS")
    void testBlocking(Vertx vertx, VertxTestContext testCtx) throws InterruptedException {
        AtomicReference<Promise<Void>> waitFutureReference = new AtomicReference<>(Promise.promise());
        WatchVerticle watchVerticleSpy = spy(new WatchVerticle(watchDir, 1, TimeUnit.MILLISECONDS, false, false));
        doAnswer(invocation -> waitFutureReference.get()).when(watchVerticleSpy).checkForChanges();

        DeploymentHelper.deployVerticle(vertx, watchVerticleSpy).compose(deploymentId ->
        // Wait 100 milliseconds to ensure that interval theoretically could triggered more then once
        waitFor(vertx, 100).compose(v -> {
            testCtx.verify(() -> verify(watchVerticleSpy, times(1)).checkForChanges());
            // Check if still no other interval was started
            // Resolve waitFuture to enter a new interval
            waitFutureReference.getAndSet(Promise.<Void>promise()).complete();
            // Wait 10 milliseconds to ensure that WatchVerticle enters a new interval
            return waitFor(vertx, 100);
        }).onComplete(testCtx.succeeding(v -> {
            // CHeck that WatchVerticle entered a new interval
            testCtx.verify(() -> verify(watchVerticleSpy, times(2)).checkForChanges());
            testCtx.completeNow();
        })));
    }

    @Test
    @DisplayName("Upcoming check intervals should not be ignored, if processing of a predecessor interval is still in progress")
    @DisabledIfEnvironmentVariable(named = "CI", matches = "true")
    @DisabledOnOs(value = { OS.MAC },
            disabledReason = "Issues with File Watching Service on macOS. We need a cross-platform Java recursive directory watcher, that works well with macOS")
    void testParallelProcessing(Vertx vertx, VertxTestContext testCtx) throws InterruptedException {
        WatchVerticle watchVerticleSpy = spy(new WatchVerticle(watchDir, 10, TimeUnit.MILLISECONDS, true, false));
        doAnswer(invocation -> Promise.promise()).when(watchVerticleSpy).checkForChanges();

        DeploymentHelper.deployVerticle(vertx, watchVerticleSpy).compose(deploymentId ->
        // Assumption that the watchVerticle has been called at least 10 times after 100 ms
        waitFor(vertx, 110)).onComplete(testCtx.succeeding(v -> {
            // Check that WatchVerticle has been called at least 10 times
            testCtx.verify(() -> verify(watchVerticleSpy, atLeast(10)).checkForChanges());
            testCtx.completeNow();
        }));
    }

    @Test
    @DisplayName("CycleTest: WatchVerticle should detect that a file was created, modified and deleted")
    @DisabledOnOs(value = { OS.MAC },
            disabledReason = "Issues with File Watching Service on macOS. We need a cross-platform Java recursive directory watcher, that works well with macOS")
    void test(Vertx vertx, VertxTestContext testCtx) throws InterruptedException {
        WatchVerticle watchVerticleSpy = spy(new WatchVerticle(watchDir, 10, TimeUnit.MINUTES, false, false));
        Path watchedFile = watchDir.resolve("watchedFile");

        DeploymentHelper.deployVerticle(vertx, watchVerticleSpy)
                .compose(s -> writeFile(vertx, watchedFile, Buffer.buffer()))
                .compose(v -> verifyFileEvent(vertx, testCtx, watchVerticleSpy,
                        () -> verify(watchVerticleSpy, atLeast(1)).observedCreate(eq(watchedFile))))
                .compose(v -> writeFile(vertx, watchedFile, Buffer.buffer(toByte("Lord Citrange")))
                        .compose(innerVoid -> verifyFileEvent(vertx, testCtx, watchVerticleSpy,
                                () -> verify(watchVerticleSpy, atLeast(1)).observedModify(eq(watchedFile)))))
                .compose(v -> deleteRecursive(vertx, watchedFile).compose(innerVoid -> verifyFileEvent(vertx, testCtx,
                        watchVerticleSpy, () -> verify(watchVerticleSpy, atLeast(1)).observedDelete(eq(watchedFile)))))
                .onComplete(testCtx.succeedingThenComplete());
    }

    @Test
    @DisplayName("WatchVerticle should detect files which already existed in the watchDir before it was started.")
    @DisabledOnOs(value = { OS.MAC },
            disabledReason = "Issues with File Watching Service on macOS. We need a cross-platform Java recursive directory watcher, that works well with macOS")
    void testExisting(Vertx vertx, VertxTestContext testCtx) throws InterruptedException {
        WatchVerticle watchVerticleSpy = spy(new WatchVerticle(watchDir, 10, TimeUnit.MINUTES, false, true));
        Path watchedFile = watchDir.resolve("watchedFile");

        writeFile(vertx, watchedFile, Buffer.buffer())
                .compose(v -> DeploymentHelper.deployVerticle(vertx, watchVerticleSpy)).compose(s -> {
                    testCtx.verify(() -> verifyCreateModifyFile(watchVerticleSpy, watchedFile));
                    return succeededFuture();
                }).onComplete(testCtx.succeedingThenComplete());
    }

    @Test
    @DisplayName("CycleTest: WatchVerticle should detect recursively that a file was created, modified and deleted")
    @DisabledOnOs(value = { OS.MAC },
            disabledReason = "Issues with File Watching Service on macOS. We need a cross-platform Java recursive directory watcher, that works well with macOS")
    void testRecursivly(Vertx vertx, VertxTestContext testCtx) throws InterruptedException {

        WatchVerticle watchVerticleSpy = spy(new WatchVerticle(watchDir, 10, TimeUnit.MINUTES, false, true));
        Path watchedSubDir = watchDir.resolve("subDir");
        Path watchedSubSubDir = watchedSubDir.resolve("subSubDir");
        Path watchedFileInSubDir = watchedSubDir.resolve("watchedFile");
        Path watchedFileInSubSubDir = watchedSubSubDir.resolve("watchedSubFile");

        DeploymentHelper.deployVerticle(vertx, watchVerticleSpy)
                // Create subdirectory and test events
                .compose(s -> createDirs(vertx, watchedSubDir)
                        .compose(v -> verifyFileEvent(vertx, testCtx, watchVerticleSpy,
                                () -> verify(watchVerticleSpy, atLeast(1)).observedCreate(eq(watchedSubDir))))
                        .compose(v -> writeFile(vertx, watchedFileInSubDir, Buffer.buffer()))
                        .compose(v -> verifyFileEvent(vertx, testCtx, watchVerticleSpy,
                                () -> verify(watchVerticleSpy, atLeast(1)).observedCreate(eq(watchedFileInSubDir))))
                        .compose(v -> writeFile(vertx, watchedFileInSubDir, Buffer.buffer(toByte("Lord Citrange"))))
                        .compose(v -> verifyFileEvent(vertx, testCtx, watchVerticleSpy,
                                () -> verify(watchVerticleSpy, atLeast(1)).observedModify(eq(watchedFileInSubDir))))
                        .compose(v -> deleteRecursive(vertx, watchedFileInSubDir))
                        .compose(v -> verifyFileEvent(vertx, testCtx, watchVerticleSpy,
                                () -> verify(watchVerticleSpy, atLeast(1)).observedDelete(eq(watchedFileInSubDir)))))

                // Create sub subdirectory and test events
                .compose(v -> createDirs(vertx, watchedSubSubDir)
                        .compose(innerVoid -> verifyFileEvent(vertx, testCtx, watchVerticleSpy,
                                () -> verify(watchVerticleSpy).observedCreate(eq(watchedSubSubDir))))
                        .compose(innerVoid -> writeFile(vertx, watchedFileInSubSubDir, Buffer.buffer()))
                        .compose(innerVoid -> verifyFileEvent(vertx, testCtx, watchVerticleSpy,
                                () -> verify(watchVerticleSpy).observedCreate(eq(watchedFileInSubSubDir)))))

                // DELETE subdirectory and check if the mapped watchKey is deleted
                .compose(v -> deleteRecursive(vertx, watchedSubDir)
                        .compose(innerVoid -> verifyFileEvent(vertx, testCtx, watchVerticleSpy,
                                () -> verify(watchVerticleSpy).observedDelete(eq(watchedSubDir))))
                        .compose(innerVoid -> {
                            testCtx.verify(
                                    () -> assertThat(watchVerticleSpy.watchKeys).doesNotContainKey(watchedSubDir));
                            return succeededFuture();
                        })
                        // check if the subdirectories/files of the deleted directory are deleted as well
                        .compose(innerVoid -> verifyFileEvent(vertx, testCtx, watchVerticleSpy,
                                () -> verify(watchVerticleSpy).observedDelete(eq(watchedSubSubDir))))
                        .compose(innerVoid -> {
                            testCtx.verify(
                                    () -> assertThat(watchVerticleSpy.watchKeys).doesNotContainKey(watchedSubSubDir));
                            return succeededFuture();
                        }))
                .onComplete(testCtx.succeedingThenComplete());
    }

    @Test
    @DisplayName("CycleTest: WatchVerticle should detect existiting files recursively that a file was created and modified")
    @DisabledOnOs(value = { OS.MAC },
            disabledReason = "Issues with File Watching Service on macOS. We need a cross-platform Java recursive directory watcher, that works well with macOS")
    void testHandleExistingRecursivly(Vertx vertx, VertxTestContext testCtx) throws InterruptedException {
        WatchVerticle watchVerticleSpy = spy(new WatchVerticle(watchDir, 10, TimeUnit.MINUTES, false, true));
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

    private static Future<Void> verifyFileEvent(Vertx vertx, VertxTestContext testCtx, WatchVerticle watchVerticleSpy,
            ExecutionBlock checks) {
        // on windows or some CI platforms, same as deletion handles, all modification handles can take a bit of time
        // any may not be reported to the file watcher verticle, immediately after the write / delete
        // operation finishes, thus add a small delay to account for this
        return waitFor(vertx, 100).compose(nothing -> watchVerticleSpy.checkForChanges())
                .onComplete(s -> testCtx.verify(checks));
    }

    private static void verifyCreateModifyFile(WatchVerticle watchVerticleSpy, Path watchedFile) {
        verify(watchVerticleSpy).observedCreate(eq(watchedFile));
        verify(watchVerticleSpy).observedModify(eq(watchedFile));
    }

    private static byte[] toByte(String string) {
        return string.getBytes(UTF_8);
    }
}
