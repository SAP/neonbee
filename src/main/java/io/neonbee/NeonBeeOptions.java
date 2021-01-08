package io.neonbee;

import static io.neonbee.internal.Helper.EMPTY;
import static java.util.Objects.requireNonNull;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

import com.hazelcast.config.ClasspathXmlConfig;
import com.hazelcast.config.Config;

import io.neonbee.job.JobVerticle;
import io.vertx.core.VertxOptions;
import io.vertx.core.eventbus.EventBusOptions;

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
     * @return the name of the NeonBee instance
     */
    String getInstanceName();

    /**
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
     * @return the verticle directory path
     */
    default Path getVerticlesDirectory() {
        return getWorkingDirectory().resolve("verticle");
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
    Integer getServerVerticlePort();

    /**
     * Gets the currently active profiles.
     *
     * @return the currently active profiles.
     */
    List<NeonBeeProfile> getActiveProfiles();

    /**
     * Get the ID the ID for the TimeZone to be used as default. Either an abbreviation such as "PST", a full name such
     * as "America/Los_Angeles".
     *
     * @return A string with the timezone
     */
    String getTimeZoneId();

    /**
     * Create a mutable NeonBeeOptions similar to VertxOptions, but as NeonBeeOptions are exposed only the interface
     * shall be used, otherwise configuration changes could cause runtime errors. To initialize a new Vertx instance use
     * this Mutable inner class.
     */
    class Mutable implements NeonBeeOptions {
        public static final String DEFAULT_CLUSTER_CONFIG = "hazelcast-cf.xml";

        private int eventLoopPoolSize = VertxOptions.DEFAULT_EVENT_LOOP_POOL_SIZE;

        private int workerPoolSize = VertxOptions.DEFAULT_WORKER_POOL_SIZE;

        private int clusterPort = EventBusOptions.DEFAULT_CLUSTER_PORT;

        private boolean clustered;

        private Config clusterConfig;

        private String instanceName;

        private Path workingDirectoryPath = Path.of(EMPTY);

        private boolean ignoreClassPath;

        private boolean disableJobScheduling;

        private final Supplier<String> generateName = () -> String.format("NeonBee-%s", UUID.randomUUID().toString());

        private Integer serverVerticlePort;

        private List<NeonBeeProfile> activeProfiles = List.of(NeonBeeProfile.ALL);

        private String timeZoneId = "UTC";

        public Mutable() {
            instanceName = generateName.get();
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
        public Mutable setInstanceName(String instanceName) {
            if (Objects.isNull(instanceName)) {
                this.instanceName = generateName.get();
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
        public Mutable setDisableJobScheduling(boolean disableJobScheduling) {
            this.disableJobScheduling = disableJobScheduling;
            return this;
        }

        @Override
        public int getClusterPort() {
            return clusterPort;
        }

        @Override
        public boolean isClustered() {
            return clustered;
        }

        @Override
        public Config getClusterConfig() {
            if (clusterConfig == null) {
                setClusterConfigResource(DEFAULT_CLUSTER_CONFIG);
            }
            return clusterConfig;
        }

        public Mutable setClusterConfigResource(String clusterConfigFile) {
            this.clusterConfig = new ClasspathXmlConfig(clusterConfigFile);
            return this;
        }

        public Mutable setClusterConfig(Config config) {
            this.clusterConfig = config;
            return this;
        }

        public Mutable setClusterPort(int clusterPort) {
            this.clusterPort = clusterPort;
            return this;
        }

        public Mutable setClustered(boolean clustered) {
            this.clustered = clustered;
            return this;
        }

        public Mutable setServerVerticlePort(Integer serverVerticlePort) {
            this.serverVerticlePort = serverVerticlePort;
            return this;
        }

        @Override
        public Integer getServerVerticlePort() {
            return this.serverVerticlePort;
        }

        @Override
        public List<NeonBeeProfile> getActiveProfiles() {
            return this.activeProfiles;
        }

        public Mutable setActiveProfiles(List<NeonBeeProfile> activeProfiles) {
            this.activeProfiles = activeProfiles;
            return this;
        }

        public Mutable setActiveProfileValues(String profileValues) {
            this.activeProfiles = NeonBeeProfile.parseProfiles(profileValues);
            return this;
        }

        @Override
        public String getTimeZoneId() {
            return this.timeZoneId;
        }

        public Mutable setTimeZoneId(String timeZoneId) {
            this.timeZoneId = timeZoneId;
            return this;
        }
    }
}
