package io.neonbee.internal.deploy;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;

public class PendingDeployment extends Deployment implements Promise<Deployment> {
    // It is necessary to throw an exception directly, otherwise code coverage calculation is not correct
    private static final IllegalStateException CANNOT_COMPLETE =
            new IllegalStateException("Cannot complete pending deployments.");

    private static final IllegalStateException CANNOT_FAIL =
            new IllegalStateException("Cannot fail pending deployments.");

    private final Future<String> deployFuture;

    PendingDeployment(Vertx vertx, String identifier, String correlationId, Future<String> deployFuture) {
        super(vertx, identifier, correlationId);
        this.deployFuture = deployFuture;
    }

    @Override
    public Future<Void> undeploy() {
        if (!deployFuture.isComplete()) {
            return failedFuture(
                    new IllegalStateException("Undeployment not possible, because deployment is still ongoing."));
        }
        return deployFuture.succeeded() ? super.undeploy() : succeededFuture();
    }

    @Override
    public String getDeploymentId() {
        return deployFuture.result();
    }

    @Override
    public void complete(Deployment result) {
        throw CANNOT_COMPLETE;
    }

    @Override
    public void complete() {
        throw CANNOT_COMPLETE;
    }

    @Override
    public boolean tryComplete(Deployment result) {
        throw CANNOT_COMPLETE;
    }

    @Override
    public boolean tryComplete() {
        throw CANNOT_COMPLETE;
    }

    @Override
    public void fail(Throwable cause) {
        throw CANNOT_FAIL;
    }

    @Override
    public void fail(String failureMessage) {
        throw CANNOT_FAIL;
    }

    @Override
    public boolean tryFail(Throwable cause) {
        throw CANNOT_FAIL;
    }

    @Override
    public boolean tryFail(String failureMessage) {
        throw CANNOT_FAIL;
    }

    @Override
    public void handle(AsyncResult<Deployment> asyncResult) {
        throw new IllegalStateException("Cannot handle pending deployments.");
    }

    @Override
    public Future<Deployment> future() {
        return deployFuture.map((Deployment) this);
    }
}
