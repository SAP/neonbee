package io.neonbee.cluster;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.test.helper.DeploymentHelper.deployVerticle;
import static io.vertx.core.Future.failedFuture;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.neonbee.NeonBee;
import io.neonbee.NeonBeeExtension;
import io.neonbee.NeonBeeInstanceConfiguration;
import io.neonbee.data.DataContext;
import io.neonbee.data.DataException;
import io.neonbee.data.DataMap;
import io.neonbee.data.DataQuery;
import io.neonbee.data.DataRequest;
import io.neonbee.data.DataVerticle;
import io.neonbee.data.internal.DataContextImpl;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxTestContext;

class DataExceptionRequestTest extends NeonBeeExtension.TestBase {
    private static final JsonObject FAILURE_OBJECT = new JsonObject().put("code", new JsonArray()
            .add(new JsonObject().put("message", "This is a bad response")).add(new JsonObject().put("lang", "en")));

    private static final Map<String, Object> FAILURE_DETAIL = Map.of("error", FAILURE_OBJECT);

    private static final DataVerticle<Buffer> DATA_EXCEPTION_VERTICLE = new DataVerticle<>() {
        @Override
        public String getName() {
            return "DataException";
        }

        @Override
        public Future<Buffer> retrieveData(DataQuery query, DataMap dataMap, DataContext context) {
            return failedFuture(new DataException(400, "Bad Response", FAILURE_DETAIL));
        }
    };

    private static final DataVerticle<Buffer> DATA_EXCEPTION_VERTICLE_ONLY_FAILURE_CODE = new DataVerticle<>() {
        @Override
        public String getName() {
            return "DataExceptionFailureCodeOnly";
        }

        @Override
        public Future<Buffer> retrieveData(DataQuery query, DataMap dataMap, DataContext context) {
            return failedFuture(new DataException(400));
        }
    };

    private static final DataVerticle<Buffer> DATA_EXCEPTION_VERTICLE_FAILURE_CODE_AND_MESSAGE = new DataVerticle<>() {
        @Override
        public String getName() {
            return "DataExceptionFailureCodeAndMessage";
        }

        @Override
        public Future<Buffer> retrieveData(DataQuery query, DataMap dataMap, DataContext context) {
            return failedFuture(new DataException(400, "Bad response"));
        }
    };

    @Test
    @Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
    @DisplayName("Test that DataException can be returned via the event bus")
    void testDataExceptionRequest(@NeonBeeInstanceConfiguration(clustered = true, activeProfiles = {}) NeonBee source,
            @NeonBeeInstanceConfiguration(clustered = true, activeProfiles = {}) NeonBee target,
            VertxTestContext testContext) {
        deployVerticle(target.getVertx(), DATA_EXCEPTION_VERTICLE).compose(s -> {
            DataRequest request = new DataRequest(DATA_EXCEPTION_VERTICLE.getName());
            return DataVerticle.<JsonObject>requestData(source.getVertx(), request, new DataContextImpl());
        }).onComplete(testContext.failing(response -> {
            testContext.verify(() -> {
                assertThat(response).isInstanceOf(DataException.class);
                assertThat(((DataException) response).failureCode()).isEqualTo(400);
                assertThat(response.getMessage()).isEqualTo("Bad Response");
                assertThat(((DataException) response).failureDetail().get("error")).isEqualTo(FAILURE_OBJECT);
            });
            testContext.completeNow();
        }));
    }

    @Test
    @Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
    @DisplayName("Test that DataException with failure code and message be returned via the event bus")
    void testDataExceptionRequestFailureCodeAndMessage(
            @NeonBeeInstanceConfiguration(clustered = true, activeProfiles = {}) NeonBee source,
            @NeonBeeInstanceConfiguration(clustered = true, activeProfiles = {}) NeonBee target,
            VertxTestContext testContext) {
        deployVerticle(target.getVertx(), DATA_EXCEPTION_VERTICLE_FAILURE_CODE_AND_MESSAGE).compose(s -> {
            DataRequest request = new DataRequest(DATA_EXCEPTION_VERTICLE_FAILURE_CODE_AND_MESSAGE.getName());
            return DataVerticle.<JsonObject>requestData(source.getVertx(), request, new DataContextImpl());
        }).onComplete(testContext.failing(response -> {
            testContext.verify(() -> {
                assertThat(response).isInstanceOf(DataException.class);
                assertThat(((DataException) response).failureCode()).isEqualTo(400);
                assertThat(response.getMessage()).isEqualTo("Bad response");
            });
            testContext.completeNow();
        }));
    }

    @Test
    @Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
    @DisplayName("Test that DataException with failure code be returned via the event bus")
    void testDataExceptionRequestFailureCodeOnly(
            @NeonBeeInstanceConfiguration(clustered = true, activeProfiles = {}) NeonBee source,
            @NeonBeeInstanceConfiguration(clustered = true, activeProfiles = {}) NeonBee target,
            VertxTestContext testContext) {
        deployVerticle(target.getVertx(), DATA_EXCEPTION_VERTICLE_ONLY_FAILURE_CODE).compose(s -> {
            DataRequest request = new DataRequest(DATA_EXCEPTION_VERTICLE_ONLY_FAILURE_CODE.getName());
            return DataVerticle.<JsonObject>requestData(source.getVertx(), request, new DataContextImpl());
        }).onComplete(testContext.failing(response -> {
            testContext.verify(() -> {
                assertThat(response).isInstanceOf(DataException.class);
                assertThat(((DataException) response).failureCode()).isEqualTo(400);
                assertThat(response.getMessage()).isEqualTo(null);
                assertThat(((DataException) response).failureDetail()).isEqualTo(Map.of());
            });
            testContext.completeNow();
        }));
    }
}
