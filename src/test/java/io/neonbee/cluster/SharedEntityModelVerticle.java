package io.neonbee.cluster;

import static io.neonbee.cluster.NeonbeeClusterRunner.STARTING_PORT;

import java.util.Map;

import io.neonbee.NeonBee;
import io.neonbee.entity.EntityModel;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;

/**
 * verticle that can be used to do something special on one of the instances of neonbee cluster. As for now no
 * functionality implemented here.
 */
public class SharedEntityModelVerticle extends AbstractVerticle {

    private static final Logger LOGGER = LoggerFactory.getLogger(SharedEntityModelVerticle.class);

    @Override
    public void start() {
        LOGGER.info("starting verticle on the instance with port " + NeonBee.get().getOptions().getServerPort());
        if (isFirstInstance()) {
            vertx.setTimer(1000, id -> {
                Map<String, EntityModel> models = NeonBee.get(vertx).getModelManager().getBufferedModels();
                LOGGER.info("buffered models: " + models);
            });
        } else {
            vertx.setTimer(4 * 60000, id -> {
                LOGGER.info("from not first instance");
            });
        }

    }

    boolean isFirstInstance() {
        Integer port = NeonBee.get().getOptions().getServerPort();
        return STARTING_PORT == port || port > STARTING_PORT + 10;
    }

}
