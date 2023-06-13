package io.neonbee.test.helper;

import static java.util.stream.Collectors.toList;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import io.vertx.core.impl.Deployment;
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
        return vertx.deployVerticle(verticle, new DeploymentOptions().setConfig(verticleConfig));
    }

    /**
     * This method undeploy the verticle assigned to the passed deploymentID.
     *
     * @param vertx        The related Vert.x instance
     * @param deploymentID The deploymentID to undeploy
     * @return A succeeded future, or a failed future with the cause.
     */
    public static Future<Void> undeployVerticle(Vertx vertx, String deploymentID) {
        return vertx.undeploy(deploymentID);
    }

    /**
     * Undeploy all verticle of the passed class
     *
     * @param vertx         The related Vert.x instance
     * @param verticleClass The class of the verticle which get undeployed.
     * @return A succeeded future, or a failed future with the cause.
     */
    public static Future<Void> undeployAllVerticlesOfClass(Vertx vertx, Class<? extends Verticle> verticleClass) {
        return Future
                .all(getAllDeployments(vertx)
                        .filter(deployment -> deployment.getVerticles().stream().anyMatch(verticleClass::isInstance))
                        .map(deployment -> undeployVerticle(vertx, deployment.deploymentID())).collect(toList()))
                .mapEmpty();
    }

    public static boolean isVerticleDeployed(Vertx vertx, Class<? extends Verticle> verticleToCheck) {
        return getAllDeployedVerticles(vertx).anyMatch(verticleToCheck::isInstance);
    }

    /**
     * Provides a Set of the classes of the deployed Verticles.
     *
     * @param vertx The related Vert.x instance
     * @return A Set of the classes of the deployed Verticles.
     */
    public static Set<Class<? extends Verticle>> getDeployedVerticles(Vertx vertx) {
        return getAllDeployedVerticles(vertx).map(Verticle::getClass).collect(Collectors.toSet());
    }

    private static Stream<Deployment> getAllDeployments(Vertx vertx) {
        return vertx.deploymentIDs().stream().map(((VertxInternal) vertx)::getDeployment);
    }

    private static Stream<Verticle> getAllDeployedVerticles(Vertx vertx) {
        return getAllDeployments(vertx).map(Deployment::getVerticles).flatMap(Set::stream);
    }

    private DeploymentHelper() {
        // Utils class no need to instantiate
    }
}
