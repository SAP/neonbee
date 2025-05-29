package io.neonbee.health;

import java.util.function.Function;

import com.retailsvc.vertx.spi.cluster.redis.ClusterHealthCheck;

import io.neonbee.NeonBee;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.ext.healthchecks.Status;

public class RedisClusterHealthCheck extends AbstractHealthCheck {

    /**
     * Name of the health check.
     */
    public static final String NAME = "cluster.redis";

    /**
     * Constructs an instance of {@link AbstractHealthCheck}.
     *
     * @param neonBee the current NeonBee instance
     */
    public RedisClusterHealthCheck(NeonBee neonBee) {
        super(neonBee);
    }

    @Override
    public Function<NeonBee, Handler<Promise<Status>>> createProcedure() {
        return neonBee -> ClusterHealthCheck.createProcedure(neonBee.getVertx());
    }

    @Override
    public String getId() {
        return NAME;
    }

    @Override
    public boolean isGlobal() {
        return true;
    }
}
