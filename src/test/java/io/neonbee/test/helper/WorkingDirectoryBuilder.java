package io.neonbee.test.helper;

import static io.neonbee.internal.verticle.ServerVerticle.CONFIG_PROPERTY_PORT_KEY;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import com.sap.cds.reflect.CdsModel;

import io.neonbee.entity.ModelDefinitionHelper;
import io.neonbee.internal.verticle.ServerVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Verticle;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;

public final class WorkingDirectoryBuilder {
    /**
     * Relative path to the config directory
     */
    public static final Path CONFIG_DIR = Path.of("config");

    /**
     * Relative path to the verticle directory
     */
    public static final Path VERTICLES_DIR = Path.of("verticles");

    /**
     * Relative path to the models directory
     */
    public static final Path MODELS_DIR = Path.of("models");

    /**
     * Relative path to the logs directory
     */
    public static final Path LOGS_DIR = Path.of("logs");

    private enum DirType {
        NONE, EMPTY, HOLLOW, COPY, STANDARD
    }

    private final DirType dirType;

    private final Path sourcePath;

    private final Map<Class<? extends Verticle>, DeploymentOptions> deploymentOptions = new HashMap<>();

    private final Set<Path> models = new HashSet<>();

    private Consumer<Path> customTask;

    private WorkingDirectoryBuilder(DirType dirType, Path sourcePath) {
        this.dirType = dirType;
        this.sourcePath = sourcePath;
    }

    /**
     * Sets the {@link ServerVerticle} config to default.
     *
     * @return The WorkingDirectoryBuilder
     * @throws IOException In case that no free port can be found.
     */
    public WorkingDirectoryBuilder setDefaultServerVerticleConfig() throws IOException {
        return setVerticleConfig(ServerVerticle.class,
                new JsonObject().put(CONFIG_PROPERTY_PORT_KEY, SystemHelper.getFreePort()));
    }

    /**
     * Sets the config part of the {@link DeploymentOptions} for the passed verticle.
     *
     * @param verticle The related verticle
     * @param config   The config part of the {@link DeploymentOptions}
     * @return The WorkingDirectoryBuilder
     */
    public WorkingDirectoryBuilder setVerticleConfig(Class<? extends Verticle> verticle, JsonObject config) {
        return setDeploymentOptions(verticle, new DeploymentOptions().setConfig(config));
    }

    /**
     * Sets the {@link DeploymentOptions} for the passed verticle.
     *
     * @param verticle The related verticle
     * @param options  The {@link DeploymentOptions}
     * @return The WorkingDirectoryBuilder
     */
    public WorkingDirectoryBuilder setDeploymentOptions(Class<? extends Verticle> verticle, DeploymentOptions options) {
        deploymentOptions.put(verticle, options);
        return this;
    }

    /**
     * Adds the passed model to the new working directory.
     *
     * @param modelPath {@link Path} to the model
     * @return the WorkingDirectoryBuilder
     */
    public WorkingDirectoryBuilder addModel(Path modelPath) {
        models.add(modelPath);
        return this;
    }

    /**
     * Sets a custom task to the {@link WorkingDirectoryBuilder} which is executed as last step during the build of the
     * working directory.
     *
     * @param customTask {@link Path} to the model
     * @return The WorkingDirectoryBuilder
     */
    public WorkingDirectoryBuilder setCustomTask(Consumer<Path> customTask) {
        this.customTask = customTask;
        return this;
    }

    /**
     * Builds the working directory of the related NeonBee instance, inside of the passed working directory root.
     *
     * @param workingDirRoot The {@link Path} to the root of the working directory, the directory don't need to exist
     *                       already.
     * @throws Exception In case that something went wrong.
     */
    public void build(Path workingDirRoot) throws Exception {
        switch (dirType) {
        case NONE:
            break;
        case EMPTY:
            Files.createDirectories(workingDirRoot);
            break;
        case HOLLOW:
            createHollowDirectory(workingDirRoot);
            break;
        case COPY:
            copyDirectory(workingDirRoot);
            break;
        case STANDARD:
            createHollowDirectory(workingDirRoot);
            setDefaultServerVerticleConfig();
            break;

        default:
            break;
        }

        for (Class<? extends Verticle> verticle : deploymentOptions.keySet()) {
            writeDeploymentOptions(verticle, deploymentOptions.get(verticle), workingDirRoot);
        }

        for (Path modelToAdd : models) {
            copyModel(workingDirRoot.resolve(MODELS_DIR), modelToAdd);
        }

        Optional.ofNullable(customTask).ifPresent(ct -> ct.accept(workingDirRoot));
    }

    private static void copyModel(Path modelsDir, Path csnPath) throws IOException {
        String csnContent = Files.readString(csnPath);
        Path csnDestination = modelsDir.resolve(csnPath.getFileName());
        Files.writeString(csnDestination, csnContent);
        CdsModel cdsModel = CdsModel.read(csnContent);

        for (Path edmxPath : ModelDefinitionHelper.resolveEdmxPaths(csnPath, cdsModel)) {
            Path edmxDestination = modelsDir.resolve(edmxPath.getFileName());
            Files.copy(edmxPath, edmxDestination);
        }
    }

    private void createHollowDirectory(Path workingDirRoot) throws IOException {
        for (Path dir : List.of(CONFIG_DIR, VERTICLES_DIR, MODELS_DIR, LOGS_DIR)) {
            Files.createDirectories(workingDirRoot.resolve(dir));
        }
    }

    private void copyDirectory(Path workingDirRoot) throws IOException {
        if (Files.isDirectory(sourcePath)) {
            FileSystemHelper.copyDirectory(sourcePath, workingDirRoot);
        } else {
            FileSystemHelper.extractZipFile(sourcePath, workingDirRoot);
        }
    }

    /**
     * @return a WorkingDirectoryBuilder that creates no working directory. Useful to test the behavior without a
     *         working directory.
     */
    public static WorkingDirectoryBuilder none() {
        return new WorkingDirectoryBuilder(DirType.NONE, null);
    }

    /**
     * @return a WorkingDirectoryBuilder that creates an empty working directory.
     */
    public static WorkingDirectoryBuilder empty() {
        return new WorkingDirectoryBuilder(DirType.EMPTY, null);
    }

    /**
     * @return a WorkingDirectoryBuilder that creates a hollow working directory, containing only these empty folders:
     *         <i>logs</i>, <i>config</i>, <i>verticle</i>, <i>models</i>.
     */
    public static WorkingDirectoryBuilder hollow() {
        return new WorkingDirectoryBuilder(DirType.HOLLOW, null);
    }

    /**
     * @return a WorkingDirectoryBuilder that creates a working directory with the content of a passed source
     *         {@link Path}. The source could be a directory or a zip file.
     */
    public static WorkingDirectoryBuilder copy(Path sourcePath) {
        return new WorkingDirectoryBuilder(DirType.COPY, sourcePath);
    }

    /**
     * @return a WorkingDirectoryBuilder that creates a hollow working directory and adds a default configuration for
     *         the {@link ServerVerticle}.
     */
    public static WorkingDirectoryBuilder standard() {
        return new WorkingDirectoryBuilder(DirType.STANDARD, null);
    }

    /**
     * Reads the {@link DeploymentOptions} of the related Verticle from the config folder of the working directory.
     *
     * @param verticle   The related Verticle
     * @param workingDir The path to the root of the working directory
     * @return {@link DeploymentOptions} of the passed Verticle.
     */
    public static DeploymentOptions readDeploymentOptions(Class<? extends Verticle> verticle, Path workingDir) {
        try {
            Path optsPath = workingDir.resolve(CONFIG_DIR).resolve(verticle.getName() + ".json");
            return new DeploymentOptions(Buffer.buffer(Files.readAllBytes(optsPath)).toJsonObject());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Writes the passed {@link DeploymentOptions} for the related Verticle into the config folder of the working
     * directory.
     *
     * @param verticle   The related Verticle
     * @param opts       The {@link DeploymentOptions} to write
     * @param workingDir The path to the root of the working directory
     */
    public static void writeDeploymentOptions(Class<? extends Verticle> verticle, DeploymentOptions opts,
            Path workingDir) {
        try {
            byte[] config = opts.toJson().toBuffer().getBytes();
            Files.write(workingDir.resolve(CONFIG_DIR).resolve(verticle.getName() + ".json"), config);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
