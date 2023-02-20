package io.neonbee.internal.handler;

import io.neonbee.NeonBee;
import io.vertx.core.MultiMap;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.PlatformHandler;

/**
 * This Vert.x handler sets the "X-Instance-Info" header in the HTTP response, if not already set. The value of this
 * header is set to a unique identifier for the NeonBee instance which is handling the request. This handler should be
 * used in a Vert.x router to set the instance info header for all outgoing HTTP responses.
 */
public class InstanceInfoHandler implements PlatformHandler {
    static final String X_INSTANCE_INFO_HEADER = "X-Instance-Info";

    @Override
    public void handle(RoutingContext routingContext) {
        routingContext.addHeadersEndHandler(nothing -> {
            MultiMap headers = routingContext.response().headers();
            if (headers.contains(X_INSTANCE_INFO_HEADER)) {
                return;
            }

            // Sets the NeonBee instance name as default
            String instanceName = NeonBee.get(routingContext.vertx()).getOptions().getInstanceName();
            if (instanceName != null && !instanceName.isBlank()) {
                headers.set(X_INSTANCE_INFO_HEADER, instanceName);
            }
        });
        routingContext.next();
    }
}
