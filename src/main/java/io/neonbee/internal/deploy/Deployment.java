package io.neonbee.internal.deploy;

import com.google.common.annotations.VisibleForTesting;

import io.neonbee.logging.LoggingFacade;
import io.vertx.core.Future;
import io.vertx.core.Vertx;

public abstract class Deployment {
    private static final LoggingFacade LOGGER = LoggingFacade.create();

    @VisibleForTesting
    final String correlationId;

    private final Vertx vertx;

    private final String identifier;

    Deployment(Vertx vertx, String identifier, String correlationId) {
        this.vertx = vertx;
        this.identifier = identifier;
        this.correlationId = correlationId;
    }

    /**
     * Returns the full qualified class name of the verticle to deploy.
     *
     * @return The identifier
     */
    public String getIdentifier() {
        return identifier;
    }

    /**
     * Returns the deploymentId assigned to this deployment by Vert.x during the deployment.
     *
     * @return the deploymentId
     */
    public abstract String getDeploymentId();

    /**
     * Undeploys the related deployment.
     *
     * @return a succeeded Future if undeploy succeeded, otherwise a failed one
     */
    public Future<Void> undeploy() {
        return Future.future(promise -> {
            LOGGER.correlateWith(correlationId).info("Start to undeploy: {}", identifier);
            vertx.undeploy(getDeploymentId(), asyncUndeploy -> {
                if (asyncUndeploy.failed()) {
                    LOGGER.correlateWith(correlationId).error("Undeployment of {} failed", identifier,
                            asyncUndeploy.cause());
                    promise.fail(asyncUndeploy.cause());
                } else {
                    LOGGER.correlateWith(correlationId).info("Undeployment of {} succeeded", identifier);
                    promise.complete();
                }
            });
        });
    }
}
