package io.neonbee.internal.verticle;

import static ch.qos.logback.classic.Level.ERROR;
import static com.google.common.truth.Truth.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.neonbee.data.DataQuery;
import io.neonbee.data.DataRequest;
import io.neonbee.test.base.DataVerticleTestBase;
import io.vertx.core.Future;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxTestContext;

public class LoggerManagerVerticleTest extends DataVerticleTestBase {

    @BeforeEach
    void setUp(VertxTestContext testContext) {
        deployVerticle(new LoggerManagerVerticle()).onComplete(testContext.succeedingThenComplete());
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    public void testRetrieveData(VertxTestContext testContext) {
        DataRequest req = new DataRequest(LoggerManagerVerticle.QUALIFIED_NAME, new DataQuery());
        Future<LoggerConfigurations> response = requestData(req);

        assertData(response, resp -> assertThat(resp.getConfigurations()).isNotEmpty(), testContext)
                .onComplete(testContext.succeedingThenComplete());
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    public void testRetrieveDataWithQuery(VertxTestContext testContext) {
        DataRequest req = new DataRequest(LoggerManagerVerticle.QUALIFIED_NAME,
                new DataQuery().setParameter("loggers", "io.neonbee.internal"));
        Future<LoggerConfigurations> response = requestData(req);

        assertData(response, resp -> {
            assertThat(resp.getConfigurations()).hasSize(1);
            LoggerConfiguration config = resp.getConfigurations().get(0);
            assertThat(config.getName()).isEqualTo("io.neonbee.internal");
            testContext.completeNow();
        }, testContext).onComplete(testContext.succeedingThenComplete());
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    public void testRetrieveDataWithMultipleQueryValues(VertxTestContext testContext) {
        DataRequest req = new DataRequest(LoggerManagerVerticle.QUALIFIED_NAME,
                new DataQuery().setParameter("loggers", "io.neonbee.internal,io.vertx.core.file"));
        Future<LoggerConfigurations> response = requestData(req);

        assertData(response, resp -> assertThat(resp.getConfigurations()).hasSize(2), testContext)
                .onComplete(testContext.succeedingThenComplete());
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    public void testUpdateData(VertxTestContext testContext) {
        LoggerConfigurations configList =
                new LoggerConfigurations(List.of(new LoggerConfiguration("io.neonbee.internal", ERROR),
                        new LoggerConfiguration("io.vertx.core.file", ERROR)));
        DataRequest updateReq = LoggerManagerVerticle.buildChangeLoggerConfigurationRequest(configList);
        DataRequest readReq = new DataRequest(LoggerManagerVerticle.QUALIFIED_NAME, new DataQuery());

        Future<LoggerConfigurations> updateResponse = requestData(updateReq);
        Future<LoggerConfigurations> readResponse = requestData(readReq);

        updateResponse.onComplete(testContext.succeeding(v -> {
            assertData(readResponse, resp -> {
                assertThat(resp.getConfigurations()).isNotEmpty();
                Optional<String> level = resp.getConfigurations().stream()
                        .filter(config -> config.getName().equals("io.neonbee.internal")).findFirst()
                        .map(config -> config.getConfiguredLevel());
                assertThat(level.isPresent()).isTrue();
                assertThat(level.get()).isEqualTo("ERROR");

                level = resp.getConfigurations().stream()
                        .filter(config -> config.getName().equals("io.vertx.core.file")).findFirst()
                        .map(config -> config.getConfiguredLevel());
                assertThat(level.isPresent()).isTrue();
                assertThat(level.get()).isEqualTo("ERROR");

                testContext.completeNow();
            }, testContext);
        }));
    }
}
