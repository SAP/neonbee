package io.neonbee.data.internal;

import static com.google.common.truth.Truth.assertThat;
import static io.vertx.core.Future.succeededFuture;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.neonbee.NeonBeeDeployable;
import io.neonbee.data.DataContext;
import io.neonbee.data.DataMap;
import io.neonbee.data.DataQuery;
import io.neonbee.data.DataRequest;
import io.neonbee.data.DataVerticle;
import io.neonbee.test.base.DataVerticleTestBase;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxTestContext;

class ResponseDataIntegrationTest extends DataVerticleTestBase {
    @Test
    @DisplayName("Check that response metadata is properly propagated")
    void testResponseDataPropagation(VertxTestContext testContext) {
        DataContext dataContext =
                new DataContextImpl("corr", "sess", "bearer", new JsonObject(), Map.of("key", "value"));
        DataRequest request = new DataRequest("Caller", new DataQuery());
        deployVerticle(new DataVerticleCallee(true)).compose(de -> deployVerticle(new DataVerticleCaller(true)))
                .compose(de -> deployVerticle(new DataVerticleIntermediary(true)))
                .compose(de -> requestData(request, dataContext))
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    assertThat(result).isEqualTo("Response from caller");
                    assertThat(dataContext.responseData().get("calleeHint")).isEqualTo("Callee");
                    assertThat(dataContext.responseData().get("intermediaryHint")).isEqualTo("Intermediary");
                    assertThat(dataContext.responseData().get("downstreamIntermediaryHint")).isEqualTo("Callee");
                    assertThat(dataContext.responseData().get("callerHint")).isEqualTo("Caller");
                    assertThat(dataContext.responseData().get("contentType")).isEqualTo("YML");
                    testContext.completeNow();
                })));
    }

    @Test
    @DisplayName("Check that response metadata should not be propagated")
    void testResponseDataNoPropagation(VertxTestContext testContext) {
        DataContext dataContext =
                new DataContextImpl("corr", "sess", "bearer", new JsonObject(), Map.of("key", "value"));
        DataRequest request = new DataRequest("Caller", new DataQuery());
        deployVerticle(new DataVerticleCallee(false)).compose(de -> deployVerticle(new DataVerticleIntermediary(false)))
                .compose(de -> deployVerticle(new DataVerticleCaller(true)))
                .compose(de -> requestData(request, dataContext))
                .onComplete(testContext.succeeding(result -> testContext.verify(() -> {
                    assertThat(result).isEqualTo("Response from caller");
                    assertThat(dataContext.responseData().get("calleeHint")).isNull();
                    assertThat(dataContext.responseData().get("intermediaryHint")).isEqualTo("Intermediary");
                    assertThat(dataContext.responseData().get("downstreamIntermediaryHint")).isEqualTo("Callee");
                    assertThat(dataContext.responseData().get("callerHint")).isEqualTo("Caller");
                    assertThat(dataContext.responseData().get("contentType")).isEqualTo("YML");

                    testContext.completeNow();
                })));
    }

    @NeonBeeDeployable
    private static class DataVerticleCallee extends DataVerticle<String> {
        public static final String NAME = "Callee";

        private final boolean propagateResponseData;

        DataVerticleCallee(boolean propagateResponseData) {
            super();
            this.propagateResponseData = propagateResponseData;
        }

        @Override
        public String getName() {
            return NAME;
        }

        @Override
        public Future<String> retrieveData(DataQuery query, DataMap require, DataContext context) {
            if (propagateResponseData) {
                context.propagateReceivedData();
            }
            context.responseData().put("calleeHint", "Callee");
            context.responseData().put("contentType", "JSON");
            return succeededFuture("Response from callee");
        }
    }

    @NeonBeeDeployable
    private static class DataVerticleIntermediary extends DataVerticle<String> {
        public static final String NAME = "Intermediary";

        private final boolean propagateResponseData;

        DataVerticleIntermediary(boolean propagateResponseData) {
            super();
            this.propagateResponseData = propagateResponseData;
        }

        @Override
        public String getName() {
            return NAME;
        }

        @Override
        public Future<Collection<DataRequest>> requireData(DataQuery query, DataContext context) {
            return succeededFuture(List.of(new DataRequest("Callee")));
        }

        @Override
        public Future<String> retrieveData(DataQuery query, DataMap require, DataContext context) {
            if (propagateResponseData) {
                context.propagateReceivedData();
            }
            context.responseData().put("intermediaryHint", "Intermediary");
            context.responseData().put("contentType", "XML");
            context.responseData().put("downstreamIntermediaryHint",
                    context.findFirstReceivedData("Callee").map(data -> data.get("calleeHint")).orElse(""));
            return succeededFuture("Response from intermediary");
        }
    }

    @NeonBeeDeployable
    private static class DataVerticleCaller extends DataVerticle<String> {
        public static final String NAME = "Caller";

        private final boolean propagateResponseData;

        DataVerticleCaller(boolean propagateResponseData) {
            super();
            this.propagateResponseData = propagateResponseData;
        }

        @Override
        public String getName() {
            return NAME;
        }

        @Override
        public Future<Collection<DataRequest>> requireData(DataQuery query, DataContext context) {
            return succeededFuture(List.of(new DataRequest("Intermediary")));
        }

        @Override
        public Future<String> retrieveData(DataQuery query, DataMap require, DataContext context) {
            if (propagateResponseData) {
                context.propagateReceivedData();
            }
            context.responseData().put("callerHint", "Caller");
            context.responseData().put("contentType", "YML");
            return succeededFuture("Response from caller");
        }
    }

}
