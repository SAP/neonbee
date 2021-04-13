package io.neonbee.internal.handler;

import static io.neonbee.config.ServerConfig.CorrelationStrategy.GENERATE_UUID;

import io.neonbee.config.ServerConfig.CorrelationStrategy;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

public class CorrelationIdHandler implements Handler<RoutingContext> {
    /**
     * The key for the correlation id stored in the RoutingContext.
     */
    public static final String CORRELATION_ID = "correlationId";

    private final CorrelationStrategy strategy;

    /**
     * Creates a CorrelationIdHandler with the default correlation strategy (header values).
     */
    public CorrelationIdHandler() {
        this(GENERATE_UUID);
    }

    /**
     * Creates a new CorrelationIdHandler with the given strategy.
     *
     * @param strategy The CorrelationStrategy
     */
    public CorrelationIdHandler(CorrelationStrategy strategy) {
        this.strategy = strategy;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        routingContext.put(CORRELATION_ID, strategy.getCorrelationId(routingContext));
        routingContext.next();
    }

    /**
     * Convenience method for retrieving the correlation id from a RoutingContext if present.
     *
     * @param routingContext The RoutingContext to retrieve the correlation id from
     * @return The correlationId of the request or null
     */
    public static String getCorrelationId(RoutingContext routingContext) {
        return routingContext.get(CORRELATION_ID);
    }
}
