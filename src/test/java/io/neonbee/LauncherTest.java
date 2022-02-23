package io.neonbee;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.Launcher.parseCommandLine;
import static io.neonbee.test.helper.FileSystemHelper.createTempDirectory;
import static io.neonbee.test.helper.SystemHelper.withEnvironment;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import com.hazelcast.config.ClasspathXmlConfig;

import io.neonbee.Launcher.EnvironmentAwareCommandLine;
import io.neonbee.test.helper.FileSystemHelper;
import io.vertx.core.cli.Argument;
import io.vertx.core.cli.CLI;
import io.vertx.core.cli.CLIException;
import io.vertx.core.cli.CommandLine;
import io.vertx.core.cli.MissingValueException;
import io.vertx.core.cli.Option;
import io.vertx.core.cli.annotations.CLIConfigurator;
import io.vertx.core.cli.impl.DefaultCommandLine;

@Isolated("some tests modify the global ProcessEnvironment using SystemHelper.withEnvironment")
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
        MissingValueException exception = assertThrows(MissingValueException.class, this::parseArgs);
        assertThat(exception.getMessage()).isEqualTo("The option 'working-directory' requires a value");
    }

    @Test
    @DisplayName("should have NeonBeeProfile ALL by default")
    void testDefaultActiveProfiles() {
        args = new String[] {};
        assertThat(parseArgs().getActiveProfiles()).containsExactly(NeonBeeProfile.ALL);
    }

    @Test
    @DisplayName("should throw an error, if instance-name is empty")
    void throwErrorIfInstanceNameIsEmpty() {
        args = new String[] { "-cwd", workDir, "-name", "" };
        CLIException exception = assertThrows(CLIException.class, this::parseArgs);
        assertThat(exception.getMessage()).isEqualTo("Cannot inject value for option 'instance-name'");
    }

    @Test
    @DisplayName("should throw error, if the passed value is other than integer for worker pool size")
    void validateWorkerPoolSizeValue() {
        args = new String[] { "-cwd", workDir, "-name", "Hodor", "-wps", "hodor" };
        CLIException exception = assertThrows(CLIException.class, this::parseArgs);
        assertThat(exception.getMessage()).isEqualTo("Cannot inject value for option 'worker-pool-size'");
    }

    @Test
    @DisplayName("should throw error, if the passed value is other than integer for event loop pool size")
    void validateEventLoopPoolSizeValue() {
        args = new String[] { "-cwd", workDir, "-name", "Hodor", "-elps", "hodor" };
        CLIException exception = assertThrows(CLIException.class, this::parseArgs);
        assertThat(exception.getMessage()).isEqualTo("Cannot inject value for option 'event-loop-pool-size'");
    }

    @Test
    @DisplayName("should generate expected neonbee options")
    void testExpectedNeonBeeOptions() throws Exception {
        args = new String[] { "-cwd", workDir, "-name", "Hodor", "-wps", "2", "-elps", "2", "-no-cp", "-no-jobs",
                "-port", "9000", "-mjp", "path1", "path2", "path3" + File.pathSeparator + "path4" };
        assertNeonBeeOptions();

        args = new String[] {};
        assertThat(parseArgs().getServerPort()).isNull();
    }

    @Test
    @DisplayName("should generate expected neonbee options")
    void testExpectedNeonBeeEnvironmentOptions() throws Exception {
        args = new String[] {};
        Map<String, String> envMap = Map.of("NEONBEE_WORKING_DIR", workDir, "NEONBEE_INSTANCE_NAME", "Hodor",
                "NEONBEE_WORKER_POOL_SIZE", "2", "NEONBEE_EVENT_LOOP_POOL_SIZE", "2", "NEONBEE_IGNORE_CLASS_PATH",
                "true", "NEONBEE_DISABLE_JOB_SCHEDULING", "true", "NEONBEE_SERVER_PORT", "9000",
                "NEONBEE_MODULE_JAR_PATHS",
                "path1" + File.pathSeparator + "path2" + File.pathSeparator + "path3" + File.pathSeparator + "path4");
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

    @Test
    @DisplayName("test EnvironmentAwareCommandLine")
    void testEnvironmentAwareCommandLine() {
        CLI cliMock = mock(CLI.class);
        Option option = new Option().setLongName("option");
        when(cliMock.getOption(any())).thenReturn(option);
        Option flag = new Option().setLongName("flag").setFlag(true);
        when(cliMock.getOption("flag")).thenReturn(flag);
        Argument argument = new Argument();
        when(cliMock.getArgument(any())).thenReturn(argument);
        when(cliMock.getArgument(anyInt())).thenReturn(argument);

        CommandLine commandLineMock = mock(DefaultCommandLine.class);
        when(commandLineMock.cli()).thenReturn(cliMock);

        EnvironmentAwareCommandLine commandLine = spy(new EnvironmentAwareCommandLine(commandLineMock));

        clearInvocations(commandLine);
        commandLine.isFlagEnabled("flag");
        verify(commandLine).hasEnvArg(any());

        clearInvocations(commandLine);
        commandLine.isSeenInCommandLine(option);
        verify(commandLine).hasEnvArg(any());

        clearInvocations(commandLine);
        commandLine.getRawValueForOption(option);
        verify(commandLine).hasEnvArg(option);

        clearInvocations(commandLine);
        commandLine.getRawValuesForOption(option);
        verify(commandLine).hasEnvArg(option);
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
        NeonBeeOptions neonBeeOptions = parseArgs();
        assertThat(neonBeeOptions.getInstanceName()).isEqualTo("Hodor");
        assertThat(neonBeeOptions.getWorkerPoolSize()).isEqualTo(2);
        assertThat(neonBeeOptions.getEventLoopPoolSize()).isEqualTo(2);
        assertThat(neonBeeOptions.shouldIgnoreClassPath()).isTrue();
        assertThat(neonBeeOptions.shouldDisableJobScheduling()).isTrue();
        assertThat(neonBeeOptions.getServerPort()).isEqualTo(9000);
        assertThat(neonBeeOptions.getModuleJarPaths()).containsExactly(Path.of("path1"), Path.of("path2"),
                Path.of("path3"), Path.of("path4"));
    }

    private void assertClusteredOptions() {
        NeonBeeOptions neonBeeOptions = parseArgs();
        assertThat(neonBeeOptions.getClusterPort()).isEqualTo(10000);
        assertThat(neonBeeOptions.isClustered()).isTrue();
        assertThat(neonBeeOptions.getClusterConfig()).isInstanceOf(ClasspathXmlConfig.class);

        ClasspathXmlConfig xmlConfig = (ClasspathXmlConfig) neonBeeOptions.getClusterConfig();
        assertThat(xmlConfig.getNetworkConfig().getPort()).isEqualTo(20000);
    }

    private NeonBeeOptions parseArgs() {
        NeonBeeOptions.Mutable options = new NeonBeeOptions.Mutable();
        CLIConfigurator.inject(parseCommandLine(args), options);
        return options;
    }
}
