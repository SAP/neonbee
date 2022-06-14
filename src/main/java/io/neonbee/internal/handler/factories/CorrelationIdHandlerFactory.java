package io.neonbee.internal.handler.factories;

import static io.vertx.core.Future.succeededFuture;

import io.neonbee.NeonBee;
import io.neonbee.config.ServerConfig;
import io.neonbee.internal.handler.CorrelationIdHandler;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

/**
 * Create the {@link CorrelationIdHandler}.
 */
public class CorrelationIdHandlerFactory implements RoutingHandlerFactory {

    @Override
    public Future<Handler<RoutingContext>> createHandler() {
        ServerConfig serverConfig = NeonBee.get().getServerConfig();
        return succeededFuture(new CorrelationIdHandler(serverConfig.getCorrelationStrategy()));
    }
}
