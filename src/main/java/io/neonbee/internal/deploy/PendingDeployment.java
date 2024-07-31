package io.neonbee.internal.deploy;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;

import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

import io.neonbee.NeonBee;
import io.neonbee.logging.LoggingFacade;
import io.vertx.core.AsyncResult;
import io.vertx.core.Expectation;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.future.Expect;
import io.vertx.core.impl.future.FutureInternal;
import io.vertx.core.impl.future.Listener;

public abstract class PendingDeployment extends Deployment implements FutureInternal<Deployment> {
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
    public Future<Deployment> expecting(Expectation<? super Deployment> expectation) {
        Expect<Deployment> expect = new Expect(context(), expectation);
        this.addListener(expect);
        return expect;
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

    private Future<Deployment> mapDeployment() {
        return deployFuture.map((Deployment) this);
    }

    @Override
    public String getDeploymentId() {
        return deployFuture.result();
    }

    @Override
    public boolean isComplete() {
        return deployFuture.isComplete();
    }

    @Override
    public Future<Deployment> onComplete(Handler<AsyncResult<Deployment>> handler) {
        return mapDeployment().onComplete(handler);
    }

    @Override
    public Deployment result() {
        return succeeded() ? this : null;
    }

    @Override
    public Throwable cause() {
        return deployFuture.cause();
    }

    @Override
    public boolean succeeded() {
        return deployFuture.succeeded();
    }

    @Override
    public boolean failed() {
        return deployFuture.failed();
    }

    @Override
    public <U> Future<U> compose(Function<Deployment, Future<U>> successMapper,
            Function<Throwable, Future<U>> failureMapper) {
        return mapDeployment().compose(successMapper, failureMapper);
    }

    @Override
    public <U> Future<U> transform(Function<AsyncResult<Deployment>, Future<U>> mapper) {
        return mapDeployment().transform(mapper);
    }

    @Override
    @Deprecated
    public <U> Future<Deployment> eventually(Function<Void, Future<U>> mapper) {
        return mapDeployment().eventually(mapper);
    }

    @Override
    public <U> Future<Deployment> eventually(Supplier<Future<U>> supplier) {
        return mapDeployment().eventually(supplier);
    }

    @Override
    public <U> Future<U> map(Function<Deployment, U> mapper) {
        return mapDeployment().map(mapper);
    }

    @Override
    public <V> Future<V> map(V value) {
        return mapDeployment().map(value);
    }

    @Override
    public Future<Deployment> otherwise(Function<Throwable, Deployment> mapper) {
        return mapDeployment().otherwise(mapper);
    }

    @Override
    public Future<Deployment> otherwise(Deployment value) {
        return mapDeployment().otherwise(value);
    }

    @Override
    public ContextInternal context() {
        return ((FutureInternal<String>) deployFuture).context();
    }

    @Override
    public void addListener(Listener<Deployment> listener) {
        ((FutureInternal<Deployment>) mapDeployment()).addListener(listener);
    }

    @Override
    public void removeListener(Listener<Deployment> listener) {
        ((FutureInternal<Deployment>) mapDeployment()).removeListener(listener);
    }

    @Override
    public Future<Deployment> timeout(long delay, TimeUnit unit) {
        return mapDeployment().timeout(delay, unit);
    }
}
