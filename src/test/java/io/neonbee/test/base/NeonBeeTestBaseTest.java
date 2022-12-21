package io.neonbee.test.base;

import static com.google.common.truth.Truth.assertThat;

import java.lang.reflect.Method;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import io.neonbee.NeonBeeOptions;
import io.neonbee.data.DataVerticle;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxTestContext;

class NeonBeeTestBaseTest extends NeonBeeTestBase {
    private static final JsonObject PRINCIPAL = new JsonObject().put("Hodor", "Hodor");

    @Override
    protected JsonObject provideUserPrincipal(TestInfo testInfo) {
        return PRINCIPAL;
    }

    @Override
    protected void adaptOptions(TestInfo testInfo, NeonBeeOptions.Mutable options) {
        if ("testAdaptOptions".equals(testInfo.getTestMethod().map(Method::getName).orElse(null))) {
            options.setInstanceName("foo-bar-instance");
        }
    }

    @Test
    @DisplayName("test that the dummy user principal is available in the DataContext")
    void testProvideUserPrincipal(VertxTestContext testContext) {
        DataVerticle<Void> dummy = createDummyDataVerticle("test/Dummy").withDynamicResponse((query, context) -> {
            testContext.verify(() -> assertThat(context.userPrincipal()).isEqualTo(PRINCIPAL));
            return null;
        });

        deployVerticle(dummy).compose(v -> createRequest(HttpMethod.GET, "/raw/test/Dummy").send())
                .onComplete(testContext.succeedingThenComplete());
    }

    @Test
    @DisplayName("test that the adapt options can be used to adapt NeonBee options")
    void testAdaptOptions() {
        assertThat(getNeonBee().getOptions().getInstanceName()).isEqualTo("foo-bar-instance");
    }

    @Test
    @DisplayName("test that the adapt options can be used to not adapt NeonBee options")
    void testNotAdaptOptions() {
        assertThat(getNeonBee().getOptions().getInstanceName()).isNotEqualTo("foo-bar-instance");
    }
}
