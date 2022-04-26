package io.neonbee.internal.handler;

import java.util.List;
import java.util.stream.Collectors;

import io.neonbee.config.AuthHandlerConfig;
import io.vertx.core.Vertx;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.AuthenticationHandler;
import io.vertx.ext.web.handler.ChainAuthHandler;

public interface AuthChainHandler extends AuthenticationHandler {
    /**
     * A no operation authentication handler.
     */
    AuthChainHandler NOOP_AUTHENTICATION_HANDLER = RoutingContext::next;

    /**
     * Creates an AuthChainHandler instance.
     *
     * @param vertx           the Vert.x instance to create the configured auth handlers
     * @param authChainConfig the auth handler configuration to be used
     * @return an AuthChainHandler based on the passed configuration. If the passed <i>authChainConfig</i> is null or
     *         empty a {@link #NOOP_AUTHENTICATION_HANDLER} will be returned.
     */
    static AuthChainHandler create(Vertx vertx, List<AuthHandlerConfig> authChainConfig) {
        if (authChainConfig == null || authChainConfig.isEmpty()) {
            return NOOP_AUTHENTICATION_HANDLER;
        }

        ChainAuthHandler authChainHandler = ChainAuthHandler.any();
        List<AuthenticationHandler> authHandlers =
                authChainConfig.stream().map(config -> config.createAuthHandler(vertx)).collect(Collectors.toList());
        authHandlers.forEach(authChainHandler::add);

        return (AuthChainHandler) authChainHandler;
    }
}
