package io.neonbee.test.base;

import static com.google.common.truth.Truth.assertThat;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import io.neonbee.data.DataVerticle;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxTestContext;

public class NeonBeeTestBaseTest extends NeonBeeTestBase {
    private static final JsonObject PRINCIPAL = new JsonObject().put("Hodor", "Hodor");

    @Override
    protected JsonObject provideUserPrincipal(TestInfo testInfo) {
        return PRINCIPAL;
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.HOURS)
    @DisplayName("test that the dummy user principal is available in the DataContext")
    void testProvideUserPrincipal(VertxTestContext testContext) {
        DataVerticle<Void> dummy = createDummyDataVerticle("test/Dummy").withDynamicResponse((query, context) -> {
            testContext.verify(() -> assertThat(context.userPrincipal()).isEqualTo(PRINCIPAL));
            testContext.completeNow();
            return null;
        });

        deployVerticle(dummy).compose(v -> createRequest(HttpMethod.GET, "/raw/test/Dummy").send())
                .onComplete(testContext.succeeding(v -> {}));
    }
}
