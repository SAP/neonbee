package io.neonbee.internal.verticle;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import io.neonbee.NeonBee;
import io.neonbee.NeonBeeOptions;
import io.neonbee.cluster.SharedEntityModelVerticle;
import io.neonbee.entity.EntityModel;
import io.neonbee.test.base.NeonBeeTestBase;
import io.neonbee.test.helper.WorkingDirectoryBuilder;
import io.vertx.core.impl.VertxImpl;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxTestContext;

public class DeployerVerticleTest extends NeonBeeTestBase {

    // 1. Override DeployerVerticle to be able to catch events and futures
    // 2. Create NeonBeeModule and copy it into verticle folder
    // 3. check that DeployerVerticle has created models temp directory maybe reflections are necessary to get the
    // modelTempDir variable from NeonBeeModule
    // 4. check if verticle are deployed -> maybe reflections are necessary to get the
    // succeededDeployments variable from NeonBeeModule

    // 5. Delete the NeonBeeModule from verticle directory
    // 6. check if modelsTempDir is deleted
    // 7. check if verticle are undeployed.

    private static final Logger LOGGER = LoggerFactory.getLogger(DeployerVerticleTest.class);

    private final Class<?> verticleCls = SharedEntityModelVerticle.class;

    public static final String RESOURCES_FOLDER = "build/resources/test/";

    public static final String MODEL_SOURCES_PATH = "io/neonbee/entity";

    public static final String MODEL_FOLDER = RESOURCES_FOLDER + MODEL_SOURCES_PATH;

    @Override
    protected void adaptOptions(TestInfo testInfo, NeonBeeOptions.Mutable options) {
        options.setDoNotWatchFiles(false);
    }

    @BeforeEach
    @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
    @SuppressWarnings("ReferenceEquality")
    public void beforeEach(VertxTestContext testContext) throws Exception {

        String fileSimpleName = verticleCls.getSimpleName() + ".jar";
        Path workDir = getNeonBee().getOptions().getWorkingDirectory();
        Path module = workDir.resolve(WorkingDirectoryBuilder.MODULES_DIR);
        File moduleDir = module.toFile();
        moduleDir.mkdirs();
        FileUtils.copyFile(createJarFile(verticleCls, getModelsToDeploy(MODEL_FOLDER)),
                new File(moduleDir + "/" + fileSimpleName));

        testContext.completeNow();
    }

    @Test
    @Timeout(value = 50, timeUnit = TimeUnit.SECONDS)
    void deploy(VertxTestContext testContext) {
        final long start = System.currentTimeMillis();
        var vertx = (VertxImpl) getNeonBee().getVertx();
        vertx.setPeriodic(3000, timerId -> {

            Set<String> verticles = new HashSet<>();

            vertx.deploymentIDs().forEach(id -> {
                var verticleClsNames = vertx.getDeployment(id).getVerticles().stream()
                        .map(v -> v.getClass().getSimpleName()).collect(Collectors.toSet());
                LOGGER.info("deployed verticles " + verticleClsNames);
                verticles.addAll(verticleClsNames);

            });

            Map<String, EntityModel> models = NeonBee.get().getModelManager().getBufferedModels();
            if (models != null) {
                LOGGER.info("entity models: "
                        + models.entrySet().stream().map(e -> e.getKey()).collect(Collectors.joining(", ")));
            }

            if (verticles.contains(verticleCls.getSimpleName()) && models.containsKey("io.neonbee.test1")) {
                vertx.cancelTimer(timerId);
                testContext.completeNow();
            } else if (System.currentTimeMillis() - start > 30000) {
                testContext.failNow("verticle " + verticleCls.getSimpleName() + " or model were not deployed ");
                vertx.cancelTimer(timerId);
            }

        });
    }

    public static File createJarFile(Class<?> verticleCls, List<File> models) throws IOException {

        String fileSimpleName = verticleCls.getSimpleName() + ".jar";
        File jarFile = new File(("build/" + fileSimpleName));
        jarFile.delete();
        var manifest = new Manifest();
        manifest.getMainAttributes().put(new Attributes.Name("NeonBee-Module"), verticleCls.getSimpleName());
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(new Attributes.Name("NeonBee-Deployables"), verticleCls.getName());
        StringBuilder modelsAttributeValue = new StringBuilder();
        StringBuilder modelsExtensionsAttributeValue = new StringBuilder();
        models.forEach(model -> {
            if (model.getName().endsWith("csn") || model.getName().endsWith("cds")) {
                if (modelsAttributeValue.length() > 0) {
                    modelsAttributeValue.append(';');
                }
                modelsAttributeValue.append(model.getPath().substring(RESOURCES_FOLDER.length()));
            } else if (model.getName().endsWith("edmx")) {
                if (modelsExtensionsAttributeValue.length() > 0) {
                    modelsExtensionsAttributeValue.append(';');
                }
                modelsExtensionsAttributeValue.append(model.getPath().substring(RESOURCES_FOLDER.length()));
            }
        });

        manifest.getMainAttributes().put(new Attributes.Name("NeonBee-Models"), modelsAttributeValue.toString());
        manifest.getMainAttributes().put(new Attributes.Name("NeonBee-Model-Extensions"),
                modelsExtensionsAttributeValue.toString());

        JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarFile), manifest);

        JarEntry clsEntry = new JarEntry(verticleCls.getName() + ".class");
        jos.putNextEntry(clsEntry);
        jos.write(FileUtils.readFileToByteArray(
                new File("build/classes/java/test/" + verticleCls.getName().replace('.', '/') + ".class")));
        models.forEach(model -> {
            try {
                jos.putNextEntry(new JarEntry(model.getPath().substring(RESOURCES_FOLDER.length())));
                jos.write(FileUtils.readFileToByteArray(model));
            } catch (IOException e) {
                LOGGER.error(e);
            }
        });
        jos.closeEntry();
        jos.finish();
        jos.flush();
        jos.close();
        return jarFile;
    }

    public static List<File> getModelsToDeploy(String folder) {
        List<String> extensions = List.of("edmx", "csn");
        File folderAsFile = new File(folder);
        return Arrays.asList(Objects.requireNonNull(
                folderAsFile.listFiles(file -> extensions.contains(FilenameUtils.getExtension(file.getName())))));
    }

}
