package io.neonbee.internal.handler;

import java.util.List;

import com.google.common.annotations.VisibleForTesting;

import io.neonbee.config.AuthHandlerConfig;
import io.vertx.core.Vertx;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.AuthenticationHandler;

public interface ChainAuthHandler extends AuthenticationHandler {
    /**
     * A no operation authentication handler.
     */
    @VisibleForTesting
    ChainAuthHandler NOOP_AUTHENTICATION_HANDLER = RoutingContext::next;

    /**
     * Creates an AuthChainHandler instance.
     *
     * @param vertx           the Vert.x instance to create the configured auth handlers
     * @param authChainConfig the auth handler configuration to be used
     * @return an AuthChainHandler based on the passed configuration. If the passed <i>authChainConfig</i> is null or
     *         empty a {@link #NOOP_AUTHENTICATION_HANDLER} will be returned.
     */
    static ChainAuthHandler create(Vertx vertx, List<AuthHandlerConfig> authChainConfig) {
        if (authChainConfig == null || authChainConfig.isEmpty()) {
            return NOOP_AUTHENTICATION_HANDLER;
        }

        io.vertx.ext.web.handler.ChainAuthHandler chainAuthHandler = io.vertx.ext.web.handler.ChainAuthHandler.any();
        List<AuthenticationHandler> authHandlers =
                authChainConfig.stream().map(config -> config.createAuthHandler(vertx)).toList();
        authHandlers.forEach(chainAuthHandler::add);

        return new ChainAuthHandler() {
            @Override
            public void handle(RoutingContext event) {
                chainAuthHandler.handle(event);
            }
        };
    }
}
