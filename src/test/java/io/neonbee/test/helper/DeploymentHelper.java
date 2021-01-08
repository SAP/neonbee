package io.neonbee.test.helper;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import io.neonbee.internal.Helper;
import io.vertx.core.CompositeFuture;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import io.vertx.core.impl.Deployment;
import io.vertx.core.impl.VertxImpl;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.json.JsonObject;

public final class DeploymentHelper {

    public static final String NEONBEE_NAMESPACE = "neonbee";

    /**
     * This method deploys the passed verticle.
     *
     * @param vertx    The related Vert.x instance
     * @param verticle The verticle to deploy
     *
     * @return A succeeded future with the deploymentId, or a failed future with the cause.
     */
    public static Future<String> deployVerticle(Vertx vertx, Verticle verticle) {
        return deployVerticle(vertx, verticle, new JsonObject());
    }

    /**
     * This method deploys the passed verticle with the passed verticle config.
     *
     * @param vertx          The related Vert.x instance
     * @param verticle       The verticle to deploy
     * @param verticleConfig The config for the verticle
     *
     * @return A succeeded future with the deploymentId, or a failed future with the cause.
     */
    public static Future<String> deployVerticle(Vertx vertx, Verticle verticle, JsonObject verticleConfig) {
        DeploymentOptions opts = new DeploymentOptions().setConfig(verticleConfig);
        return Future.future(promise -> vertx.deployVerticle(verticle, opts, promise));
    }

    /**
     * This method undeploys the verticle assigned to the passed deploymentID.
     *
     * @param vertx        The related Vert.x instance
     * @param deploymentID The deploymentID to undeploy
     * @return A succeeded future, or a failed future with the cause.
     */
    public static Future<Void> undeployVerticle(Vertx vertx, String deploymentID) {
        return Future.future(promise -> vertx.undeploy(deploymentID, promise));
    }

    /**
     * Undeploys all verticle of the passed class
     *
     * @param vertx         The related Vert.x instance
     * @param verticleClass The class of the verticle which get undeployed.
     * @return A succeeded future, or a failed future with the cause.
     */
    public static Future<Void> undeployAllVerticlesOfClass(Vertx vertx, Class<? extends Verticle> verticleClass) {
        List<Future<Void>> undeployFutures = new ArrayList<>();
        for (String deploymentID : Set.copyOf(vertx.deploymentIDs())) {
            Deployment deployment = ((VertxImpl) vertx).getDeployment(deploymentID);
            if (deployment.getVerticles().stream().anyMatch(verticle -> verticleClass.isInstance(verticle))) {
                undeployFutures.add(undeployVerticle(vertx, deploymentID));
            }
        }

        return CompositeFuture.all(Helper.uncheckedMapper(undeployFutures)).mapEmpty();
    }

    public static boolean isVerticleDeployed(Vertx vertx, Class<? extends Verticle> verticleToCheck) {
        return vertx.deploymentIDs().stream()
                .flatMap(deploymentId -> ((VertxInternal) vertx).getDeployment(deploymentId).getVerticles().stream())
                .filter(verticle -> verticleToCheck.isInstance(verticle)).findFirst().isPresent();
    }

    private DeploymentHelper() {
        // Utils class no need to instantiate
    }
}
