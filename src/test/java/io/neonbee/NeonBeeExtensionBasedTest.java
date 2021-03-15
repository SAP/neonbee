package io.neonbee;

import static com.google.common.truth.Truth.assertThat;
import static io.vertx.core.Future.succeededFuture;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.hazelcast.core.Hazelcast;

import io.neonbee.data.DataContext;
import io.neonbee.data.DataMap;
import io.neonbee.data.DataQuery;
import io.neonbee.data.DataVerticle;
import io.neonbee.test.helper.DeploymentHelper;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.impl.VertxInternal;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(NeonBeeExtension.class)
class NeonBeeExtensionBasedTest {

    @Test
    @Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
    @DisplayName("NeonBee should start with default options / default working directory")
    void testNeonBeeDefault(@NeonBeeInstanceConfiguration(activeProfiles = NeonBeeProfile.ALL) NeonBee neonBee) {
        assertThat(neonBee).isNotNull();
        assertThat(isClustered(neonBee)).isFalse();
    }

    @Test
    @Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
    @DisplayName("NeonBee should start with NONE profile. Only system verticle should be deployed")
    void testNeonBeeWithNoneProfile(NeonBee neonBee) {
        assertThat(neonBee).isNotNull();
        assertThat(neonBee.getOptions().getActiveProfiles()).isEmpty();
        assertThat(isClustered(neonBee)).isFalse();
    }

    @Test
    @Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
    @DisplayName("Instances with the same name should return the same NeonBee instance")
    void testSameNeonBeeInstance(@NeonBeeInstanceConfiguration(instanceName = "node1") NeonBee instance1,
            @NeonBeeInstanceConfiguration(instanceName = "node1") NeonBee instance2) {
        assertThat(instance1).isSameInstanceAs(instance2);
    }

    @Test
    @Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
    @DisplayName("NeonBee should start with CORE profile and deploy CoreDataVerticle")
    void testNeonBeeWithCoreDeployment(
            @NeonBeeInstanceConfiguration(activeProfiles = NeonBeeProfile.CORE) NeonBee neonBee,
            VertxTestContext testContext) {
        assertThat(neonBee).isNotNull();
        assertThat(neonBee.getOptions().getActiveProfiles()).contains(NeonBeeProfile.CORE);
        Vertx vertx = neonBee.getVertx();
        vertx.deployVerticle(new CoreDataVerticle(), testContext.succeeding(id -> {
            assertThat(DeploymentHelper.isVerticleDeployed(vertx, CoreDataVerticle.class)).isTrue();
            testContext.completeNow();
        }));
    }

    @Test
    @Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
    @DisplayName("NeonBee should start with NONE profile and deploy CoreDataVerticle manually")
    void testNeonBeeWithNoneDeploymentAndManualDeployment(@NeonBeeInstanceConfiguration() NeonBee neonBee,
            VertxTestContext testContext) {
        assertThat(neonBee).isNotNull();
        assertThat(neonBee.getOptions().getActiveProfiles()).isEmpty();
        Vertx vertx = neonBee.getVertx();

        vertx.deployVerticle(new CoreDataVerticle(), testContext.succeeding(id -> {
            assertThat(DeploymentHelper.isVerticleDeployed(vertx, CoreDataVerticle.class)).isTrue();
            testContext.completeNow();
        }));
    }

    @Test
    @Timeout(value = 20, timeUnit = TimeUnit.SECONDS)
    @DisplayName("3 NeonBee instances with WEB, CORE and STABLE profiles should be started and join one cluster.")
    void testNeonBeeWithClusters(
            @NeonBeeInstanceConfiguration(activeProfiles = NeonBeeProfile.WEB, clustered = true) NeonBee web,
            @NeonBeeInstanceConfiguration(activeProfiles = NeonBeeProfile.CORE, clustered = true) NeonBee core,
            @NeonBeeInstanceConfiguration(activeProfiles = NeonBeeProfile.STABLE, clustered = true) NeonBee stable) {
        assertThat(web).isNotNull();
        assertThat(web.getOptions().getActiveProfiles()).contains(NeonBeeProfile.WEB);
        assertThat(web.getVertx().isClustered()).isTrue();
        assertThat(isClustered(web)).isTrue();

        assertThat(core).isNotNull();
        assertThat(core.getOptions().getActiveProfiles()).contains(NeonBeeProfile.CORE);
        assertThat(core.getVertx().isClustered()).isTrue();
        assertThat(isClustered(core)).isTrue();

        assertThat(stable).isNotNull();
        assertThat(stable.getOptions().getActiveProfiles()).contains(NeonBeeProfile.STABLE);
        assertThat(stable.getVertx().isClustered()).isTrue();
        assertThat(isClustered(stable)).isTrue();

        assertThat(Hazelcast.getAllHazelcastInstances().size()).isAtLeast(3);
    }

    @NeonBeeDeployable(profile = NeonBeeProfile.CORE)
    public static class CoreDataVerticle extends DataVerticle<String> {
        private static final String NAME = "CoreDataVerticle";

        @Override
        public String getName() {
            return NAME;
        }

        @Override
        public Future<String> retrieveData(DataQuery query, DataMap require, DataContext context) {
            return succeededFuture("CoreDataResponse");
        }
    }

    private boolean isClustered(NeonBee neonBee) {
        return ((VertxInternal) neonBee.getVertx()).getClusterManager() != null;
    }
}
