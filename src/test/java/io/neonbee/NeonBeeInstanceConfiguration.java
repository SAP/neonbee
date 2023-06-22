package io.neonbee;

import static io.neonbee.NeonBeeProfile.ALL;
import static io.vertx.core.Future.succeededFuture;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.neonbee.cluster.ClusterManagerFactory;
import io.vertx.core.Future;
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

    String clusterConfigFile() default "";

    /**
     * Use this option only in combination with {@link io.neonbee.NeonBeeExtension.EncryptedEventbusTestBase}.
     *
     * @return True if the node should encrypt its eventbus communication, or false if not.
     */
    boolean encrypted() default false;

    String instanceName() default "";

    String workingDirectoryPath() default "./working_dir/";

    boolean ignoreClassPath() default true;

    boolean doNotWatchFiles() default true;

    boolean disableJobScheduling() default true;

    NeonBeeProfile[] activeProfiles() default { ALL };

    ClusterManager clusterManager() default ClusterManager.FAKE;

    String metricsRegistryName() default "";

    enum ClusterManager {
        FAKE {
            @Override
            ClusterManagerFactory factory() {
                return new ClusterManagerFactory() {
                    @Override
                    protected String getDefaultConfig() {
                        return null;
                    }

                    @Override
                    public Future<io.vertx.core.spi.cluster.ClusterManager> create(NeonBeeOptions options) {
                        return succeededFuture(new FakeClusterManager());
                    }
                };
            }
        },
        HAZELCAST {
            @Override
            ClusterManagerFactory factory() {
                return ClusterManagerFactory.HAZELCAST_FACTORY;
            }
        },
        INFINISPAN {
            @Override
            ClusterManagerFactory factory() {
                return ClusterManagerFactory.INFINISPAN_FACTORY;
            }
        };

        /**
         * @return factory for creating the {@link io.vertx.core.spi.cluster.ClusterManager}
         */
        abstract ClusterManagerFactory factory();
    }
}
