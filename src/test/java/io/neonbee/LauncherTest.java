package io.neonbee;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.Launcher.INTERFACE;
import static io.neonbee.Launcher.parseOptions;
import static io.neonbee.test.helper.FileSystemHelper.createTempDirectory;
import static io.neonbee.test.helper.SystemHelper.withEnvironment;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.hazelcast.config.ClasspathXmlConfig;

import io.neonbee.test.helper.FileSystemHelper;
import io.vertx.core.cli.InvalidValueException;
import io.vertx.core.cli.MissingValueException;

class LauncherTest {
    private static Path tempDirPath;

    private String[] args;

    private static String workDir;

    @BeforeAll
    static void setUp() throws IOException {
        tempDirPath = createTempDirectory();
        workDir = tempDirPath.toAbsolutePath().toString();
    }

    @AfterAll
    static void tearDown() {
        FileSystemHelper.deleteRecursiveBlocking(tempDirPath);
    }

    @Test
    @DisplayName("should throw an error, if working directory value is not passed")
    void throwErrorIfWorkingDirValueIsEmpty() {
        args = new String[] { "-cwd" };
        MissingValueException exception = assertThrows(MissingValueException.class, this::parseOptionsArgs);
        assertThat(exception.getMessage()).isEqualTo("The option 'working-directory' requires a value");
    }

    @Test
    @DisplayName("should throw an error, if instance-name is empty")
    void throwErrorIfInstanceNameIsEmpty() {
        args = new String[] { "-cwd", workDir, "-name", "" };
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, this::parseOptionsArgs);
        assertThat(exception.getMessage()).isEqualTo("instanceName must not be empty");
    }

    @Test
    @DisplayName("should throw error, if the passed value is other than integer for worker pool size")
    void validateWorkerPoolSizeValue() {
        args = new String[] { "-cwd", workDir, "-name", "Hodor", "-wps", "hodor" };
        InvalidValueException exception = assertThrows(InvalidValueException.class, this::parseOptionsArgs);
        assertThat(exception.getMessage()).isEqualTo("The value 'hodor' is not accepted by 'worker-pool-size'");
    }

    @Test
    @DisplayName("should throw error, if the passed value is other than integer for event loop pool size")
    void validateEventLoopPoolSizeValue() {
        args = new String[] { "-cwd", workDir, "-name", "Hodor", "-elps", "hodor" };
        InvalidValueException exception = assertThrows(InvalidValueException.class, this::parseOptionsArgs);
        assertThat(exception.getMessage()).isEqualTo("The value 'hodor' is not accepted by 'event-loop-pool-size'");
    }

    @Test
    @DisplayName("should generate expected neonbee options")
    void testExpectedNeonBeeOptions() throws Exception {
        args = new String[] { "-cwd", workDir, "-name", "Hodor", "-wps", "2", "-elps", "2", "-no-cp", "-no-jobs",
                "-port", "9000" };
        assertNeonBeeOptions();

        args = new String[] {};
        Map<String, String> envMap = Map.of("NEONBEE_WORKING_DIR", workDir, "NEONBEE_INSTANCE_NAME", "Hodor",
                "NEONBEE_WORKER_POOL_SIZE", "2", "NEONBEE_EVENT_LOOP_POOL_SIZE", "2", "NEONBEE_IGNORE_CLASS_PATH",
                "true", "NEONBEE_DISABLE_JOB_SCHEDULING", "true", "NEONBEE_SERVER_PORT", "9000");
        withEnvironment(envMap, this::assertNeonBeeOptions);
    }

    @Test
    @DisplayName("should generate expected clustered neonbee options")
    void testExpectedClusterNeonBeeOptions() throws Exception {
        args = new String[] { "-cwd", workDir, "-cl", "-cc", "hazelcast-local.xml", "-clp", "10000" };
        assertClusteredOptions();

        args = new String[] {};
        Map<String, String> envMap = Map.of("NEONBEE_WORKING_DIR", workDir, "NEONBEE_CLUSTERED", "true",
                "NEONBEE_CLUSTER_CONFIG", "hazelcast-local.xml", "NEONBEE_CLUSTER_PORT", "10000");
        withEnvironment(envMap, this::assertClusteredOptions);
    }

    static class TestPreProcessor implements LauncherPreProcessor {
        private boolean preProcessorExecuted;

        @Override
        public void execute(NeonBeeOptions options) {
            this.preProcessorExecuted = true;
        }

        public boolean isPreProcessorExecuted() {
            return preProcessorExecuted;
        }
    }

    private void assertNeonBeeOptions() {
        NeonBeeOptions neonBeeOptions = parseOptionsArgs();
        assertThat(neonBeeOptions.getInstanceName()).isEqualTo("Hodor");
        assertThat(neonBeeOptions.getWorkerPoolSize()).isEqualTo(2);
        assertThat(neonBeeOptions.getEventLoopPoolSize()).isEqualTo(2);
        assertThat(neonBeeOptions.shouldIgnoreClassPath()).isTrue();
        assertThat(neonBeeOptions.shouldDisableJobScheduling()).isTrue();
        assertThat(neonBeeOptions.getServerPort()).isEqualTo(9000);
    }

    private void assertClusteredOptions() {
        NeonBeeOptions neonBeeOptions = parseOptionsArgs();
        assertThat(neonBeeOptions.getClusterPort()).isEqualTo(10000);
        assertThat(neonBeeOptions.isClustered()).isTrue();
        assertThat(neonBeeOptions.getClusterConfig()).isInstanceOf(ClasspathXmlConfig.class);

        ClasspathXmlConfig xmlConfig = (ClasspathXmlConfig) neonBeeOptions.getClusterConfig();
        assertThat(xmlConfig.getNetworkConfig().getPort()).isEqualTo(20000);
    }

    private NeonBeeOptions parseOptionsArgs() {
        return parseOptions(INTERFACE.parse(List.of(args)));
    }
}
