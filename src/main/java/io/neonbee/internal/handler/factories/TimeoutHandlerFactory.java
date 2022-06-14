package io.neonbee.internal.handler.factories;

import static io.vertx.core.Future.succeededFuture;

import java.util.concurrent.TimeUnit;

import io.neonbee.NeonBee;
import io.neonbee.config.ServerConfig;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.TimeoutHandler;

/**
 * Create the {@link TimeoutHandler}.
 */
public class TimeoutHandlerFactory implements RoutingHandlerFactory {

    @Override
    public Future<Handler<RoutingContext>> createHandler() {
        ServerConfig serverConfig = NeonBee.get().getServerConfig();
        return succeededFuture(TimeoutHandler.create(TimeUnit.SECONDS.toMillis(serverConfig.getTimeout()),
                serverConfig.getTimeoutStatusCode()));
    }
}
