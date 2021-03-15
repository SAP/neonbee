package io.neonbee;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.test.helper.FileSystemHelper.createTempDirectory;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.hazelcast.config.ClasspathXmlConfig;

import io.neonbee.test.helper.FileSystemHelper;
import io.vertx.core.eventbus.EventBusOptions;

class NeonBeeOptionsTest {
    @Test
    @DisplayName("should generate an instance name if no instance name is passed")
    void generateNameIfNoIsPassed() {
        assertThat(new NeonBeeOptions.Mutable().getInstanceName()).containsMatch("^NeonBee-.{36}$");
    }

    @Test
    @DisplayName("should generate an instance name if null is passed as an instance name")
    void generateNameIfNullIsPassed() {
        assertThat(new NeonBeeOptions.Mutable().setInstanceName(null).getInstanceName())
                .containsMatch("^NeonBee-.{36}$");
    }

    @Test
    @DisplayName("should not generate an instance name if an instance name is passed")
    void usePassedInstanceName() {
        assertThat(new NeonBeeOptions.Mutable().setInstanceName("Hodor").getInstanceName()).isEqualTo("Hodor");
    }

    @Test
    @DisplayName("should throw an error, if passed instance name is empty")
    void checkThatInstanceNameIsNotEmpty() {
        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> new NeonBeeOptions.Mutable().setInstanceName(""));
        assertThat(exception.getMessage()).isEqualTo("instanceName must not be empty");
    }

    @Test
    @DisplayName("should throw an error, if event loop pool size is less then 1")
    void checkThatEventLoopPoolSizeIsIsAtLeastOne() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new NeonBeeOptions.Mutable().setEventLoopPoolSize(0));
        assertThat(exception.getMessage()).isEqualTo("eventLoopSize must be > 0");
    }

    @Test
    @DisplayName("should throw an error, if worker pool size is less then 1")
    void checkThatWorkerPoolSizeIsIsAtLeastOne() {
        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> new NeonBeeOptions.Mutable().setWorkerPoolSize(0));
        assertThat(exception.getMessage()).isEqualTo("workerPoolSize must be > 0");
    }

    @Test
    @DisplayName("should throw an error, if working directory is null")
    void checkThatWorkingDirIsNotNull() {
        NullPointerException exception =
                assertThrows(NullPointerException.class, () -> new NeonBeeOptions.Mutable().setWorkingDirectory(null));
        assertThat(exception.getMessage()).isEqualTo("workingDirectory must not be null");
    }

    @Test
    @DisplayName("should set workingDirectory, if working directory does exist on the file system")
    void checkThatWorkingDirExists() throws IOException {
        Path tempDirPath = createTempDirectory();
        new NeonBeeOptions.Mutable().setWorkingDirectory(tempDirPath);
        FileSystemHelper.deleteRecursiveBlocking(tempDirPath);
    }

    @Test
    @DisplayName("should resolve working directory subfolders according to the working directory path")
    void checkThatSubfoldersExist() throws IOException {
        Path tempDirPath = createTempDirectory();
        NeonBeeOptions opts = new NeonBeeOptions.Mutable().setWorkingDirectory(tempDirPath);
        assertThat((Object) opts.getLogDirectory()).isEqualTo(tempDirPath.resolve("logs"));
        assertThat((Object) opts.getConfigDirectory()).isEqualTo(tempDirPath.resolve("config"));
        assertThat((Object) opts.getModelsDirectory()).isEqualTo(tempDirPath.resolve("models"));
        assertThat((Object) opts.getVerticlesDirectory()).isEqualTo(tempDirPath.resolve("verticles"));
        FileSystemHelper.deleteRecursiveBlocking(tempDirPath);
    }

    @Test
    @DisplayName("should ignore class path if set")
    void shouldIgnoreClassPath() {
        assertThat(new NeonBeeOptions.Mutable().setIgnoreClassPath(true).shouldIgnoreClassPath()).isTrue();
    }

    @Test
    @DisplayName("should not schedule jobs if set")
    void shouldDisableJobScheduling() {
        assertThat(new NeonBeeOptions.Mutable().setDisableJobScheduling(true).shouldDisableJobScheduling()).isTrue();
    }

    @Test
    @DisplayName("should enable clustered mode if set")
    void shouldEnableClustered() {
        assertThat(new NeonBeeOptions.Mutable().setClustered(true).isClustered()).isTrue();
    }

    @Test
    @DisplayName("Test port set correctly")
    void checkClusterPort() {
        assertThat(new NeonBeeOptions.Mutable().getClusterPort()).isEqualTo(EventBusOptions.DEFAULT_CLUSTER_PORT);
        assertThat(new NeonBeeOptions.Mutable().setClusterPort(10000).getClusterPort()).isEqualTo(10000);
    }

    @Test
    @DisplayName("Test config set correctly")
    void checkClusterConfig() {
        assertThat(new NeonBeeOptions.Mutable().setClusterConfigResource("hazelcast-local.xml").getClusterConfig())
                .isInstanceOf(ClasspathXmlConfig.class);
        ClasspathXmlConfig xmlConfig = (ClasspathXmlConfig) new NeonBeeOptions.Mutable()
                .setClusterConfigResource("hazelcast-local.xml").getClusterConfig();
        assertThat(xmlConfig.getNetworkConfig().getPort()).isEqualTo(20000);
    }

    @Test
    @DisplayName("Test server verticle port set correctly")
    void checkServerVerticlePort() {
        assertThat(new NeonBeeOptions.Mutable().getServerVerticlePort()).isNull();
        assertThat(new NeonBeeOptions.Mutable().setServerVerticlePort(10000).getServerVerticlePort()).isEqualTo(10000);
    }

    @Test
    @DisplayName("Test server profiles set correctly")
    void checkProfiles() {
        assertThat(new NeonBeeOptions.Mutable().setActiveProfileValues("CORE,WEB").getActiveProfiles())
                .contains(NeonBeeProfile.CORE);
        assertThat(new NeonBeeOptions.Mutable().setActiveProfileValues("CORE,WEB").getActiveProfiles())
                .contains(NeonBeeProfile.WEB);
        assertThat(new NeonBeeOptions.Mutable().setActiveProfileValues("anything").getActiveProfiles())
                .contains(NeonBeeProfile.ALL);
    }
}
