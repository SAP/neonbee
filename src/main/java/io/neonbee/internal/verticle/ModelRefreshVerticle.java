package io.neonbee.internal.verticle;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import io.neonbee.NeonBee;
import io.neonbee.entity.EntityModelManager;
import io.neonbee.logging.LoggingFacade;
import io.vertx.core.Promise;

public class ModelRefreshVerticle extends WatchVerticle {
    private static final LoggingFacade LOGGER = LoggingFacade.create();

    private static final int DEFAULT_CHECK_INTERVAL = 5;

    /**
     * Creates a ModelRefreshVerticle that watches for new models on the given path.
     *
     * @param modelsDirPath the directory to watch
     */
    public ModelRefreshVerticle(Path modelsDirPath) {
        super(modelsDirPath, DEFAULT_CHECK_INTERVAL, TimeUnit.SECONDS, false, false);
    }

    ModelRefreshVerticle(Path modelsDirPath, long interval, TimeUnit unit) {
        super(modelsDirPath, interval, unit, false, false);
    }

    @Override
    public void observedCreate(Path affectedPath, Promise<Void> finishPromise) {
        if (!isCopyLogic(config())) {
            triggerRefresh(finishPromise);
        } else {
            finishPromise.complete();
        }
    }

    @Override
    public void observedModify(Path affectedPath, Promise<Void> finishPromise) {
        if (isCopyLogic(config())) {
            triggerRefresh(finishPromise);
        } else {
            finishPromise.complete();
        }
    }

    @Override
    public void observedDelete(Path affectedPath, Promise<Void> finishPromise) {
        triggerRefresh(finishPromise);
    }

    private void triggerRefresh(Promise<Void> finishPromise) {
        EntityModelManager.reloadModels(NeonBee.get(vertx)).map((Void) null).onComplete(asyncResult -> {
            LOGGER.debug("Models have been refreshed.");
            finishPromise.handle(asyncResult);
        });
    }
}
