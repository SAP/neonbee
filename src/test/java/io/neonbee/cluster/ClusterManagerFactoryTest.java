package io.neonbee.cluster;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.cluster.ClusterManagerFactory.HAZELCAST_FACTORY;
import static io.neonbee.cluster.ClusterManagerFactory.INFINISPAN_FACTORY;

import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.neonbee.NeonBeeOptions;
import io.neonbee.NeonBeeOptions.Mutable;
import io.vertx.core.Future;
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
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    void testDefaultFactories(ClusterManagerFactory cmf, String defaultConfig, Class clusterManagerImpl,
            VertxTestContext testContext) {
        assertThat(cmf.getDefaultConfig()).isEqualTo(defaultConfig);

        cmf.create(new Mutable()).onComplete(testContext.succeeding(cm -> {
            testContext.verify(() -> assertThat(cm).isInstanceOf(clusterManagerImpl));
            testContext.completeNow();
        }));
    }
}
