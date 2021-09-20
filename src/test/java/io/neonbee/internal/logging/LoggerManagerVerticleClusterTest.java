package io.neonbee.internal.logging;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.internal.verticle.LoggerManagerVerticle.QUERY_PARAMETER_LOCAL;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import ch.qos.logback.classic.Level;
import io.neonbee.NeonBee;
import io.neonbee.NeonBeeExtension;
import io.neonbee.NeonBeeInstanceConfiguration;
import io.neonbee.data.DataAction;
import io.neonbee.data.DataContext;
import io.neonbee.data.DataQuery;
import io.neonbee.data.DataRequest;
import io.neonbee.data.DataVerticle;
import io.neonbee.data.internal.DataContextImpl;
import io.neonbee.internal.verticle.LoggerConfiguration;
import io.neonbee.internal.verticle.LoggerManagerVerticle;
import io.neonbee.test.helper.ConcurrentHelper;
import io.neonbee.test.helper.DeploymentHelper;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(NeonBeeExtension.class)
class LoggerManagerVerticleClusterTest {

    private final DataContext dataContext = new DataContextImpl();

    @Test
    @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
    void testSetLoggerLevelInCluster(@NeonBeeInstanceConfiguration(clustered = true, activeProfiles = {}) NeonBee node1,
            @NeonBeeInstanceConfiguration(clustered = true, activeProfiles = {}) NeonBee node2,
            VertxTestContext testContext) {
        expectLogLevels(node1, "DEBUG", node2, "DEBUG", null, testContext);
    }

    @Test
    @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
    // cannot be tested currently, as setting the log level for one logger always applies to the JVM (as loggers are
    // static entities, cluster test starts no separate JVM though, so log levels are shared)
    @Disabled
    void testSetLoggerLevelOnOneClusterNodeOnly(
            @NeonBeeInstanceConfiguration(clustered = true, activeProfiles = {}) NeonBee node1,
            @NeonBeeInstanceConfiguration(clustered = true, activeProfiles = {}) NeonBee node2,
            VertxTestContext testContext) {
        expectLogLevels(node1, "DEBUG", node2, "ERROR", query -> {
            query.setParameter(QUERY_PARAMETER_LOCAL, Boolean.toString(true));
        }, testContext);
    }

    private void expectLogLevels(NeonBee node1, String expectedLogLevel1, NeonBee node2, String expectedLogLevel2,
            Consumer<DataQuery> modifyQueryToNode1, VertxTestContext testContext) {
        List<LoggerConfiguration> configList = List.of(new LoggerConfiguration("io.neonbee.internal", Level.DEBUG));

        DataQuery updateQuery = new DataQuery().setAction(DataAction.UPDATE).setBody(
                new JsonArray(configList.stream().map(LoggerConfiguration::toJson).collect(Collectors.toList()))
                        .toBuffer());
        if (modifyQueryToNode1 != null) {
            modifyQueryToNode1.accept(updateQuery);
        }
        DataRequest updateReq = new DataRequest(LoggerManagerVerticle.QUALIFIED_NAME, updateQuery).setLocalOnly(true);

        DataRequest readReq = new DataRequest(LoggerManagerVerticle.QUALIFIED_NAME,
                new DataQuery().setParameter("loggers", "io.neonbee.internal")).setLocalOnly(true);

        CompositeFuture
                .all(DeploymentHelper.deployVerticle(node1.getVertx(), new LoggerManagerVerticle()),
                        DeploymentHelper.deployVerticle(node2.getVertx(), new LoggerManagerVerticle()))
                .<JsonArray>compose(cf -> DataVerticle.requestData(node1.getVertx(), updateReq, dataContext))
                .<Void>compose(it -> ConcurrentHelper.waitFor(node1.getVertx(), 150L))
                .<CompositeFuture>compose(
                        up -> CompositeFuture.all(DataVerticle.requestData(node1.getVertx(), readReq, dataContext),
                                DataVerticle.requestData(node2.getVertx(), readReq, dataContext)))
                .compose(cf -> {
                    List<LoggerConfiguration> node1Response =
                            cf.<JsonArray>resultAt(0).stream().map(JsonObject.class::cast)
                                    .map(LoggerConfiguration::fromJson).collect(Collectors.toList());
                    List<LoggerConfiguration> node2Response =
                            cf.<JsonArray>resultAt(1).stream().map(JsonObject.class::cast)
                                    .map(LoggerConfiguration::fromJson).collect(Collectors.toList());

                    testContext.verify(() -> {
                        assertThat(node1Response).isNotEmpty();
                        Optional<String> level = node1Response.stream()
                                .filter(config -> "io.neonbee.internal".equals(config.getName())).findFirst()
                                .map(LoggerConfiguration::getConfiguredLevel).map(theLevel -> theLevel.levelStr);
                        assertThat(level.isPresent()).isTrue();
                        assertThat(level.get()).isEqualTo(expectedLogLevel1);

                        assertThat(node2Response).isNotEmpty();
                        level = node2Response.stream().filter(config -> "io.neonbee.internal".equals(config.getName()))
                                .findFirst().map(LoggerConfiguration::getConfiguredLevel)
                                .map(theLevel -> theLevel.levelStr);
                        assertThat(level.isPresent()).isTrue();
                        assertThat(level.get()).isEqualTo(expectedLogLevel2);
                    });
                    return Future.succeededFuture().<Void>mapEmpty();
                }).onComplete(testContext.succeedingThenComplete());
    }
}
