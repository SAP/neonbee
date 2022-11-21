package io.neonbee.cluster;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.cluster.ClusterManagerFactory.HAZELCAST_FACTORY;
import static io.neonbee.cluster.ClusterManagerFactory.INFINISPAN_FACTORY;
import static io.neonbee.test.helper.ResourceHelper.TEST_RESOURCES;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.infinispan.manager.DefaultCacheManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.hazelcast.config.ClasspathXmlConfig;
import com.hazelcast.config.Config;
import com.hazelcast.config.FileSystemXmlConfig;

import io.neonbee.NeonBeeOptions;
import io.neonbee.NeonBeeOptions.Mutable;
import io.neonbee.test.helper.FileSystemHelper;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.ext.cluster.infinispan.InfinispanClusterManager;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;

@ExtendWith(VertxExtension.class)
class ClusterManagerFactoryTest {

    @Test
    void testGetEffectiveConfig() {
        String expectedDefaultConfig = "defaultConf.conf";

        ClusterManagerFactory cmf = new ClusterManagerFactory() {

            @Override
            public Future<ClusterManager> create(NeonBeeOptions neonBeeOptions) {
                return null;
            }

            @Override
            protected String getDefaultConfig() {
                return expectedDefaultConfig;
            }
        };

        Mutable opts = new Mutable().setClusterConfig("");
        assertThat(cmf.getEffectiveConfig(opts)).isEqualTo(expectedDefaultConfig);
        opts.setClusterConfig(null);
        assertThat(cmf.getEffectiveConfig(opts)).isEqualTo(expectedDefaultConfig);

        String otherConfig = "otherConfig.cfg";
        opts.setClusterConfig(otherConfig);
        assertThat(cmf.getEffectiveConfig(opts)).isEqualTo(otherConfig);
    }

    static Stream<Arguments> withClusterManagers() {
        Arguments hazelcast = Arguments.of(HAZELCAST_FACTORY, "hazelcast-cf.xml", HazelcastClusterManager.class);
        Arguments infinispan = Arguments.of(INFINISPAN_FACTORY, "infinispan-local.xml", InfinispanClusterManager.class);
        return Stream.of(hazelcast, infinispan);
    }

    @ParameterizedTest(name = "{index}: for {2}")
    @MethodSource("withClusterManagers")
    @DisplayName("Test ClusterManagerFactory")
    @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
    void testDefaultFactories(ClusterManagerFactory cmf, String defaultConfig, Class clusterManagerImpl, Vertx vertx,
            VertxTestContext testContext) {
        assertThat(cmf.getDefaultConfig()).isEqualTo(defaultConfig);

        cmf.create(new Mutable()).onComplete(testContext.succeeding(cm -> {
            testContext.verify(() -> assertThat(cm).isInstanceOf(clusterManagerImpl));
            // the INFINISPAN_FACTORY creates a DefaultCacheManager, which creates Threads that we have to stop!
            if (cm instanceof InfinispanClusterManager) {
                try {
                    ((DefaultCacheManager) ((InfinispanClusterManager) cm).getCacheContainer()).close();
                } catch (IOException e) {
                    testContext.failNow(e);
                }
            }
            testContext.completeNow();
        }));
    }

    static Stream<Arguments> withHazelcastConfig() throws IOException {
        File hazelcastTestFile = FileSystemHelper.createTempDirectory().resolve("test-hazelcast.xml").toFile();
        File file = TEST_RESOURCES.resolve("hazelcast-localtcp.xml").toFile();
        FileUtils.copyFile(file, hazelcastTestFile);

        Mutable options = new Mutable();
        options.setClustered(true).setClusterConfig(hazelcastTestFile.getPath());

        Arguments fileSystemFile = Arguments.of(hazelcastTestFile.getPath(), FileSystemXmlConfig.class, options);
        Arguments classpathFile = Arguments.of("hazelcast-cf.xml", ClasspathXmlConfig.class, new Mutable());
        return Stream.of(fileSystemFile, classpathFile);
    }

    @ParameterizedTest(name = "{index}: Test loading Hazelcast config from {0}")
    @MethodSource("withHazelcastConfig")
    @DisplayName("Hazelcast config test")
    @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
    void testHazelcastFileConfig(String file, Class<Config> config, Mutable options, VertxTestContext testContext) {
        HAZELCAST_FACTORY.create(options).onComplete(testContext.succeeding(cm -> {
            testContext.verify(() -> {
                HazelcastClusterManager hcm = (HazelcastClusterManager) cm;
                assertThat(hcm.getConfig()).isInstanceOf(config);
            });
            testContext.completeNow();
        }));
    }
}
