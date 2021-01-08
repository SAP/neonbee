package io.neonbee.internal.logging;

import static com.google.common.truth.Truth.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import ch.qos.logback.classic.Level;
import io.neonbee.NeonBee;
import io.neonbee.NeonBeeExtension;
import io.neonbee.NeonBeeInstanceConfiguration;
import io.neonbee.data.DataContext;
import io.neonbee.data.DataQuery;
import io.neonbee.data.DataRequest;
import io.neonbee.data.DataVerticle;
import io.neonbee.data.internal.DataContextImpl;
import io.neonbee.internal.verticle.LoggerConfiguration;
import io.neonbee.internal.verticle.LoggerConfigurations;
import io.neonbee.internal.verticle.LoggerManagerVerticle;
import io.neonbee.test.helper.ConcurrentHelper;
import io.neonbee.test.helper.DeploymentHelper;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(NeonBeeExtension.class)
public class LoggerManagerVerticleClusterTest {

    private final DataContext dataContext = new DataContextImpl();

    @Test
    @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
    public void testSetLogggerLevelInCluster(@NeonBeeInstanceConfiguration(clustered = true) NeonBee node1,
            @NeonBeeInstanceConfiguration(clustered = true) NeonBee node2, VertxTestContext testContext) {
        LoggerConfigurations configs =
                new LoggerConfigurations(List.of(new LoggerConfiguration("io.neonbee.internal", Level.ERROR),
                        new LoggerConfiguration("io.vertx.core.file", Level.ERROR)));
        DataRequest updateReq = LoggerManagerVerticle.buildChangeLoggerConfigurationRequest(configs);
        DataRequest readReq = new DataRequest(LoggerManagerVerticle.QUALIFIED_NAME,
                new DataQuery().setParameter("loggers", "io.neonbee.internal"));

        CompositeFuture
                .all(DeploymentHelper.deployVerticle(node1.getVertx(), new LoggerManagerVerticle()),
                        DeploymentHelper.deployVerticle(node2.getVertx(), new LoggerManagerVerticle()))
                .<JsonArray>compose(cf -> DataVerticle.requestData(node1.getVertx(), updateReq, dataContext))
                .<Void>compose(it -> ConcurrentHelper.waitFor(node1.getVertx(), 150L))
                .<CompositeFuture>compose(
                        up -> CompositeFuture.all(DataVerticle.requestData(node1.getVertx(), readReq, dataContext),
                                DataVerticle.requestData(node2.getVertx(), readReq, dataContext)))
                .compose(cf -> {
                    LoggerConfigurations node1Response = cf.resultAt(0);
                    LoggerConfigurations node2Response = cf.resultAt(1);

                    testContext.verify(() -> {
                        assertThat(node1Response.getConfigurations()).isNotEmpty();
                        Optional<String> level = node1Response.getConfigurations().stream()
                                .filter(config -> config.getName().equals("io.neonbee.internal")).findFirst()
                                .map(config -> config.getConfiguredLevel());
                        assertThat(level.isPresent()).isTrue();
                        assertThat(level.get()).isEqualTo("ERROR");

                        assertThat(node2Response.getConfigurations()).isNotEmpty();
                        level = node2Response.getConfigurations().stream()
                                .filter(config -> config.getName().equals("io.neonbee.internal")).findFirst()
                                .map(config -> config.getConfiguredLevel());
                        assertThat(level.isPresent()).isTrue();
                        assertThat(level.get()).isEqualTo("ERROR");
                    });
                    return Future.succeededFuture().<Void>mapEmpty();
                }).onComplete(testContext.succeedingThenComplete());
    }
}
