package io.neonbee.internal.handler;

import io.neonbee.NeonBee;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.ext.web.RoutingContext;

public class InstanceInfoHandler implements Handler<RoutingContext> {
    static final String X_INSTANCE_INFO_HEADER = "X-Instance-Info";

    /**
     * Convenience method as similar other Vertx handler implementations (e.g. ErrorHandler).
     *
     * @return The InstanceInformationHandler
     */
    public static InstanceInfoHandler create() {
        return new InstanceInfoHandler();
    }

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
