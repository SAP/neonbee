package io.neonbee.internal.handler.factories;

import static io.vertx.core.Future.succeededFuture;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.annotations.VisibleForTesting;

import io.neonbee.NeonBee;
import io.neonbee.config.CorsConfig;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.ext.web.handler.SecurityPolicyHandler;
import io.vertx.ext.web.handler.TimeoutHandler;

/**
 * Create the {@link TimeoutHandler}.
 */
public class CorsHandlerFactory implements RoutingHandlerFactory {
    @VisibleForTesting
    static final class NoOpHandler implements SecurityPolicyHandler {
        @Override
        public void handle(RoutingContext routingContext) {
            routingContext.next();
        }
    }

    /**
     * Create a new {@link CorsHandlerFactory}.
     */
    public CorsHandlerFactory() {
        // no initialization needed, however:
        // checkstyle suggests to create an empty constructor to explain the use of the class and
        // sonarcube is complaining if the constructor is empty and suggests to add (this) comment ;)
    }

    @Override
    public Future<Handler<RoutingContext>> createHandler() {
        CorsConfig corsConfig = NeonBee.get().getServerConfig().getCorsConfig();
        if (corsConfig.isEnabled()) {
            CorsHandler corsHandler = CorsHandler.create();
            if (corsConfig.getOrigins() != null) {
                corsHandler.addOrigins(corsConfig.getOrigins());
            }
            if (corsConfig.getRelativeOrigins() != null) {
                corsHandler.addRelativeOrigins(corsConfig.getRelativeOrigins());
            }
            if (corsConfig.getAllowedHeaders() != null) {
                corsHandler.allowedHeaders(corsConfig.getAllowedHeaders());
            }
            if (corsConfig.getAllowedMethods() != null) {
                Set<HttpMethod> allowedMethods = corsConfig.getAllowedMethods().stream()
                        .map(s -> HttpMethod.valueOf(s.toUpperCase(Locale.ROOT))).collect(Collectors.toSet());
                corsHandler.allowedMethods(allowedMethods);
            }
            if (corsConfig.getExposedHeaders() != null) {
                corsHandler.exposedHeaders(corsConfig.getExposedHeaders());
            }
            corsHandler.maxAgeSeconds(corsConfig.getMaxAgeSeconds());
            corsHandler.allowCredentials(corsConfig.getAllowCredentials());
            return succeededFuture(corsHandler);
        } else {
            return succeededFuture(new NoOpHandler());
        }
    }
}
