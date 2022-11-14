package io.neonbee.cluster;

import static com.google.common.base.Strings.isNullOrEmpty;
import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static java.lang.ClassLoader.getSystemClassLoader;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import org.infinispan.manager.DefaultCacheManager;

import com.google.common.annotations.VisibleForTesting;
import com.hazelcast.config.ClasspathXmlConfig;
import com.hazelcast.config.Config;
import com.hazelcast.config.FileSystemXmlConfig;

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

            return Future.future(promise -> {
                Config config;
                if (exitsInClasspath(effectiveConfig)) {
                    config = new ClasspathXmlConfig(getSystemClassLoader(), effectiveConfig, System.getProperties());
                } else {
                    try {
                        config = new FileSystemXmlConfig(effectiveConfig, System.getProperties());
                    } catch (FileNotFoundException e) {
                        throw new RuntimeException(e);
                    }
                }

                promise.complete(new HazelcastClusterManager(config));
            });
        }

        /**
         * check if the file exists on the classpath.
         *
         * @param effectiveConfigFileName the file name
         * @return true if the file exists on the classpath, otherwise flase
         */
        private boolean exitsInClasspath(String effectiveConfigFileName) {
            try (InputStream inputStream = getSystemClassLoader().getResourceAsStream(effectiveConfigFileName)) {
                return inputStream != null;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
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
                return succeededFuture(
                        new InfinispanClusterManager(new SelfStoppingDefaultCacheManager(effectiveConfig, true)));
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

    /**
     * A default cache manager that utilizes the internal logic of the Vert.x {@link InfinispanClusterManager}
     * implementation and that stops itself after getting signaled (by the removal of the clusterViewListener) that it
     * should stop itself.
     */
    @VisibleForTesting
    static class SelfStoppingDefaultCacheManager extends DefaultCacheManager {
        private Object clusterViewListener;

        /**
         * @see DefaultCacheManager#DefaultCacheManager(String, boolean)
         * @param configurationFile name of configuration file to use as a template for all caches created
         * @param start             if true, the cache manager is started
         * @throws java.io.IOException if there is a problem with the configuration file.
         */
        SelfStoppingDefaultCacheManager(String configurationFile, boolean start) throws IOException {
            super(configurationFile, start);
        }

        @Override
        public void addListener(Object listener) {
            // the ClusterViewListener is a class enclosed in the InfinispanClusterManager, because this is our own
            // DefaultCacheManager, we will need to close it again, as soon as Vert.x shuts down / leaves the cluster.
            // we utilize that in the InfinispanClusterManager implementation the clusterViewListener is removed just
            // before the cache manager should be stopped, so we can do the same in reverse in the removeListener method
            if (clusterViewListener == null
                    && InfinispanClusterManager.class.equals(listener.getClass().getEnclosingClass())) {
                clusterViewListener = listener;
            }

            super.addListener(listener);
        }

        @Override
        public void removeListener(Object listener) {
            super.removeListener(listener);

            // see comment in addListener, this is similar to what Vert.x does in the InfinispanClusterManager impl.
            if (listener == clusterViewListener) { // NOPMD, we want to compare by reference
                stop();
            }
        }
    }
}
