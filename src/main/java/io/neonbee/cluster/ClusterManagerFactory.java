package io.neonbee.cluster;

import static com.google.common.base.Strings.isNullOrEmpty;
import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static java.lang.ClassLoader.getSystemClassLoader;

import java.io.IOException;

import org.infinispan.manager.DefaultCacheManager;

import com.hazelcast.config.ClasspathXmlConfig;

import io.neonbee.NeonBeeOptions;
import io.vertx.core.Future;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.ext.cluster.infinispan.InfinispanClusterManager;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;

public abstract class ClusterManagerFactory {

    /**
     * The ClusterManagerFactory for Hazelcast.
     */
    public static final ClusterManagerFactory HAZELCAST_FACTORY = new ClusterManagerFactory() {
        @Override
        protected String getDefaultConfig() {
            return "hazelcast-cf.xml";
        }

        @Override
        public Future<ClusterManager> create(NeonBeeOptions neonBeeOptions) {
            String effectiveConfig = getEffectiveConfig(neonBeeOptions);
            return succeededFuture(new HazelcastClusterManager(new ClasspathXmlConfig(effectiveConfig)));
        }
    };

    /**
     * The ClusterManagerFactory for Infinispan.
     */
    public static final ClusterManagerFactory INFINISPAN_FACTORY = new ClusterManagerFactory() {
        @Override
        protected String getDefaultConfig() {
            return "infinispan-local.xml";
        }

        @Override
        public Future<ClusterManager> create(NeonBeeOptions neonBeeOptions) {
            String effectiveConfig = getEffectiveConfig(neonBeeOptions);

            try {
                Thread.currentThread().setContextClassLoader(getSystemClassLoader());
                return succeededFuture(new InfinispanClusterManager(new DefaultCacheManager(effectiveConfig, true)));
            } catch (IOException e) {
                return failedFuture(e);
            }
        }
    };

    /**
     * Returns the path of the effective configuration file for a ClusterManager. If no custom config path is set, the
     * default path will be returned.
     *
     * @param options The NeonBee options
     * @return the path of the effective configuration file.
     */
    protected String getEffectiveConfig(NeonBeeOptions options) {
        return isNullOrEmpty(options.getClusterConfig()) ? getDefaultConfig() : options.getClusterConfig();
    }

    /**
     * Returns the path to the default configuration file for the related ClusterManager.
     *
     * @return the path to the default configuration file.
     */
    protected abstract String getDefaultConfig();

    /**
     * Creates the related ClusterManager based on the passed options.
     *
     * @param options The NeonBee options
     * @return a Future holding the related ClusterManager.
     */
    public abstract Future<ClusterManager> create(NeonBeeOptions options);
}
