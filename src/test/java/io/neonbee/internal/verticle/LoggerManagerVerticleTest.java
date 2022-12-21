package io.neonbee.internal.verticle;

import static ch.qos.logback.classic.Level.ERROR;
import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.NeonBeeProfile.NO_WEB;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import io.neonbee.NeonBeeOptions;
import io.neonbee.data.DataAction;
import io.neonbee.data.DataQuery;
import io.neonbee.data.DataRequest;
import io.neonbee.test.base.DataVerticleTestBase;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxTestContext;

class LoggerManagerVerticleTest extends DataVerticleTestBase {
    @Override
    protected void adaptOptions(TestInfo testInfo, NeonBeeOptions.Mutable options) {
        options.addActiveProfile(NO_WEB);
    }

    @BeforeEach
    void setUp(VertxTestContext testContext) {
        deployVerticle(new LoggerManagerVerticle()).onComplete(testContext.succeedingThenComplete());
    }

    @Test
    void testRetrieveData(VertxTestContext testContext) {
        DataRequest req = new DataRequest(LoggerManagerVerticle.QUALIFIED_NAME, new DataQuery());
        Future<JsonArray> response = requestData(req);

        assertData(response, resp -> assertThat(resp).isNotEmpty(), testContext)
                .onComplete(testContext.succeedingThenComplete());
    }

    @Test
    void testRetrieveDataWithQuery(VertxTestContext testContext) {
        DataRequest req = new DataRequest(LoggerManagerVerticle.QUALIFIED_NAME,
                new DataQuery().setParameter("loggers", "io.neonbee.internal"));
        Future<JsonArray> response = requestData(req);

        assertData(response, resp -> {
            assertThat(resp).hasSize(1);
            LoggerConfiguration config = LoggerConfiguration.fromJson(resp.getJsonObject(0));
            assertThat(config.getName()).isEqualTo("io.neonbee.internal");
            testContext.completeNow();
        }, testContext).onComplete(testContext.succeedingThenComplete());
    }

    @Test
    void testRetrieveDataWithMultipleQueryValues(VertxTestContext testContext) {
        DataRequest req = new DataRequest(LoggerManagerVerticle.QUALIFIED_NAME,
                new DataQuery().setParameter("loggers", "io.neonbee.internal,io.vertx.core.file"));
        Future<JsonArray> response = requestData(req);

        assertData(response, resp -> assertThat(resp).hasSize(2), testContext)
                .onComplete(testContext.succeedingThenComplete());
    }

    @Test
    void testUpdateData(VertxTestContext testContext) {
        List<LoggerConfiguration> configList = List.of(new LoggerConfiguration("io.neonbee.internal", ERROR),
                new LoggerConfiguration("io.vertx.core.file", ERROR));
        DataRequest updateReq = new DataRequest(LoggerManagerVerticle.QUALIFIED_NAME,
                new DataQuery().setAction(DataAction.UPDATE).setBody(
                        new JsonArray(configList.stream().map(LoggerConfiguration::toJson).collect(Collectors.toList()))
                                .toBuffer()));

        requestData(updateReq).compose(updateResponse -> {
            return this.<JsonArray>requestData(new DataRequest(LoggerManagerVerticle.QUALIFIED_NAME, new DataQuery()));
        }).onComplete(testContext.<JsonArray>succeeding(resp -> {
            assertThat(resp).isNotEmpty();
            Optional<String> level = resp.stream().map(JsonObject.class::cast).map(LoggerConfiguration::fromJson)
                    .filter(config -> "io.neonbee.internal".equals(config.getName())).findFirst()
                    .map(LoggerConfiguration::getConfiguredLevel).map(theLevel -> theLevel.levelStr);
            assertThat(level.isPresent()).isTrue();
            assertThat(level.get()).isEqualTo("ERROR");

            level = resp.stream().map(JsonObject.class::cast).map(LoggerConfiguration::fromJson)
                    .filter(config -> "io.vertx.core.file".equals(config.getName())).findFirst()
                    .map(LoggerConfiguration::getConfiguredLevel).map(theLevel -> theLevel.levelStr);
            assertThat(level.isPresent()).isTrue();
            assertThat(level.get()).isEqualTo("ERROR");

            testContext.completeNow();
        }));
    }
}
