package io.neonbee.test.helper;

import static java.util.stream.Collectors.toList;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import io.vertx.core.impl.VertxImpl;
import io.vertx.core.json.JsonObject;

public final class DeploymentHelper {

    public static final String NEONBEE_NAMESPACE = "neonbee";

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
                        .filter(deployment -> deployment.getVerticleClass().isAssignableFrom(verticleClass))
                        .map(deploymentObj -> undeployVerticle(vertx, deploymentObj.getDeploymentId()))
                        .collect(toList()))
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

    /**
     * Provides a Stream of the TestDeployment objects of the deployed Verticles. The TestDeployment class contains the
     * deploymentId and the verticle class of a deployed verticle.
     *
     * @param vertx The related Vert.x instance
     * @return A Stream of the TestDeployment objects of the deployed Verticles.
     */
    private static Stream<TestDeployment> getAllDeployments(Vertx vertx) {
        return ((VertxImpl) vertx).deploymentManager().deployments().stream().map(deploymentObject -> {
            try {
                return new TestDeployment(deploymentObject.id(),
                        resolveVerticleClass(deploymentObject.deployment().identifier()));
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }).toList().stream();
    }

    /**
     * Provides a Stream of the deployed Verticles.
     *
     * @param vertx The related Vert.x instance
     * @return A Stream of the deployed Verticles.
     */
    private static Stream<Verticle> getAllDeployedVerticles(Vertx vertx) {
        return getAllDeployments(vertx)
                .map(TestDeployment::getVerticleClass)
                .map(DeploymentHelper::instantiate);
    }

    private static Verticle instantiate(Class<? extends Verticle> clazz) {
        try {
            return clazz.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "Failed to instantiate verticle: " + clazz.getName(), e);
        }
    }

    /**
     * This method resolves the verticle class from the passed identifier. The identifier is the class name of the
     * verticle, prefixed with "java:".
     *
     * @param identifier The identifier to resolve the verticle class from.
     * @return The verticle class resolved from the identifier.
     */
    private static Class<? extends Verticle> resolveVerticleClass(String identifier)
            throws ClassNotFoundException {
        String className = identifier.startsWith("java:")
                ? identifier.substring(5)
                : identifier;
        return Class.forName(className).asSubclass(Verticle.class);
    }

    /**
     * This class represents a TestDeployment class, containing the deploymentId and the verticle class. The
     * TestDeployment class is used to store the deploymentId and the verticle class of a deployed verticle. Vertx 5
     * does not provide a way to get the verticle class of a deployed verticle, so we need to store it ourselves in this
     * class.
     */

    private static final class TestDeployment {

        private final String deploymentId;

        private final Class<? extends Verticle> verticleClass;

        private TestDeployment(String deploymentId, Class<? extends Verticle> verticleClass) {
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
