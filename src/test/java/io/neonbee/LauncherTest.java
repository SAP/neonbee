package io.neonbee;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.Launcher.INTERFACE;
import static io.neonbee.Launcher.parseOptions;
import static io.neonbee.test.helper.FileSystemHelper.createTempDirectory;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

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

    @BeforeAll
    static void setUp() throws IOException {
        tempDirPath = createTempDirectory();
    }

    @AfterAll
    static void tearDown() {
        FileSystemHelper.deleteRecursiveBlocking(tempDirPath);
    }

    @Test
    @DisplayName("should throw error, if working directory value is not passed")
    void throwErrorIfWorkingDirValueIsEmpty() {
        args = new String[] { "-cwd" };
        MissingValueException exception =
                assertThrows(MissingValueException.class, () -> parseOptions(INTERFACE.parse(List.of(args))));
        assertThat(exception.getMessage()).isEqualTo("The option 'working-directory' requires a value");
    }

    @Test
    @DisplayName("should throw error, if instance-name is empty")
    void throwErrorIfInstanceNameIsEmpty() {
        args = new String[] { "-cwd", tempDirPath.toAbsolutePath().toString(), "-name", "" };
        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> parseOptions(INTERFACE.parse(List.of(args))));
        assertThat(exception.getMessage()).isEqualTo("instanceName must not be empty");
    }

    @Test
    @DisplayName("should use passed instance-name")
    void usePassedInstanceName() {
        args = new String[] { "-cwd", tempDirPath.toAbsolutePath().toString(), "-name", "Hodor" };
        NeonBeeOptions neonBeeOptions = parseOptions(INTERFACE.parse(List.of(args)));
        assertThat(neonBeeOptions.getInstanceName()).isEqualTo("Hodor");
    }

    @Test
    @DisplayName("should throw error, if the passed value is other than integer for worker pool size")
    void validateWorkerPoolSizeValue() {
        args = new String[] { "-cwd", tempDirPath.toAbsolutePath().toString(), "-name", "Hodor", "-wps", "hodor" };
        InvalidValueException exception =
                assertThrows(InvalidValueException.class, () -> parseOptions(INTERFACE.parse(List.of(args))));
        assertThat(exception.getMessage()).isEqualTo("The value 'hodor' is not accepted by 'worker-pool-size'");
    }

    @Test
    @DisplayName("should throw error, if the passed value is other than integer for event loop pool size")
    void validateEventLoopPoolSizeValue() {
        args = new String[] { "-cwd", tempDirPath.toAbsolutePath().toString(), "-name", "Hodor", "-elps", "hodor" };
        InvalidValueException exception =
                assertThrows(InvalidValueException.class, () -> parseOptions(INTERFACE.parse(List.of(args))));
        assertThat(exception.getMessage()).isEqualTo("The value 'hodor' is not accepted by 'event-loop-pool-size'");
    }

    @Test
    @DisplayName("should generate expected neonbee options")
    void testExpectedNeonBeeOptions() {
        args = new String[] { "-cwd", tempDirPath.toAbsolutePath().toString(), "-name", "Hodor", "-wps", "2", "-elps",
                "2", "-no-cp", "-no-jobs", "-svp", "9000" };
        NeonBeeOptions neonBeeOptions = parseOptions(INTERFACE.parse(List.of(args)));
        assertThat(neonBeeOptions.getInstanceName()).isEqualTo("Hodor");
        assertThat(neonBeeOptions.getWorkerPoolSize()).isEqualTo(2);
        assertThat(neonBeeOptions.getEventLoopPoolSize()).isEqualTo(2);
        assertThat(neonBeeOptions.shouldIgnoreClassPath()).isTrue();
        assertThat(neonBeeOptions.shouldDisableJobScheduling()).isTrue();
        assertThat(neonBeeOptions.getServerVerticlePort()).isEqualTo(9000);
        args = new String[] {};
        neonBeeOptions = parseOptions(INTERFACE.parse(List.of(args)));
        assertThat(neonBeeOptions.getServerVerticlePort()).isNull();
    }

    @Test
    @DisplayName("should generate expected clustered neonbee options")
    void testExpectedClusterNeonBeeOptions() {
        args = new String[] { "-cwd", tempDirPath.toAbsolutePath().toString(), "-cl", "-cc", "hazelcast-local.xml",
                "-clp", "10000" };
        NeonBeeOptions neonBeeOptions = parseOptions(INTERFACE.parse(List.of(args)));
        assertThat(neonBeeOptions.getClusterPort()).isEqualTo(10000);
        assertThat(neonBeeOptions.isClustered()).isTrue();
        assertThat(neonBeeOptions.getClusterConfig()).isInstanceOf(ClasspathXmlConfig.class);
        ClasspathXmlConfig xmlConfig = (ClasspathXmlConfig) neonBeeOptions.getClusterConfig();
        assertThat(xmlConfig.getNetworkConfig().getPort()).isEqualTo(20000);
    }

    @Test
    @DisplayName("should execute list of preprocessors.")
    void testExecutePreProcessors() {
        TestPreProcessor processor = new TestPreProcessor();
        List<LauncherPreProcessor> preProcessors = List.of(processor);
        Launcher.executePreProcessors(preProcessors, new NeonBeeOptions.Mutable());
        assertThat(processor.isPreProcessorExecuted()).isTrue();
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
}
