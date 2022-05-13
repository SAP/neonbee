package io.neonbee.internal.handler;

import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;

import java.util.Optional;

import io.neonbee.logging.LoggingFacade;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.PlatformHandler;

/**
 * Similar but simplified to the io.vertx.ext.web.handler.impl.LoggerHandlerImpl, w/ minor adaptions for logging the
 * correlationId.
 */
public class LoggerHandler implements PlatformHandler {
    /**
     * The facaded logger to use to log the events.
     */
    private static final LoggingFacade LOGGER = LoggingFacade.create();

    @Override
    public void handle(RoutingContext routingContext) {
        routingContext.addBodyEndHandler(nothing -> log(routingContext, System.currentTimeMillis()));
        routingContext.next();
    }

    private void log(RoutingContext routingContext, long timestamp) {
        HttpServerRequest request = routingContext.request();

        String version;
        switch (request.version()) {
        case HTTP_1_0:
            version = "HTTP/1.0";
            break;
        case HTTP_1_1:
            version = "HTTP/1.1";
            break;
        case HTTP_2:
            version = "HTTP/2.0";
            break;
        default:
            version = "-";
            break;
        }

        int statusCode = request.response().getStatusCode();
        String message = String.format("%s - %s %s %s %d %d - %d ms",
                Optional.ofNullable(request.remoteAddress()).map(SocketAddress::host).orElse(null), request.method(),
                request.uri(), version, statusCode, request.response().bytesWritten(),
                System.currentTimeMillis() - timestamp);

        LOGGER.correlateWith(routingContext);

        if (statusCode >= INTERNAL_SERVER_ERROR.code()) {
            LOGGER.error(message);
        } else if (statusCode >= BAD_REQUEST.code()) {
            LOGGER.warn(message);
        } else {
            LOGGER.info(message);
        }
    }
}
