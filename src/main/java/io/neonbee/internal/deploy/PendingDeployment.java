package io.neonbee.internal.deploy;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;

import java.util.concurrent.TimeUnit;

import io.neonbee.NeonBee;
import io.neonbee.logging.LoggingFacade;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;

public abstract class PendingDeployment extends Deployment {
    private static final LoggingFacade LOGGER = LoggingFacade.create();

    private final Future<String> deployFuture;

    PendingDeployment(NeonBee neonBee, Deployable deployable, Future<String> deployFuture) {
        super(neonBee, deployable);

        LOGGER.info("Started deployment of {} ...", deployable);

        Future<String> timeoutFuture = deployFuture;
        int timeout = neonBee.getConfig().getDeploymentTimeout(deployable.getType());
        if (timeout > 0) {
            Vertx vertx = neonBee.getVertx();
            Promise<String> timeoutPromise = Promise.promise();
            // fail the promise after the timeout is expired
            long timerId = vertx.setTimer(TimeUnit.SECONDS.toMillis(timeout), nothing -> {
                timeoutPromise.fail("Deployment timed-out after " + timeout + " seconds");
            });
            // in case the deployment finished, it completes the promise and we can cancel the timer
            deployFuture.onComplete(timeoutPromise).onComplete(deploymentId -> {
                vertx.cancelTimer(timerId);
            });
            timeoutFuture = timeoutPromise.future();
        }

        this.deployFuture = timeoutFuture.map(deploymentId -> {
            // in case a deployment doesn't want to specify a own deployment ID, generate one based on the hash code of
            // the pending deployment (thus all deployables, might just return an empty future)
            return deploymentId != null ? deploymentId : super.getDeploymentId();
        }).onSuccess(deploymentId -> {
            LOGGER.info("Deployment of {} succeeded with ID {}", deployable, deploymentId);
        }).onFailure(throwable -> {
            LOGGER.error("Deployment of {} failed", deployable, throwable);
        });
    }

    @Override
    public final Future<Void> undeploy() {
        Deployable deployable = getDeployable();
        if (!deployFuture.isComplete()) {
            return failedFuture(new IllegalStateException(
                    "Cannot undeploy " + deployable.getIdentifier() + ", because deployment is still ongoing."));
        } else if (deployFuture.failed() && !Deployables.class.isInstance(deployable)) {
            // if the deployable wasn't successfully deployed, we do not need to undeploy it
            return succeededFuture();
        }

        String deployableIdentifier = deployable.getIdentifier();
        String deploymentId = deployFuture.result();
        LOGGER.info("Starting to undeploy {} with ID {}", deployableIdentifier, deploymentId);
        return undeploy(deploymentId).onSuccess(nothing -> {
            LOGGER.info("Undeployment of {} with ID {} succeeded", deployableIdentifier, deploymentId);
        }).onFailure(throwable -> {
            LOGGER.error("Undeployment of {} with ID {} failed", deployableIdentifier, deploymentId, throwable);
        });
    }

    /**
     * Undeploy a deployment with a given deployment ID.
     *
     * @see Deployment#undeploy()
     * @param deploymentId the deployment ID of the deployment to undeploy
     * @return a future to signal when the undeployment was completed
     */
    protected abstract Future<Void> undeploy(String deploymentId);

    /**
     * Returns the Deployment object after deployment is completed.
     *
     * @return future containing deployment object.
     */
    public Future<Deployment> getDeployment() {
        return deployFuture.map(id -> this);
    }
}
