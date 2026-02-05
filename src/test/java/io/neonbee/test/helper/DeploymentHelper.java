package io.neonbee.test.helper;

import static java.util.stream.Collectors.toList;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

public final class DeploymentHelper {

    public static final String NEONBEE_NAMESPACE = "neonbee";

    private static final Map<String, Deployment> DEPLOYMENT_INFO_MAP = new HashMap<>();

    private DeploymentHelper() {
        // Utils class no need to instantiate
    }

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
        return vertx.deployVerticle(verticle, new DeploymentOptions().setConfig(verticleConfig))
                .onSuccess(deploymentId -> {
                    DEPLOYMENT_INFO_MAP.put(deploymentId, new Deployment(deploymentId, verticle.getClass()));
                });
    }

    /**
     * This method undeploy the verticle assigned to the passed deploymentID.
     *
     * @param vertx        The related Vert.x instance
     * @param deploymentID The deploymentID to undeploy
     * @return A succeeded future, or a failed future with the cause.
     */
    public static Future<Void> undeployVerticle(Vertx vertx, String deploymentID) {
        DEPLOYMENT_INFO_MAP.remove(deploymentID);
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
                        .filter(deployment -> deployment.getVerticleClass().isAssignableFrom(verticleClass))
                        .map(deployment -> undeployVerticle(vertx, deployment.getDeploymentId())).collect(toList()))
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
        return vertx.deploymentIDs().stream().filter(deploymentId -> DEPLOYMENT_INFO_MAP.containsKey(deploymentId))
                .map(DEPLOYMENT_INFO_MAP::get).collect(toList()).stream();
    }

    private static Stream<Verticle> getAllDeployedVerticles(Vertx vertx) {
        return getAllDeployments(vertx)
                .map(deployment -> vertx.getOrCreateContext().get(deployment.getDeploymentId()))
                .filter(object -> object instanceof Verticle)
                .map(Verticle.class::cast);
    }

    private static class Deployment {
        private final String deploymentId;

        private final Class<? extends Verticle> verticleClass;

        private Deployment(String deploymentId, Class<? extends Verticle> verticleClass) {
            this.deploymentId = deploymentId;
            this.verticleClass = verticleClass;
        }

        public String getDeploymentId() {
            return deploymentId;
        }

        public Class<? extends Verticle> getVerticleClass() {
            return verticleClass;
        }
    }

}
