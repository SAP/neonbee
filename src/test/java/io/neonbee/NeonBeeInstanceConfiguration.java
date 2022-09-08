package io.neonbee;

import static io.neonbee.NeonBeeProfile.ALL;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.function.Function;

import io.vertx.test.fakecluster.FakeClusterManager;

/**
 * An annotation to pass NeonBee configuration to the NeonBee instance required for testing.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Target(ElementType.PARAMETER)
@SuppressWarnings("checkstyle:MissingJavadocMethod")
public @interface NeonBeeInstanceConfiguration {
    int eventLoopPoolSize() default 1;

    int workerPoolSize() default 1;

    int clusterPort() default 0;

    boolean clustered() default false;

    String clusterConfigFile() default "hazelcast-localtcp.xml";

    String instanceName() default "";

    String workingDirectoryPath() default "./working_dir/";

    boolean ignoreClassPath() default true;

    boolean doNotWatchFiles() default true;

    boolean disableJobScheduling() default true;

    NeonBeeProfile[] activeProfiles() default { ALL };

    ClusterManager clusterManager() default ClusterManager.FAKE;

    enum ClusterManager {
        FAKE {
            @Override
            Function<NeonBeeOptions, io.vertx.core.spi.cluster.ClusterManager> factory() {
                return (opts) -> new FakeClusterManager();
            }
        },

        HAZELCAST {
            @Override
            Function<NeonBeeOptions, io.vertx.core.spi.cluster.ClusterManager> factory() {
                return NeonBee.HAZELCAST_FACTORY;
            }
        };

        /**
         * @return factory for creating the {@link io.vertx.core.spi.cluster.ClusterManager}
         */
        abstract Function<NeonBeeOptions, io.vertx.core.spi.cluster.ClusterManager> factory();
    }
}
