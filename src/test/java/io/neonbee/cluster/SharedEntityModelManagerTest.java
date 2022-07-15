package io.neonbee.cluster;

import static io.neonbee.internal.verticle.DeployerVerticleTest.MODEL_FOLDER;
import static io.neonbee.internal.verticle.DeployerVerticleTest.getModelsToDeploy;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Isolated;

import com.sap.cds.impl.util.Pair;

import io.neonbee.internal.verticle.DeployerVerticleTest;
import io.neonbee.logging.LoggingFacade;
import io.neonbee.test.helper.WorkingDirectoryBuilder;

/**
 * creates 2 neonbee nodes in cluster, deploys one module with dummy verticle and 2 entity models
 */
@Isolated("")
public class SharedEntityModelManagerTest {

    private static final LoggingFacade LOGGER = LoggingFacade.create();

    private NeonbeeClusterRunner clusterRunner;

    private Class<?> verticleCls = SharedEntityModelVerticle.class;

    @BeforeAll
    static void setUp() throws IOException {
        NeonbeeClusterRunner.init();
    }

    @AfterAll
    static void tearDown() {
        NeonbeeClusterRunner.cleanUp();
    }

    @BeforeEach
    void beforeEach() {
        clusterRunner = new NeonbeeClusterRunner();
    }

    @Test
    void run() throws IOException, InterruptedException {
        List<Pair<Process, StringBuilder>> processInfos = new ArrayList<>(3);
        for (int i = 0; i < 2; i++) {
            processInfos.add(clusterRunner.startProcess(i));
        }

        Thread.sleep(30 * 1000);
        /**
         * assert that every instance of neonbee hasn't obtained the model
         */
        for (Pair<Process, StringBuilder> processInfo : processInfos) {
            assertFalse(Arrays.stream(processInfo.right.toString().split("\n"))
                    .anyMatch(s -> s.contains("obtainedChanges: ") && s.contains("io.neonbee.test2")));
        }

        // deploy test verticle with model to the first neonbee instance
        String fileSimpleName = verticleCls.getSimpleName() + ".jar";
        Path workDir = NeonbeeClusterRunner.getWorkDirPath(0);
        Path module = workDir.resolve(WorkingDirectoryBuilder.MODULES_DIR);
        File moduleDir = module.toFile();
        moduleDir.mkdirs();
        FileUtils.copyFile(DeployerVerticleTest.createJarFile(verticleCls, getModelsToDeploy(MODEL_FOLDER)),
                new File(moduleDir + "/" + fileSimpleName));

        Thread.sleep(150_000);

        processInfos.forEach(pi -> LOGGER.info("process output " + pi.left.pid() + "\n" + pi.right));
        /**
         * assert that every instance of neonbee obtained the model
         */
        for (Pair<Process, StringBuilder> processInfo : processInfos) {
            assertTrue(Arrays.stream(processInfo.right.toString().split("\n"))
                    .anyMatch(s -> s.contains("obtainedChanges: ") && s.contains("io.neonbee.test2")));
        }

    }

    @AfterEach
    void afterEach() {
        clusterRunner.clean();
    }
}
