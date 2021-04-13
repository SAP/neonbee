package io.neonbee.internal.handler;

import static com.google.common.truth.Truth.assertThat;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.neonbee.NeonBee;
import io.neonbee.data.DataException;
import io.neonbee.hook.Hook;
import io.neonbee.hook.HookContext;
import io.neonbee.hook.HookType;
import io.neonbee.test.base.DataVerticleTestBase;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxTestContext;

class HooksHandlerTest extends DataVerticleTestBase {
    private static final String CORRELATION_ID = "bliblablub";

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("Checks that HooksHandler executes hook")
    void checkHookGetsExecutedSuccess(VertxTestContext testContext) {
        Checkpoint cp = testContext.checkpoint(2);
        TestHook hook = new TestHook();
        hook.doSomething = p -> {
            cp.flag();
            p.complete();
        };

        getNeonBee().getHookRegistry().registerInstanceHooks(hook, CORRELATION_ID)
                .compose(v -> sendRequestReturnStatusCode())
                .onComplete(testContext.succeeding(statusCode -> testContext.verify(() -> {
                    assertThat(statusCode).isEqualTo(404);
                    cp.flag();
                })));
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("Checks that HooksHanlder executes hook and fail request in case of error")
    void checkHookGetsExecutedAndFailsRequestInCaseOfException(VertxTestContext testContext) {
        Checkpoint cp = testContext.checkpoint(2);
        TestHook hook = new TestHook();
        hook.doSomething = p -> {
            cp.flag();
            p.fail(new Exception("Hodor"));
        };

        getNeonBee().getHookRegistry().registerInstanceHooks(hook, CORRELATION_ID)
                .compose(v -> sendRequestReturnStatusCode())
                .onComplete(testContext.succeeding(statusCode -> testContext.verify(() -> {
                    assertThat(statusCode).isEqualTo(500);
                    cp.flag();
                })));
    }

    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("Checks that HooksHanlder executes hook and propagate error code in case of DataException")
    void checkHookGetsExecutedAndPropagateErrorCodeIfDataException(VertxTestContext testContext) {
        Checkpoint cp = testContext.checkpoint(2);
        TestHook hook = new TestHook();
        hook.doSomething = p -> {
            cp.flag();
            p.fail(new DataException(403, "Hodor"));
        };

        getNeonBee().getHookRegistry().registerInstanceHooks(hook, CORRELATION_ID)
                .compose(v -> sendRequestReturnStatusCode())
                .onComplete(testContext.succeeding(statusCode -> testContext.verify(() -> {
                    assertThat(statusCode).isEqualTo(403);
                    cp.flag();
                })));
    }

    private Future<Integer> sendRequestReturnStatusCode() {
        return Future.<HttpResponse<Buffer>>future(p -> createRequest(HttpMethod.GET, "/raw/core/Hodor").send(p))
                .map(response -> response.statusCode());
    }

    public static class TestHook {
        private Consumer<Promise<Void>> doSomething = p -> {};

        @SuppressWarnings("PMD.UnusedFormalParameter")
        @Hook(HookType.ONCE_PER_REQUEST)
        public void test(NeonBee neonBee, HookContext hookContext, Promise<Void> promise) {
            doSomething.accept(promise);
            promise.complete();
        }
    }
}
