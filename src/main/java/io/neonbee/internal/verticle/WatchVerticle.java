package io.neonbee.internal.verticle;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.google.common.annotations.VisibleForTesting;

import io.neonbee.NeonBee;
import io.neonbee.internal.helper.FileSystemHelper;
import io.neonbee.logging.LoggingFacade;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

public class WatchVerticle extends AbstractVerticle {
    /**
     * The default WatchVerticle check interval. The default time unit is milliseconds.
     */
    public static final int DEFAULT_CHECK_INTERVAL = 500;

    @VisibleForTesting
    static final String WATCH_LOGIC_KEY = "watchLogic";

    @VisibleForTesting
    static final String WATCH_LOGIC_OPTION_COPY = "copy";

    private static final LoggingFacade LOGGER = LoggingFacade.create();

    private static final long UNDEPLOY_DELAY = 50L;

    @VisibleForTesting
    final long watchPeriodMillis;

    final Map<Path, WatchKey> watchKeys = new HashMap<>();

    private final Path watchPath;

    private WatchService watcher;

    private final boolean parallelProcessing;

    private final boolean handleExisting;

    private final String counterName = UUID.randomUUID().toString();

    /**
     * A common problem using WatchVerticle is to detect if a watched resource was moved or copied, because if a file is
     * moved only the method {@link #observedCreate(Path, Promise)} is called. But if a file is copied the
     * {@link #observedCreate(Path, Promise)} AND the {@link #observedModify(Path, Promise)} methods are called.
     *
     * <p>
     * Due to the implementation of WatchVerticle and the underlying WatchService from the JDK it is not possible to get
     * all events for one file and then decide what to to.
     *
     * <p>
     * A workaround for this problem could be, that a concrete implementation of a WatchVerticle is only listening to
     * CREATE or MODIFY events. But both options have a down side:
     * <ol>
     * <li>Listen to CREATE only: This would have the benefit, that in case of a copied file only the CREATE is called.
     * But in case that a watched resource gets updated (e.g. content change) the concrete WatchVerticle would ignore
     * this.</li>
     * <li>Listen to MODIFY only: This would have the benefit, that the concrete WatchVerticle is recognizing the update
     * of a watched resource (e.g. content change). But in case a new file is moved into a watched directory the
     * concrete WatchVerticle wouldn't recognize it.</li>
     * </ol>
     *
     * <p>
     * Due to the fact that both options have a down side, it should be configurable how a concrete WatchVerticle should
     * behave. This method could be use to parse the configuration of a concrete WatchVerticle implementation and
     * returns the specified behavior. This functionality is not directly added to the WacthVerticle, because there are
     * so many complex and specific scenarios which must be handled separately. So every WatchVerticle implementation
     * can use this method, but must implement its own logic if it should support this option.
     *
     * <p>
     * If nothing is configured, the default response is false which is equivalent to CREATE only.
     *
     * @param config The verticle configuration object
     * @return True, if the passed configuration contains a key "watchLogic" with the value "copy", otherwise false.
     */
    public static boolean isCopyLogic(JsonObject config) {
        String value = Optional.ofNullable(config).orElse(new JsonObject()).getString(WATCH_LOGIC_KEY);
        return Optional.ofNullable(value).map(WATCH_LOGIC_OPTION_COPY::equalsIgnoreCase).orElse(false);
    }

    /**
     * The WatchVerticle is a wrapper for {@link WatchService} and allows to react on the following events:
     * <ul>
     * <li>ENTRY_CREATE</li>
     * <li>ENTRY_DELETE</li>
     * <li>ENTRY_MODIFY</li>
     * </ul>
     *
     * Due to the limitations of {@link WatchService} it is not possible to monitor changes in subdirectories of the
     * passed <b>watchDir</b>.
     *
     * By default the WatchVerticle checks every 500 milliseconds for changes in the watched directory.
     *
     * @param watchPath The {@link Path} to monitor
     */
    public WatchVerticle(Path watchPath) {
        this(watchPath, DEFAULT_CHECK_INTERVAL, TimeUnit.MILLISECONDS, true, true);
    }

    /**
     * The WatchVerticle is a wrapper for {@link WatchService} and allows to react on the following events:
     * <ul>
     * <li>ENTRY_CREATE</li>
     * <li>ENTRY_DELETE</li>
     * <li>ENTRY_MODIFY</li>
     * </ul>
     * The WatchVerticle is capable to monitor changes in subdirectories of the passed <b>watchDir</b>.
     *
     * @param watchPath          The {@link Path} to monitor
     * @param interval           The interval to check for changes
     * @param unit               The unit of the check interval
     * @param parallelProcessing If false, ignores upcoming intervals if processing of a predecessor interval is still
     *                           in progress. Default is true.
     * @param handleExisting     If true, trigger an ENTRY_CREATE and ENTRY_MODIFY event, for every element in the
     *                           watchPath. Default is true.
     */
    public WatchVerticle(Path watchPath, long interval, TimeUnit unit, boolean parallelProcessing,
            boolean handleExisting) {
        super();
        this.watchPeriodMillis = unit.toMillis(interval);
        this.watchPath = watchPath.toAbsolutePath();
        this.parallelProcessing = parallelProcessing;
        this.handleExisting = handleExisting;
    }

    @Override
    public void start(Promise<Void> startPromise) {
        Vertx vertx = getVertx();
        if (NeonBee.get(vertx).getOptions().doNotWatchFiles()) {
            // edge case: signal completion to Vert.x before undeploying ourself after a small delay
            startPromise.complete();
            vertx.setTimer(UNDEPLOY_DELAY, timerId -> {
                vertx.undeploy(deploymentID()).onFailure(throwable -> {
                    LOGGER.error("Failed to undeploy watch verticle", throwable);
                });
            });
            return;
        }

        try {
            watcher = watchPath.getFileSystem().newWatchService();
        } catch (IOException e) {
            startPromise.fail(e);
            return;
        }

        (handleExisting ? handleExistingFiles(watchPath) : registerWatchKey(watchPath))
                .compose(nothing -> vertx.sharedData().getLocalCounter(counterName)).onSuccess(counter -> {
                    vertx.setPeriodic(watchPeriodMillis, l -> {
                        if (parallelProcessing) {
                            checkForChanges();
                        } else {
                            counter.compareAndSet(0, 1).onSuccess(result -> {
                                if (result) {
                                    checkForChanges().onComplete(nothing -> counter.compareAndSet(1, 0));
                                }
                            });
                        }
                    });
                }).<Void>mapEmpty().onComplete(startPromise);
    }

    @Override
    public void stop(Promise<Void> stopPromise) throws Exception {
        if (watcher != null) {
            try {
                watcher.close();
            } catch (IOException e) {
                stopPromise.fail(e);
                return;
            }
        }

        stopPromise.complete();
    }

    /**
     * Process all existing files to be monitored. After the registration of the WatchKey, all elements in the passed
     * directory will be mapped into a list of {@link Path}'s. Then these paths are passed into
     * {@link #handleFileEvents(List, List)} which returns a {@link Future} which is resolved when all elements in the
     * passed directory are processed. For each element which gets processed by {@link #handleFileEvents(List, List)}, a
     * Future, which is resolved when the related action is finished, is added to the List futuresToResolve.
     *
     * @param dir The {@link Path} to monitor
     */
    private Future<Void> handleExistingFiles(Path dir) {
        List<Future<Void>> futuresToResolve = new ArrayList<>();
        return registerWatchKey(dir).compose(v -> FileSystemHelper.readDir(vertx, dir))
                .compose(dirContent -> handleFileEvents(dirContent, futuresToResolve))
                .compose(fileFutures -> Future.all(fileFutures)
                        .compose(compFut -> Future.all(futuresToResolve)).mapEmpty());
    }

    /**
     * This method iterates over the passed elements of a directory and creates for each of those elements a WatchEvent
     * of type ENTRY_CREATE and ENTRY_MODIFY. In case that the current element is a directory, the path of this
     * directory is passed to handleExistingFiles, to ensure a recursively behavior.
     *
     * @param dirContent       List of file {@link Path}s to be triggered
     * @param futuresToResolve List of from the triggered processEvent()
     */
    private Future<List<Future<Void>>> handleFileEvents(List<Path> dirContent, List<Future<Void>> futuresToResolve) {
        return Future.succeededFuture(
                dirContent.stream().map(path -> FileSystemHelper.isDirectory(vertx, path).compose(isDirectory -> {
                    futuresToResolve.add(processEvent(path, ENTRY_CREATE));
                    futuresToResolve.add(processEvent(path, ENTRY_MODIFY));

                    if (isDirectory) {
                        futuresToResolve.add(handleExistingFiles(path));
                    }
                    return Future.succeededFuture((Void) null);
                })).toList());
    }

    private Future<Void> handleWatchKeyEvents(Path watchKeyPath, WatchKey watchKey) {
        List<WatchEvent<?>> events = watchKey.pollEvents();
        List<Future<Void>> watchEventFutures = new ArrayList<>(events.size());

        for (WatchEvent<?> event : events) {
            Path affectedPath = watchKeyPath.resolve(event.context().toString());
            watchEventFutures.add(processEvent(affectedPath, event.kind()));
        }

        return Future.join(watchEventFutures).onComplete(asyncCompFuture -> {
            watchKeys.get(watchPath).reset();
        }).mapEmpty();
    }

    @VisibleForTesting
    Future<Void> checkForChanges() {
        // To prevent a ConcurrentModificationException while iterating over values of map watchKeys a temporary list is
        // created.
        Map<Path, WatchKey> tempWatchKeys = Map.copyOf(watchKeys);
        List<Future<Void>> watchKeyFutures = new ArrayList<>(tempWatchKeys.size());
        for (Map.Entry<Path, WatchKey> entry : tempWatchKeys.entrySet()) {
            watchKeyFutures.add(handleWatchKeyEvents(entry.getKey(), entry.getValue()));
        }

        return Future.join(watchKeyFutures).mapEmpty();
    }

    private Future<Void> registerWatchKey(Path affectedPath) {
        try {
            watchKeys.put(affectedPath, affectedPath.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY));
            return Future.succeededFuture();
        } catch (IOException e) {
            return Future.failedFuture(e);
        }
    }

    private Future<Void> processEvent(Path affectedPath, Kind<?> kind) {
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Observed WatchEvent of kind '{}' for Path '{}'", kind.name(), affectedPath);
        }

        Promise<Void> promise = Promise.promise();
        if (ENTRY_CREATE.equals(kind)) {
            FileSystemHelper.isDirectory(vertx, affectedPath).compose(isDirectory -> {
                if (isDirectory) {
                    return registerWatchKey(affectedPath);
                } else {
                    return Future.succeededFuture();
                }
            }).onComplete(asyncRegisterResult -> {
                if (asyncRegisterResult.failed()) {
                    promise.fail(asyncRegisterResult.cause());
                } else {
                    observedCreate(affectedPath, promise);
                }
            });
        } else if (ENTRY_DELETE.equals(kind)) {
            Optional.ofNullable(watchKeys.remove(affectedPath)).ifPresent(WatchKey::cancel);
            observedDelete(affectedPath, promise);
        } else if (ENTRY_MODIFY.equals(kind)) {
            observedModify(affectedPath, promise);
        } else if (OVERFLOW.equals(kind)) {
            promise.complete();
        } else {
            LOGGER.warn("Unknown WatchEvent kind '{}' for Path '{}'", kind, affectedPath);
            promise.complete();
        }
        return promise.future();
    }

    /**
     * This method is called, if a file or folder was created in the monitored directory. Don't execute long running
     * code directly in this method, otherwise it will block the execution of other events.
     *
     * @param affectedPath The {@link Path} of the created file or directory
     */
    @SuppressWarnings("PMD.UnusedFormalParameter")
    public void observedCreate(Path affectedPath) {
        // do nothing
    }

    /**
     * This method is called, if a file or folder was created in the monitored directory. Don't execute long running
     * code directly in this method, otherwise it will block the execution of other events.
     *
     * @param affectedPath  The {@link Path} of the created file or directory
     * @param finishPromise The future to complete, if the operation related to the affectedPath is done
     */
    public void observedCreate(Path affectedPath, Promise<Void> finishPromise) {
        observedCreate(affectedPath);
        finishPromise.complete();
    }

    /**
     * This method is called, if file or folder was deleted in the monitored directory. Don't execute long running code
     * directly in this method, otherwise it will block the execution of other events.
     *
     * @param affectedPath The {@link Path} of the of the deleted file or directory
     */
    @SuppressWarnings("PMD.UnusedFormalParameter")
    public void observedDelete(Path affectedPath) {
        // do nothing
    }

    /**
     * This method is called, if file or folder was deleted in the monitored directory. Don't execute long running code
     * directly in this method, otherwise it will block the execution of other events.
     *
     * @param affectedPath  The {@link Path} of the of the deleted file or directory
     * @param finishPromise The future to complete, if the operation related to the affectedPath is done
     */
    public void observedDelete(Path affectedPath, Promise<Void> finishPromise) {
        observedDelete(affectedPath);
        finishPromise.complete();
    }

    /**
     * This method is called, if a new file or folder was modified in the monitored directory. Don't execute long
     * running code directly in this method, otherwise it will block the execution of other events.
     *
     * @param affectedPath The {@link Path} of the of the modified file or directory
     */
    @SuppressWarnings("PMD.UnusedFormalParameter")
    public void observedModify(Path affectedPath) {
        // do nothing
    }

    /**
     * This method is called, if a file or folder was modified in the monitored directory. Don't execute long running
     * code directly in this method, otherwise it will block the execution of other events.
     *
     * @param affectedPath  The {@link Path} of the of the modified file or directory
     * @param finishPromise The future to complete, if the operation related to the affectedPath is done
     */
    public void observedModify(Path affectedPath, Promise<Void> finishPromise) {
        observedModify(affectedPath);
        finishPromise.complete();
    }
}
