package io.neonbee.internal.json;

import static com.fasterxml.jackson.core.StreamReadConstraints.DEFAULT_MAX_STRING_LEN;
import static io.neonbee.internal.json.ConfigurableJsonFactoryTest.SMALLER_JSON_LENGTH;
import static io.neonbee.internal.json.ConfigurableJsonFactoryTest.assertStringLengthExceptionMessage;
import static io.neonbee.internal.json.ConfigurableJsonFactoryTest.createJsonObjectWithLengthOver;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import io.neonbee.NeonBee;
import io.neonbee.NeonBeeExtension;
import io.neonbee.NeonBeeInstanceConfiguration;
import io.neonbee.data.DataContext;
import io.neonbee.data.DataQuery;
import io.neonbee.data.DataRequest;
import io.neonbee.data.DataVerticle;
import io.neonbee.data.internal.DataContextImpl;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxTestContext;

@Isolated("The maximum string size is set on the JsonCodec that is injected to Vert.x using SPI, thus it is a global setting, that may influence other tests")
class ConfigurableJsonFactoryClusterTest extends NeonBeeExtension.TestBase {
    @BeforeAll
    static void setToSmallerSize() {
        ConfigurableJsonFactory.CODEC.setMaxStringLength(SMALLER_JSON_LENGTH);
    }

    @AfterAll
    static void resetToDefault() {
        ConfigurableJsonFactory.CODEC.setMaxStringLength(DEFAULT_MAX_STRING_LEN);
    }

    @Test
    @DisplayName("Sending a JSON that on serialization will result in a JSON that exceeds the maxStringLength should raise a descriptive error message")
    void testSendDescriptiveJsonError(
            @NeonBeeInstanceConfiguration(clustered = true, activeProfiles = {}) NeonBee nb1,
            @NeonBeeInstanceConfiguration(clustered = true, activeProfiles = {}) NeonBee nb2,
            VertxTestContext testContext) {
        DataVerticle<JsonObject> testVerticle = new DataVerticle<>() {
            @Override
            public String getName() {
                return "TestVerticle";
            }

            @Override
            public Future<JsonObject> retrieveData(DataQuery query, DataContext context) {
                return Future.succeededFuture();
            }
        };

        nb2.getVertx().deployVerticle(testVerticle).map(any -> new DataRequest(testVerticle.getName(),
                new DataQuery().setBody(createJsonObjectWithLengthOver(SMALLER_JSON_LENGTH, false).toBuffer())))
                .compose(dr -> DataVerticle.requestData(nb1.getVertx(), dr, new DataContextImpl()))
                .onComplete(testContext.failing(throwable -> {
                    assertStringLengthExceptionMessage(throwable);
                    testContext.completeNow();
                }));
    }

    @Test
    @DisplayName("Using a JSON in the target verticle that exceeds the maxStringLength should raise a descriptive error message")
    void testRetrieveDescriptiveJsonError(
            @NeonBeeInstanceConfiguration(clustered = true, activeProfiles = {}) NeonBee nb1,
            @NeonBeeInstanceConfiguration(clustered = true, activeProfiles = {}) NeonBee nb2,
            VertxTestContext testContext) {
        DataVerticle<JsonObject> testVerticle = new DataVerticle<>() {
            @Override
            public String getName() {
                return "TestVerticle";
            }

            @Override
            public Future<JsonObject> retrieveData(DataQuery query, DataContext context) {
                return Future.succeededFuture(createJsonObjectWithLengthOver(SMALLER_JSON_LENGTH, true));
            }
        };

        nb2.getVertx().deployVerticle(testVerticle).map(any -> new DataRequest(testVerticle.getName()))
                .compose(dr -> DataVerticle.requestData(nb1.getVertx(), dr, new DataContextImpl()))
                .onComplete(testContext.failing(throwable -> {
                    assertStringLengthExceptionMessage(throwable);
                    testContext.completeNow();
                }));
    }

    @Test
    @DisplayName("Responding with a JSON that on serialization will result in a JSON that exceeds the maxStringLength should raise a descriptive error message")
    void testResponseDescriptiveJsonError(
            @NeonBeeInstanceConfiguration(clustered = true, activeProfiles = {}) NeonBee nb1,
            @NeonBeeInstanceConfiguration(clustered = true, activeProfiles = {}) NeonBee nb2,
            VertxTestContext testContext) {
        DataVerticle<JsonObject> testVerticle = new DataVerticle<>() {
            @Override
            public String getName() {
                return "TestVerticle";
            }

            @Override
            public Future<JsonObject> retrieveData(DataQuery query, DataContext context) {
                return Future.succeededFuture(createJsonObjectWithLengthOver(SMALLER_JSON_LENGTH, false));
            }
        };

        nb2.getVertx().deployVerticle(testVerticle).map(any -> new DataRequest(testVerticle.getName()))
                .compose(dr -> DataVerticle.requestData(nb1.getVertx(), dr, new DataContextImpl()))
                .onComplete(testContext.failing(throwable -> {
                    assertStringLengthExceptionMessage(throwable);
                    testContext.completeNow();
                }));
    }
}
