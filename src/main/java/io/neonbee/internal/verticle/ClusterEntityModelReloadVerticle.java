package io.neonbee.internal.verticle;

import java.util.concurrent.TimeUnit;

import io.neonbee.NeonBee;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;

public class ClusterEntityModelReloadVerticle extends AbstractVerticle {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClusterEntityModelReloadVerticle.class);

    public static final Long REMOTE_MODEL_RELOAD_DELAY = TimeUnit.MINUTES.toMillis(2);

    @Override
    public void start() {
        long reloadPeriod = config().getLong("REMOTE_MODEL_RELOAD_DELAY", REMOTE_MODEL_RELOAD_DELAY);
        vertx.setPeriodic(reloadPeriod, t -> {
            NeonBee.get(vertx).getModelManager().reloadRemoteModels(getVertx())
                    .onSuccess(map -> LOGGER.info("obtainedChanges: " + map));
        });
    }
}
