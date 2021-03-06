package io.neonbee.internal.verticle;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import io.neonbee.internal.deploy.NeonBeeModule;
import io.neonbee.logging.LoggingFacade;
import io.vertx.core.Future;
import io.vertx.core.Promise;

public class DeployerVerticle extends WatchVerticle {
    private static final LoggingFacade LOGGER = LoggingFacade.create();

    private final Map<Path, NeonBeeModule> modules = new ConcurrentHashMap<>();

    /**
     * Creates a DeployerVerticle that watches for new NeonBeeModules on the given path.
     *
     * @param watchDir the directory to watch
     */
    public DeployerVerticle(Path watchDir) {
        this(watchDir, WatchVerticle.DEFAULT_CHECK_INTERVAL, TimeUnit.MILLISECONDS);
    }

    DeployerVerticle(Path watchDir, long interval, TimeUnit unit) {
        super(watchDir, interval, unit, false, true);
    }

    @Override
    public void observedCreate(Path affectedPath, Promise<Void> finishPromise) {
        if (!isCopyLogic(config())) {
            triggerDeployment(affectedPath, finishPromise);
        } else {
            finishPromise.complete();
        }
    }

    @Override
    public void observedModify(Path affectedPath, Promise<Void> finishPromise) {
        if (isCopyLogic(config())) {
            triggerDeployment(affectedPath, finishPromise);
        } else {
            finishPromise.complete();
        }
    }

    private void triggerDeployment(Path affectedPath, Promise<Void> finishPromise) {
        if (!affectedPath.toFile().getName().endsWith(".jar")) {
            finishPromise.complete();
            return;
        }
        String correlationId = UUID.randomUUID().toString();

        LOGGER.correlateWith(correlationId).info("Parse NeonBeeModule from JAR file: {}",
                affectedPath.toAbsolutePath());
        NeonBeeModule.fromJar(vertx, affectedPath, correlationId).recover(t -> {
            // Log errors from inside of method Deployable.fromJarFile
            LOGGER.correlateWith(correlationId).error("An error occurred while parsing jar file {}",
                    affectedPath.toAbsolutePath(), t);
            return Future.failedFuture(t);
        }).compose(neonBeeModule ->
        // The deploy method automatically cleans up in case of failure.
        neonBeeModule.deploy().compose(v -> {
            NeonBeeModule replacedModule = modules.put(affectedPath, neonBeeModule);
            if (Objects.isNull(replacedModule)) {
                return Future.succeededFuture();
            }
            return replacedModule.undeploy().recover(t -> {
                LOGGER.correlateWith(correlationId)
                        .error("Unexpected error occurred during undeploy of the replaced NeonBeeModule", t);
                return Future.failedFuture(t);
            });
        }).recover(t -> {
            LOGGER.correlateWith(correlationId).error(
                    "Unexpected error occurred during deployment of NeonBeeModule from JAR file: {}",
                    affectedPath.toAbsolutePath(), t);
            return Future.failedFuture(t);
        })).onComplete(finishPromise);
    }

    @Override
    public void observedDelete(Path affectedPath, Promise<Void> finishPromise) {
        if (!affectedPath.toFile().getName().endsWith(".jar")) {
            finishPromise.complete();
            return;
        }
        Optional.ofNullable(modules.remove(affectedPath))
                .ifPresentOrElse(moduleToUndeploy -> moduleToUndeploy.undeploy().recover(t -> {
                    LOGGER.correlateWith(moduleToUndeploy.getCorrelationId())
                            .error("Unexpected error occurred during undeploy", t);
                    return Future.failedFuture(t);
                }).onComplete(finishPromise), () -> finishPromise.complete());
    }
}
