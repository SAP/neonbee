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
    /**
     * Create a new {@link CorrelationIdHandlerFactory}.
     */
    public CorrelationIdHandlerFactory() {
        // no initialization needed, however:
        // checkstyle suggests to create an empty constructor to explain the use of the class and
        // sonarcube is complaining if the constructor is empty and suggests to add (this) comment ;)
    }

    @Override
    public Future<Handler<RoutingContext>> createHandler() {
        ServerConfig serverConfig = NeonBee.get().getServerConfig();
        return succeededFuture(new CorrelationIdHandler(serverConfig.getCorrelationStrategy()));
    }
}
