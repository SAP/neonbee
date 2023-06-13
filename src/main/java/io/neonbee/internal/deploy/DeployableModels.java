package io.neonbee.internal.deploy;

import static java.util.Objects.requireNonNull;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.io.ByteStreams;

import io.neonbee.NeonBee;
import io.neonbee.entity.EntityModelDefinition;
import io.neonbee.internal.helper.AsyncHelper;
import io.neonbee.internal.scanner.ClassPathScanner;
import io.vertx.core.Future;
import io.vertx.core.Vertx;

public class DeployableModels extends Deployable {
    /**
     * The JAR file MANIFEST.MF descriptor for the deployable models.
     */
    public static final String NEONBEE_MODELS = "NeonBee-Models";

    /**
     * The JAR file MANIFEST.MF descriptor for the deployable model extensions / associated models.
     */
    public static final String NEONBEE_MODEL_EXTENSIONS = "NeonBee-Model-Extensions";

    @VisibleForTesting
    final EntityModelDefinition modelDefinition;

    /**
     * Creates a {@link DeployableModels} by scanning the JAR file for model files to deploy.
     *
     * @param vertx   the Vert.x instance used to scan the class path
     * @param jarPath the path to a JAR file to find the models in
     * @return a {@link DeployableModels}
     */
    public static Future<DeployableModels> fromJar(Vertx vertx, Path jarPath) {
        return ClassPathScanner.forJarFile(vertx, jarPath).compose(classPathScanner -> {
            return scanClassPath(vertx, classPathScanner).eventually(classPathScanner.close(vertx));
        });
    }

    @VisibleForTesting
    static Future<DeployableModels> scanClassPath(Vertx vertx, ClassPathScanner classPathScanner) {
        // to load the models, we can use the same class-loader that the class-path-scanner uses, because we anyway
        // just use it to read the resources
        ClassLoader classLoader = classPathScanner.getClassLoader(); // NOPMD false positive getClassLoader
        Future<Map<String, byte[]>> csnModelDefinitions = classPathScanner.scanManifestFiles(vertx, NEONBEE_MODELS)
                .compose(csnModelNames -> readModelPayloads(vertx, classLoader, csnModelNames));
        Future<Map<String, byte[]>> associatedModelDefinitions =
                classPathScanner.scanManifestFiles(vertx, NEONBEE_MODEL_EXTENSIONS)
                        .compose(associatedModelNames -> readModelPayloads(vertx, classLoader, associatedModelNames));
        return Future.all(csnModelDefinitions, associatedModelDefinitions)
                .map(compositeResult -> new EntityModelDefinition(csnModelDefinitions.result(),
                        associatedModelDefinitions.result()))
                .map(DeployableModels::new);
    }

    /**
     * Creates a {@link DeployableModels} from a {@link EntityModelDefinition}.
     *
     * @param modelDefinition the {@link EntityModelDefinition} to deploy
     */
    public DeployableModels(EntityModelDefinition modelDefinition) {
        super();
        this.modelDefinition = modelDefinition;
    }

    @Override
    public String getIdentifier() {
        return modelDefinition.toString();
    }

    @Override
    public PendingDeployment deploy(NeonBee neonBee) {
        return new PendingDeployment(neonBee, this,
                neonBee.getModelManager().registerModels(modelDefinition).mapEmpty()) {
            @Override
            protected Future<Void> undeploy(String deploymentId) {
                return neonBee.getModelManager().unregisterModels(modelDefinition).mapEmpty();
            }
        };
    }

    @VisibleForTesting
    static Future<Map<String, byte[]>> readModelPayloads(Vertx vertx, ClassLoader classLoader,
            List<String> modelPaths) {
        return AsyncHelper.executeBlocking(vertx, () -> {
            Map<String, byte[]> modelDefinitions = new HashMap<>(modelPaths.size());
            for (String modelPath : modelPaths) {
                try (InputStream input = requireNonNull(classLoader.getResourceAsStream(modelPath),
                        "Specified model path wasn't found in NeonBee module")) {
                    modelDefinitions.put(modelPath, ByteStreams.toByteArray(input));
                }
            }
            return modelDefinitions;
        });
    }
}
