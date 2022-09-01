package io.neonbee;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.NeonBeeOptions.Mutable.DEFAULT_CLUSTER_CONFIG;
import static io.neonbee.NeonBeeProfile.ALL;
import static io.neonbee.NeonBeeProfile.CORE;
import static io.neonbee.NeonBeeProfile.WEB;
import static io.neonbee.test.helper.FileSystemHelper.createTempDirectory;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.neonbee.NeonBeeOptions.Mutable;
import io.neonbee.test.helper.FileSystemHelper;
import io.vertx.core.VertxOptions;
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
    @DisplayName("should throw an error, if working directory is null")
    void checkThatWorkingDirIsNotNull() {
        NullPointerException exception =
                assertThrows(NullPointerException.class, () -> new NeonBeeOptions.Mutable().setWorkingDirectory(null));
        assertThat(exception.getMessage()).isEqualTo("workingDirectory must not be null");
    }

    @Test
    @DisplayName("should resolve working directory subfolders according to the working directory path")
    void checkThatSubfoldersExist() throws IOException {
        Path tempDirPath = createTempDirectory();
        NeonBeeOptions opts = new NeonBeeOptions.Mutable().setWorkingDirectory(tempDirPath);
        assertThat((Object) opts.getLogDirectory()).isEqualTo(tempDirPath.resolve("logs"));
        assertThat((Object) opts.getConfigDirectory()).isEqualTo(tempDirPath.resolve("config"));
        assertThat((Object) opts.getModelsDirectory()).isEqualTo(tempDirPath.resolve("models"));
        assertThat((Object) opts.getModulesDirectory()).isEqualTo(tempDirPath.resolve("modules"));
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
    @DisplayName("Test server verticle port set correctly")
    void checkServerVerticlePort() {
        assertThat(new NeonBeeOptions.Mutable().getServerPort()).isNull();
        assertThat(new NeonBeeOptions.Mutable().setServerPort(10000).getServerPort()).isEqualTo(10000);
    }

    @Test
    @DisplayName("Test server profiles set correctly")
    void checkProfiles() {
        Mutable opts = new NeonBeeOptions.Mutable();
        assertThat(opts.getActiveProfiles()).containsExactly(ALL);
        opts = new NeonBeeOptions.Mutable().setActiveProfiles("CORE,WEB");
        assertThat(opts.getActiveProfiles()).containsExactly(CORE, WEB);
        opts = new NeonBeeOptions.Mutable().setActiveProfiles(List.of(CORE, WEB));
        assertThat(opts.getActiveProfiles()).containsExactly(CORE, WEB);
        opts = new NeonBeeOptions.Mutable().setActiveProfiles();
        assertThat(opts.getActiveProfiles()).isEmpty();
        opts = new NeonBeeOptions.Mutable().setActiveProfiles("anything");
        assertThat(opts.getActiveProfiles()).isEmpty();

        opts = new NeonBeeOptions.Mutable().setActiveProfiles(List.of(CORE, WEB, WEB, CORE));
        assertThat(opts.getActiveProfiles()).containsExactly(CORE, WEB);
        Set<NeonBeeProfile> profiles = opts.getActiveProfiles();
        assertThrows(UnsupportedOperationException.class, () -> profiles.add(WEB));

        opts.addActiveProfile(ALL).addActiveProfile(ALL);
        opts.addActiveProfiles(CORE, ALL);
        assertThat(opts.getActiveProfiles()).containsExactly(CORE, WEB, ALL);
        opts.removeActiveProfile(CORE).removeActiveProfile(CORE).removeActiveProfile(WEB);
        opts.removeActiveProfiles(WEB, CORE);
        assertThat(opts.getActiveProfiles()).containsExactly(ALL);

        opts.clearActiveProfiles();
        assertThat(opts.getActiveProfiles()).isEmpty();
    }

    @Test
    @DisplayName("Test event loop pool size getter and setter")
    void testEventLoopPoolSizeGetterSetter() {
        Mutable mutable = new NeonBeeOptions.Mutable();
        assertThat(mutable.getEventLoopPoolSize()).isEqualTo(VertxOptions.DEFAULT_EVENT_LOOP_POOL_SIZE);
        assertThat(mutable.setEventLoopPoolSize(10000).getEventLoopPoolSize()).isEqualTo(10000);
    }

    @Test
    @DisplayName("should throw an error, if event loop pool size is less then 1")
    void checkThatEventLoopPoolSizeIsIsAtLeastOne() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new NeonBeeOptions.Mutable().setEventLoopPoolSize(0));
        assertThat(exception.getMessage()).isEqualTo("eventLoopSize must be > 0");
    }

    @Test
    @DisplayName("Test worker pool size getter and setter")
    void testWorkerPoolSizeGetterSetter() {
        Mutable mutable = new NeonBeeOptions.Mutable();
        assertThat(mutable.getWorkerPoolSize()).isEqualTo(VertxOptions.DEFAULT_WORKER_POOL_SIZE);
        assertThat(mutable.setWorkerPoolSize(10000).getWorkerPoolSize()).isEqualTo(10000);
    }

    @Test
    @DisplayName("should throw an error, if worker pool size is less then 1")
    void checkThatWorkerPoolSizeIsIsAtLeastOne() {
        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> new NeonBeeOptions.Mutable().setWorkerPoolSize(0));
        assertThat(exception.getMessage()).isEqualTo("workerPoolSize must be > 0");
    }

    @Test
    @DisplayName("Test clusterConfig getter and setter")
    void testClusterConfig() {
        Mutable mutable = new NeonBeeOptions.Mutable();
        assertThat(mutable.getClusterConfig()).isEqualTo(DEFAULT_CLUSTER_CONFIG);

        mutable.setClusterConfig("hazelcast-local.xml");
        assertThat(mutable.getClusterConfig()).isEqualTo("hazelcast-local.xml");

        mutable = new NeonBeeOptions.Mutable().setClusterConfig("hazelcast-local.xml");
        assertThat(mutable.getClusterConfig()).isEqualTo("hazelcast-local.xml");
    }

    @Test
    @DisplayName("Test moduleJarPaths getter and setter")
    void testModuleJarPath() {
        Mutable mutable = new NeonBeeOptions.Mutable();
        assertThat(mutable.getModuleJarPaths()).isEmpty();

        List<Path> paths = new ArrayList<>();
        paths.add(Path.of("a"));
        paths.add(Path.of("b"));
        assertThat(mutable.setModuleJarPaths(paths)).isSameInstanceAs(mutable);
        assertThat(mutable.getModuleJarPaths()).isNotSameInstanceAs(paths);

        assertThat(mutable.getModuleJarPaths()).containsExactly(Path.of("a"), Path.of("b"));
        assertThrows(UnsupportedOperationException.class, () -> mutable.getModuleJarPaths().add(Path.of("c")));

        assertThat(mutable.setModuleJarPaths("e", "f").getModuleJarPaths()).containsExactly(Path.of("e"), Path.of("f"));
        assertThat(mutable.setModuleJarPaths("g" + File.pathSeparator + "h",
                "i" + File.pathSeparator + "j" + File.separator + "k").getModuleJarPaths())
                        .containsExactly(Path.of("g"), Path.of("h"), Path.of("i"), Path.of("j" + File.separator + "k"));
    }
}
