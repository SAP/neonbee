package io.neonbee.internal.deploy;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.google.common.annotations.VisibleForTesting;

import io.neonbee.NeonBee;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;

public class Deployables extends Deployable {
    /**
     * By default, if any of the deployables fail to deploy, all deployables that have been successfully deployed will
     * be reverted and undeployed again. If true, the {@link #deploy(NeonBee)} will still return a
     * {@link PendingDeployment} with a failed result, however calling {@link Deployment#undeploy()} will undeploy the
     * partial deployment still.
     */
    @VisibleForTesting
    boolean keepPartialDeployment;

    private final List<Deployable> deployables;

    /**
     * Wait for a given list of deployables to load and create a new {@link Deployables} instance from it. E.g.:
     * {@code Deployables.fromDeployables(List.of(DeployableVerticle.fromClass(...)))}.
     *
     * @param deployables a list of deployables to create the {@link Deployables} from
     * @return a future to {@link Deployables}
     */
    public static Future<Deployables> fromDeployables(List<Future<? extends Deployable>> deployables) {
        return Future.all(deployables).map(CompositeFuture::<Deployable>list).map(Deployables::new);
    }

    /**
     * Create a new set of {@link Deployables}.
     *
     * @param deployables the list of {@link Deployables} to deploy
     */
    public Deployables(List<? extends Deployable> deployables) {
        super();
        // copy the list so getDeployables stays modifiable
        this.deployables = new ArrayList<>(requireNonNull(deployables));
    }

    /**
     * A convenience function to deploy all of the deployables to a given NeonBee instance. This is useful for when
     * loading a certain set of deployables asynchronously, because it allows you to simply write:
     * {@code Deployables.fromDeployables(...).compose(allTo(neonBee))}.
     *
     * @param neonBee the NeonBee instance to deploy to
     * @return a mapper mapping to a future of deployment, deploying all of the given deployables
     */
    public static Function<Deployables, Future<Deployment>> allTo(NeonBee neonBee) {
        return deployables -> deployables.deploy(neonBee);
    }

    /**
     * A convenience function to deploy any of the deployables to a given NeonBee instance. This is useful for when
     * loading a certain set of deployables asynchronously, because it allows you to simply write:
     * {@code Deployables.fromDeployables(...).compose(anyTo(neonBee))}.
     *
     * @param neonBee the NeonBee instance to deploy to
     * @return a mapper mapping to a future of deployment, deploying any (also partial deployments allowed) of the given
     *         deployables
     */
    public static Function<Deployables, Future<Deployment>> anyTo(NeonBee neonBee) {
        return deployables -> {
            PendingDeployment pendingDeployment = deployables.keepPartialDeployment().deploy(neonBee);
            return pendingDeployment.otherwise(pendingDeployment);
        };
    }

    @Override
    public String getType() {
        return Deployables.class.getSimpleName();
    }

    @Override
    public String getIdentifier() {
        return "[" + getDeployables().stream().map(Object::toString).collect(Collectors.joining(",")) + "]";
    }

    /**
     * Get a list of all the {@link Deployable} objects managed by this class.
     *
     * @return the deployables of this class. The returned list may be modified by the callee
     */
    public List<Deployable> getDeployables() {
        return deployables;
    }

    /**
     * If called the Deployables will keep any partial deployments deployed, without triggering an immediate undeploy.
     *
     * @return the {@link Deployables} for chaining
     */
    public Deployables keepPartialDeployment() {
        this.keepPartialDeployment = true;
        return this;
    }

    @Override
    public PendingDeployment deploy(NeonBee neonBee) {
        return deploy(neonBee, null);
    }

    /**
     * Trigger the deployments of all deployables of this class. In contrast to calling the public
     * {@link #deploy(NeonBee)} a function can be specified that is called transforming the outcome of the undeploy
     * operation. However the given mapper, can only negatively influence the result of the undeploy operation, meaning:
     * The afterUndeploy function will be called in any case, if the undeployment of all deployments succeeds or fails,
     * however if the undeployment already failed, the result of the undeploy operation will be a failure, regardless of
     * if the afterUndeploy handler is returning a success. In case the undeployment succeeded and the afterUndeploy
     * handler returns a failure, the resulting deployment will fail.
     *
     * @param <T>           the type of the afterUndeploy future will not influence the result of the operation
     * @param neonBee       the NeonBee instance to deploy to
     * @param afterUndeploy the afterUndeploy handler to invoke after all deployments are completed
     * @return a {@link PendingDeployment}
     */
    protected final <T> PendingDeployment deploy(NeonBee neonBee,
            Function<AsyncResult<CompositeFuture>, Future<T>> afterUndeploy) {
        // the general structure / order of this method is a little strange, because we want to create the
        // PendingDeployment of this Deployables instance first, in order to first output the log messages for this
        // Deployables instance before we start deploying the individual deployables

        List<PendingDeployment> pendingDeployments = new ArrayList<>();
        Supplier<Future<Void>> undeploy = () -> {
            return Future.join(pendingDeployments.stream().map(pendingDeployment -> {
                // three possibilities here: pendingDeployment hasn't completed yet, transform will wait for it to
                // complete and then undeploy it. pending deployment was completed successfully, undeploy will undeploy
                // it, or otherwise will do nothing because a failed deployment results in a successful undeploy
                return pendingDeployment.transform(deployResult -> pendingDeployment.undeploy());
            }).collect(Collectors.toList())).transform(undeployResult -> {
                // call the afterUndeploy mapper function regardless of wether the undeployment succeeded or failed
                // in case the undeployment succeeded, the future returned by afterUndeploy may succeed or fail the
                // deployment. if the undeployment failed, the mapper still gets called, but the original failure gets
                // propagated regardless of the outcome of the afterUndeploy handler
                Future<T> afterFuture = afterUndeploy != null ? afterUndeploy.apply(undeployResult) : succeededFuture();
                return undeployResult.succeeded() ? afterFuture
                        : afterFuture.transform(anyResult -> failedFuture(undeployResult.cause()));
            }).mapEmpty();
        };

        Promise<CompositeFuture> deployPromise = Promise.promise();
        PendingDeployment pendingDeployment = new PendingDeployment(neonBee, this,
                deployPromise.future().recover(failedDeployment -> {
                    // in case the deployment (partially) failed, undeploy everything that already got deployed
                    return (!keepPartialDeployment ? undeploy.get() : succeededFuture())
                            .transform(anyResult -> failedFuture(failedDeployment));
                }).map(Integer.toHexString(hashCode()))) {
            @Override
            protected Future<Void> undeploy(String deploymentId) {
                return undeploy.get();
            }
        };

        getDeployables().stream().map(deployable -> deployable.deploy(neonBee)).forEach(pendingDeployments::add);
        // when we should keep partial deployments use a joinComposite, so we wait for all deployments to finish
        // independent if a single one fails or not. in case we should not keep partial deployments (default) use
        // allComposite here, which will fail, when one deployment fails, and thus we can start undeploying all
        // succeeded
        // (or to be succeeded pending deployments) as unfortunately there is no way to cancel active deployments
        (keepPartialDeployment ? Future.join(pendingDeployments) : Future.all(pendingDeployments))
                .onComplete(deployPromise);

        return pendingDeployment;
    }
}
