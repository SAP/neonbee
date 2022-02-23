package io.neonbee;

import static io.neonbee.NeonBeeProfile.parseProfiles;
import static io.neonbee.internal.helper.StringHelper.EMPTY;
import static java.util.Objects.requireNonNull;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.hazelcast.config.ClasspathXmlConfig;
import com.hazelcast.config.Config;

import io.neonbee.internal.helper.FileSystemHelper;
import io.neonbee.internal.verticle.WatchVerticle;
import io.neonbee.job.JobVerticle;
import io.vertx.core.VertxOptions;
import io.vertx.core.cli.annotations.ConvertedBy;
import io.vertx.core.cli.annotations.DefaultValue;
import io.vertx.core.cli.annotations.Description;
import io.vertx.core.cli.annotations.Name;
import io.vertx.core.cli.annotations.Option;
import io.vertx.core.cli.annotations.Summary;
import io.vertx.core.cli.converters.Converter;
import io.vertx.core.eventbus.EventBusOptions;

@SuppressWarnings("PMD.ExcessivePublicCount")
public interface NeonBeeOptions {
    /**
     * Get the maximum number of worker threads to be used by the NeonBee instance.
     * <p>
     * Worker threads are used for running blocking code and worker verticle.
     *
     * @return the maximum number of worker threads
     */
    int getEventLoopPoolSize();

    /**
     * Get the maximum number of worker threads to be used by the NeonBee instance.
     * <p>
     * Worker threads are used for running blocking code and worker verticle.
     *
     * @return the maximum number of worker threads
     */
    int getWorkerPoolSize();

    /**
     * Returns the name of the NeonBee instance.
     *
     * @return the name of the NeonBee instance
     */
    String getInstanceName();

    /**
     * Returns the current working directory path.
     *
     * @return the current working directory path
     */
    Path getWorkingDirectory();

    /**
     * Returns the config directory path resolved against the {@link #getWorkingDirectory working directory}.
     *
     * @return the config directory path
     */
    default Path getConfigDirectory() {
        return getWorkingDirectory().resolve("config");
    }

    /**
     * Returns the verticle directory path resolved against the {@link #getWorkingDirectory working directory}.
     *
     * @deprecated use {@link #getModulesDirectory} instead and place "verticle JARs" in the {@code /modules} directory
     *             instead of in the {@code /verticles} directory
     * @return the verticle directory path
     */
    @Deprecated
    default Path getVerticlesDirectory() {
        return getWorkingDirectory().resolve("verticles");
    }

    /**
     * Returns the modules directory path resolved against the {@link #getWorkingDirectory working directory}.
     *
     * @return the modules directory path
     */
    default Path getModulesDirectory() {
        return getWorkingDirectory().resolve("modules");
    }

    /**
     * Returns the models directory path resolved against the {@link #getWorkingDirectory working directory}.
     *
     * @return the models directory path
     */
    default Path getModelsDirectory() {
        return getWorkingDirectory().resolve("models");
    }

    /**
     * Returns the logs directory path resolved against the {@link #getWorkingDirectory working directory}.
     *
     * @return the logs directory path
     */
    default Path getLogDirectory() {
        return getWorkingDirectory().resolve("logs");
    }

    /**
     * Check if NeonBee should ignore verticle / models on the class path.
     *
     * @return true if class path should be ignored, otherwise false.
     */
    boolean shouldIgnoreClassPath();

    /**
     * Check if NeonBee should disable scheduling jobs via {@link JobVerticle}s.
     *
     * @return true if NeonBee should not schedule any job verticle, otherwise false.
     */
    boolean shouldDisableJobScheduling();

    /**
     * Check if NeonBee should disable watching files via {@link WatchVerticle}s.
     *
     * @return true if NeonBee should not watch files, otherwise false.
     */
    boolean doNotWatchFiles();

    /**
     * Get the port number of the event bus. If not set, a random port will be selected.
     *
     * @return the port number of the event bus
     */
    int getClusterPort();

    /**
     * Whether NeonBee should be started in cluster mode.
     *
     * @return whether NeonBee should be started in cluster mode
     */
    boolean isClustered();

    /**
     * Gets Hazelcast cluster configuration.
     *
     * @return Hazelcast cluster configuration
     */
    Config getClusterConfig();

    /**
     * Get the port number of the server verticle. If not set, the port number will be retrieved from the server
     * verticle config.
     *
     * @return the port number of the server verticle
     */
    Integer getServerPort();

    /**
     * Gets the active profiles.
     *
     * @return the active profiles
     */
    Set<NeonBeeProfile> getActiveProfiles();

    /**
     * Returns a list of paths to module JARs, that should be deployed when NeonBee starts.
     *
     * @return a list of paths to JAR files
     */
    List<Path> getModuleJarPaths();

    /**
     * Create a mutable NeonBeeOptions similar to VertxOptions, but as NeonBeeOptions are exposed only the interface
     * shall be used, otherwise configuration changes could cause runtime errors. To initialize a new Vertx instance use
     * this Mutable inner class.
     */
    @Name("neonbee")
    @Summary("A command line interface for starting and configuring a NeonBee and its associated Vert.x instance")
    class Mutable implements NeonBeeOptions {
        /**
         * The default cluster configuration file name.
         */
        public static final String DEFAULT_CLUSTER_CONFIG = "hazelcast-cf.xml";

        /**
         * The default active profiles.
         */
        public static final String DEFAULT_ACTIVE_PROFILES = "ALL";

        private int eventLoopPoolSize = VertxOptions.DEFAULT_EVENT_LOOP_POOL_SIZE;

        private int workerPoolSize = VertxOptions.DEFAULT_WORKER_POOL_SIZE;

        private int clusterPort = EventBusOptions.DEFAULT_CLUSTER_PORT;

        private boolean clustered;

        private Config clusterConfig;

        private String instanceName;

        private Path workingDirectoryPath = Path.of(EMPTY);

        private boolean ignoreClassPath;

        private boolean disableJobScheduling;

        private boolean doNotWatchFiles;

        private Integer serverPort;

        private Set<NeonBeeProfile> activeProfiles = parseProfiles(DEFAULT_ACTIVE_PROFILES);

        private List<Path> moduleJarPaths = Collections.emptyList();

        /**
         * Instantiates a mutable {@link NeonBeeOptions} instance.
         */
        public Mutable() {
            instanceName = generateName();
        }

        @Override
        public int getEventLoopPoolSize() {
            return eventLoopPoolSize;
        }

        /**
         * Set the maximum number of event loop threads to be used by the NeonBee instance. The number of threads must
         * be larger then 0.
         *
         * @param eventLoopPoolSize the number of threads
         * @return a reference to this, so the API can be used fluently
         */
        @Option(longName = "event-loop-pool-size", shortName = "elps")
        @Description("Set the number of threads for the event loop pool")
        public Mutable setEventLoopPoolSize(int eventLoopPoolSize) {
            if (eventLoopPoolSize < 1) {
                throw new IllegalArgumentException("eventLoopSize must be > 0");
            }
            this.eventLoopPoolSize = eventLoopPoolSize;
            return this;
        }

        @Override
        public int getWorkerPoolSize() {
            return workerPoolSize;
        }

        /**
         * Set the maximum number of worker threads to be used by the NeonBee instance. The number of threads must be
         * larger then 0.
         *
         * @param workerPoolSize the number of threads
         * @return a reference to this, so the API can be used fluently
         */
        @Option(longName = "worker-pool-size", shortName = "wps")
        @Description("Set the number of threads for the worker pool")
        public Mutable setWorkerPoolSize(int workerPoolSize) {
            if (workerPoolSize < 1) {
                throw new IllegalArgumentException("workerPoolSize must be > 0");
            }
            this.workerPoolSize = workerPoolSize;
            return this;
        }

        @Override
        public String getInstanceName() {
            return instanceName;
        }

        /**
         * Set the name of the NeonBee instance. The instance name must have at least one character. If null is passed,
         * a new instance name will be generated.
         *
         * @param instanceName the name of the NeonBee instance
         * @return a reference to this, so the API can be used fluently
         */
        @Option(longName = "instance-name", shortName = "name")
        @Description("Set the instance name")
        public Mutable setInstanceName(String instanceName) {
            if (Objects.isNull(instanceName)) {
                this.instanceName = generateName();
            } else if (instanceName.isEmpty()) {
                throw new IllegalArgumentException("instanceName must not be empty");
            } else {
                this.instanceName = instanceName;
            }
            return this;
        }

        @Override
        public Path getWorkingDirectory() {
            return workingDirectoryPath;
        }

        /**
         * Set the working directory of the NeonBee instance. The working directory must be not null and must exist on
         * the file system.
         *
         * @param workingDirectory the name of the NeonBee instance
         * @return a reference to this, so the API can be used fluently
         */
        @Option(longName = "working-directory", shortName = "cwd")
        @Description("Set the current working directory")
        @DefaultValue("./working_dir/")
        @ConvertedBy(PathConverter.class)
        public Mutable setWorkingDirectory(Path workingDirectory) {
            requireNonNull(workingDirectory, "workingDirectory must not be null");
            this.workingDirectoryPath = workingDirectory.toAbsolutePath().normalize();
            return this;
        }

        @Override
        public boolean shouldIgnoreClassPath() {
            return ignoreClassPath;
        }

        /**
         * Sets whether NeonBee should ignore verticle / models on the class path.
         *
         * @param ignoreClassPath flag true/false
         * @return a reference to this, so the API can be used fluently
         */
        @Option(longName = "ignore-class-path", shortName = "no-cp", flag = true)
        @Description("Set whether to ignore verticle and models on the class path or not")
        public Mutable setIgnoreClassPath(boolean ignoreClassPath) {
            this.ignoreClassPath = ignoreClassPath;
            return this;
        }

        @Override
        public boolean shouldDisableJobScheduling() {
            return disableJobScheduling;
        }

        /**
         * Sets whether NeonBee should not schedule any job verticle.
         *
         * @param disableJobScheduling flag true/false
         * @return a reference to this, so the API can be used fluently
         */
        @Option(longName = "disable-job-scheduling", shortName = "no-jobs", flag = true)
        @Description("Set whether to schedule any job verticles or not")
        public Mutable setDisableJobScheduling(boolean disableJobScheduling) {
            this.disableJobScheduling = disableJobScheduling;
            return this;
        }

        @Override
        public boolean doNotWatchFiles() {
            return doNotWatchFiles;
        }

        /**
         * Sets whether NeonBee should watch files or not.
         *
         * @param doNotWatchFiles flag true/false
         * @return a reference to this, so the API can be used fluently
         */
        @Option(longName = "do-not-watch-files", shortName = "no-watchers", flag = true)
        @Description("Set whether to watch any files")
        public Mutable setDoNotWatchFiles(boolean doNotWatchFiles) {
            this.doNotWatchFiles = doNotWatchFiles;
            return this;
        }

        @Override
        public boolean isClustered() {
            return clustered;
        }

        /**
         * Set clustered.
         *
         * @param clustered true if clustered
         * @return this instance for chaining
         */
        @Option(longName = "clustered", shortName = "cl", flag = true)
        @Description("Set whether to start in clustered mode or not")
        public Mutable setClustered(boolean clustered) {
            this.clustered = clustered;
            return this;
        }

        @Override
        public int getClusterPort() {
            return clusterPort;
        }

        /**
         * Set the port used for clustering.
         *
         * @param clusterPort the port
         * @return this instance for chaining
         */
        @Option(longName = "cluster-port", shortName = "clp")
        @Description("Set the port of cluster event bus")
        public Mutable setClusterPort(int clusterPort) {
            this.clusterPort = clusterPort;
            return this;
        }

        @Override
        public Config getClusterConfig() {
            if (clusterConfig == null) {
                setClusterConfigResource(DEFAULT_CLUSTER_CONFIG);
            }
            return clusterConfig;
        }

        /**
         * Set the cluster config.
         *
         * @param config the cluster config
         * @return this instance for chaining
         */
        public Mutable setClusterConfig(Config config) {
            this.clusterConfig = config;
            return this;
        }

        /**
         * Set a cluster config by loading a resource from the class path (blocking).
         *
         * @param resource the resource, an XML configuration file from the class path
         * @return this instance for chaining
         */
        @Option(longName = "cluster-config", shortName = "cc")
        @Description("Set the cluster/Hazelast configuration file path")
        public Mutable setClusterConfigResource(String resource) {
            return setClusterConfig(new ClasspathXmlConfig(resource));
        }

        @Override
        public Integer getServerPort() {
            return this.serverPort;
        }

        /**
         * Set the server port.
         *
         * @param serverPort the server port
         * @return this instance for chaining
         */
        @Option(longName = "server-port", shortName = "port")
        @Description("Set the HTTP(S) port of server")
        public Mutable setServerPort(Integer serverPort) {
            this.serverPort = serverPort;
            return this;
        }

        @Override
        public Set<NeonBeeProfile> getActiveProfiles() {
            return activeProfiles;
        }

        /**
         * Set the active profiles.
         *
         * @param profiles the profiles
         * @return this instance for chaining
         */
        public Mutable setActiveProfiles(Collection<NeonBeeProfile> profiles) {
            this.activeProfiles = ImmutableSet.copyOf(requireNonNull(profiles));
            return this;
        }

        /**
         * Set the active profiles.
         *
         * @param activeProfiles the profiles
         * @return this instance for chaining
         */
        @Option(longName = "active-profiles", shortName = "ap", acceptMultipleValues = true)
        @DefaultValue(DEFAULT_ACTIVE_PROFILES)
        @Description("Set the active deployment profiles")
        public Mutable setActiveProfiles(String... activeProfiles) {
            return setActiveProfiles(Arrays.stream(activeProfiles).map(NeonBeeProfile::parseProfiles)
                    .flatMap(Collection::stream).collect(Collectors.toSet()));
        }

        /**
         * Add an active profile.
         *
         * @param profile the active profile to add
         * @return this instance for chaining
         */
        public Mutable addActiveProfile(NeonBeeProfile profile) {
            return addActiveProfiles(profile);
        }

        /**
         * Add active profiles.
         *
         * @param profiles the active profiles to add
         * @return this instance for chaining
         */
        public Mutable addActiveProfiles(NeonBeeProfile... profiles) {
            this.activeProfiles = Sets.union(this.activeProfiles, Set.of(profiles));
            return this;
        }

        /**
         * Remove an active profile.
         *
         * @param profile the active profile to remove
         * @return this instance for chaining
         */
        public Mutable removeActiveProfile(NeonBeeProfile profile) {
            return removeActiveProfiles(profile);
        }

        /**
         * Remove active profiles.
         *
         * @param profiles the active profiles to remove
         * @return this instance for chaining
         */
        public Mutable removeActiveProfiles(NeonBeeProfile... profiles) {
            this.activeProfiles = Sets.difference(this.activeProfiles, Set.of(profiles));
            return this;
        }

        /**
         * Remove all active profiles. Equivalent of setting an empty set.
         *
         * @return this instance for chaining
         */
        public Mutable clearActiveProfiles() {
            this.activeProfiles = Set.of();
            return this;
        }

        @Override
        public List<Path> getModuleJarPaths() {
            return moduleJarPaths;
        }

        /**
         * Set paths to modules that will be deployed when NeonBee is starting.
         *
         * @param moduleJarPaths a collection of paths
         * @return this instance for chaining
         */
        public Mutable setModuleJarPaths(List<Path> moduleJarPaths) {
            this.moduleJarPaths = ImmutableList.copyOf(requireNonNull(moduleJarPaths));
            return this;
        }

        /**
         * Sets a list of module path(s) to be loaded when NeonBee is starting.
         *
         * @param moduleJarPaths any number of path strings to JARs
         * @return this instance for chaining
         */
        @Option(longName = "module-jar-paths", shortName = "mjp")
        @Description("A list of path(s) to module JARs to be loaded during startup")
        public Mutable setModuleJarPaths(String... moduleJarPaths) {
            return this.setModuleJarPaths(Arrays.stream(moduleJarPaths).map(FileSystemHelper::parsePaths)
                    .flatMap(Collection::stream).collect(Collectors.toList()));
        }

        private String generateName() {
            return String.format("%s-%s", NeonBee.class.getSimpleName(), UUID.randomUUID().toString());
        }
    }

    class PathConverter implements Converter<Path> {
        @Override
        public Path fromString(String s) {
            return Path.of(s);
        }
    }
}
