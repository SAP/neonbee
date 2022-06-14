package io.neonbee.internal.handler.factories;

import java.util.Optional;

import com.google.common.annotations.VisibleForTesting;

import io.neonbee.NeonBee;
import io.neonbee.config.ServerConfig;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.PlatformHandler;
import io.vertx.ext.web.handler.SessionHandler;
import io.vertx.ext.web.sstore.ClusteredSessionStore;
import io.vertx.ext.web.sstore.LocalSessionStore;
import io.vertx.ext.web.sstore.SessionStore;

/**
 * Creates a SessionHandler.
 *
 * If ServerConfig.SessionHandling is set to NONE a no-operation handler is returned, which does not perform any session
 * handling.
 */
public class SessionHandlerFactory implements RoutingHandlerFactory {

    @VisibleForTesting
    static final class NoOpHandler implements PlatformHandler {
        @Override
        public void handle(RoutingContext routingContext) {
            routingContext.next();
        }
    }

    @Override
    public Future<Handler<RoutingContext>> createHandler() {
        ServerConfig config = NeonBee.get().getServerConfig();
        Handler<RoutingContext> sh =
                createSessionStore(NeonBee.get().getVertx(), config.getSessionHandling()).map(SessionHandler::create)
                        .map(sessionHandler -> sessionHandler.setSessionCookieName(config.getSessionCookieName()))
                        .map(sessionHandler -> (Handler<RoutingContext>) sessionHandler).orElseGet(NoOpHandler::new);
        return Future.succeededFuture(sh);
    }

    /**
     * Creates a {@linkplain SessionStore} based on the given {@linkplain ServerConfig} to use either local or clustered
     * session handling. If no session handling should be used, an empty optional is returned.
     *
     * @param vertx           the Vert.x instance to create the {@linkplain SessionStore} for
     * @param sessionHandling the session handling type
     * @return an optional session store, suitable for the given Vert.x instance and based on the provided config value
     *         (none/local/clustered). In case the session handling is set to clustered, but Vert.x does not run in
     *         clustered mode, fallback to the local session handling.
     */
    @VisibleForTesting
    static Optional<SessionStore> createSessionStore(Vertx vertx, ServerConfig.SessionHandling sessionHandling) {
        switch (sessionHandling) {
        case LOCAL:
            return Optional.of(LocalSessionStore.create(vertx));
        case CLUSTERED:
            if (!vertx.isClustered()) { // Behaves like clustered in case that instance isn't clustered
                return Optional.of(LocalSessionStore.create(vertx));
            }
            return Optional.of(ClusteredSessionStore.create(vertx));
        default: /* nothing to do here, no session handling, so neither add a cookie, nor a session handler */
            return Optional.empty();
        }
    }
}
