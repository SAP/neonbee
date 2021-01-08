package io.neonbee.internal.handler;

import static io.neonbee.internal.handler.CorrelationIdHandler.Strategy.GENERATE_UUID;

import java.util.UUID;
import java.util.function.Function;

import com.google.common.base.Strings;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;

public class CorrelationIdHandler implements Handler<RoutingContext> {
    /**
     * The key for the correlation id stored in the RoutingContext.
     */
    public static final String CORRELATION_ID = "correlationId";

    private Strategy strategy;

    /**
     * Convenience method as similar other Vertx handler implementations (e.g. ErrorHandler)
     *
     * Creates a CorrelationIdHandler with the default correlation strategy (header values)
     *
     * @return A CorrelationIdHandler instance
     */
    public static CorrelationIdHandler create() {
        return create(GENERATE_UUID);
    }

    /**
     * Convenience method as similar other Vertx handler implementations (e.g. ErrorHandler)
     *
     * Creates a CorrelationIdHandler with a given correlation strategy
     *
     * @param strategy The correlation {@link Strategy}
     * @return A CorrelationIdHandler instance
     */
    public static CorrelationIdHandler create(Strategy strategy) {
        return new CorrelationIdHandler(strategy);
    }

    /**
     * Creates a new CorrelationIdHandler with the given strategy.
     *
     * @param strategy The strategy
     */
    public CorrelationIdHandler(Strategy strategy) {
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

    public enum Strategy {
        /**
         * Generates a random UUID for every incoming request.
         */
        GENERATE_UUID(routingContext -> UUID.randomUUID().toString()),
        /**
         * Generates a random UUID for every incoming request.
         */
        REQUEST_HEADER(routingContext -> {
            HttpServerRequest request = routingContext.request();
            String correlationId = request.getHeader("X-CorrelationID");
            if (Strings.isNullOrEmpty(correlationId)) {
                correlationId = request.getHeader("x-vcap-request-id"); // used on CloudFoundry
            }
            if (Strings.isNullOrEmpty(correlationId)) {
                correlationId = GENERATE_UUID.getCorrelationId(routingContext);
            }
            return correlationId;
        });

        @SuppressWarnings("ImmutableEnumChecker")
        final Function<RoutingContext, String> mapping;

        Strategy(Function<RoutingContext, String> mapping) {
            this.mapping = mapping;
        }

        String getCorrelationId(RoutingContext routingContext) {
            return mapping.apply(routingContext);
        }
    }
}
