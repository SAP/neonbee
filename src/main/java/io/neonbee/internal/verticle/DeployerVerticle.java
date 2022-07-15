package io.neonbee.internal.verticle;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import io.neonbee.NeonBee;
import io.neonbee.internal.deploy.DeployableModule;
import io.neonbee.internal.deploy.Deployment;
import io.neonbee.logging.LoggingFacade;
import io.vertx.core.Future;
import io.vertx.core.Promise;

public class DeployerVerticle extends WatchVerticle {
    private static final LoggingFacade LOGGER = LoggingFacade.create();

    private final Map<Path, Deployment> deployedModules = new ConcurrentHashMap<>();

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

        LOGGER.debug("in trigger deployment");

        DeployableModule.fromJar(vertx, affectedPath).compose(deployableModule -> {
            // The deploy method automatically cleans up in case of failure.
            return deployableModule.deploy(NeonBee.get(vertx)).compose(deployment -> {
                Deployment replacedModule = deployedModules.put(affectedPath, deployment);
                if (Objects.isNull(replacedModule)) {
                    return Future.succeededFuture();
                }
                return replacedModule.undeploy().onFailure(t -> {
                    LOGGER.error("Unexpected error occurred during undeploy of the replaced module", t);
                });
            }).onFailure(throwable -> {
                if (LOGGER.isErrorEnabled()) {
                    LOGGER.error("Unexpected error occurred during deployment of module from JAR file: {}",
                            affectedPath.toAbsolutePath(), throwable);
                }
            });
        }).compose(dummy -> NeonBee.get(vertx).getModelManager().reloadModels().map((Void) null))
                .onComplete(asyncResult -> {
                    LOGGER.debug("Models have been refreshed.");
                    finishPromise.handle(asyncResult);
                });
    }

    @Override
    public void observedDelete(Path affectedPath, Promise<Void> finishPromise) {
        if (!affectedPath.toFile().getName().endsWith(".jar")) {
            finishPromise.complete();
            return;
        }

        Deployment deployedModule = deployedModules.remove(affectedPath);
        if (deployedModule == null) {
            finishPromise.complete();
            return;
        }

        deployedModule.undeploy().onFailure(t -> {
            LOGGER.error("Unexpected error occurred during undeploy", t);
        }).onComplete(finishPromise);
    }
}
