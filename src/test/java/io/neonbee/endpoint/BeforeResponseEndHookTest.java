package io.neonbee.endpoint;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.hook.HookType.LAST_CHUNK;
import static io.neonbee.hook.HookType.ROUTING_CONTEXT;
import static io.neonbee.test.helper.DeploymentHelper.NEONBEE_NAMESPACE;

import java.util.function.BiConsumer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.neonbee.NeonBee;
import io.neonbee.data.DataException;
import io.neonbee.data.DataVerticle;
import io.neonbee.hook.Hook;
import io.neonbee.hook.HookContext;
import io.neonbee.hook.HookType;
import io.neonbee.test.base.DataVerticleTestBase;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxTestContext;

class BeforeResponseEndHookTest extends DataVerticleTestBase {
    private static final String CORRELATION_ID = "bliblablub";

    private static final String VERTICLE_NAME = "Hodor";

    private static final Buffer RESPONSE_BODY = Buffer.buffer("Hodor");

    @Test
    @DisplayName("Checks that the BEFORE_RESPONSE_END hook is executed before the response is sent to the client")
    void checkHookGetsExecutedBeforeResponseEnd(VertxTestContext testContext) {
        Checkpoint cp1 = testContext.laxCheckpoint();
        Checkpoint cp2 = testContext.laxCheckpoint();
        TestHook hook = new TestHook();
        hook.executeBRE = (hookContext, promise) -> {
            testContext.verify(() -> {
                assertThat(hookContext.<RoutingContext>get(ROUTING_CONTEXT)).isNotNull();
                assertThat(hookContext.<Buffer>get(LAST_CHUNK)).isEqualTo(RESPONSE_BODY);
            });
            cp1.flag();
            promise.complete();
        };

        getNeonBee().getHookRegistry().registerInstanceHooks(hook, CORRELATION_ID)
                .compose(v -> deployBufferResponseVerticle())
                .compose(v -> sendRequest())
                .onComplete(testContext.succeeding(response -> testContext.verify(() -> {
                    assertThat(response.statusCode()).isEqualTo(200);
                    assertThat(response.bodyAsBuffer()).isEqualTo(RESPONSE_BODY);
                    cp2.flag();
                })));
    }

    @Test
    @DisplayName("Checks that the BEFORE_RESPONSE_END hook may add a final header to the response")
    void checkHookMayAddHeaderToResponse(VertxTestContext testContext) {
        Checkpoint cp1 = testContext.laxCheckpoint();
        Checkpoint cp2 = testContext.laxCheckpoint();
        TestHook hook = new TestHook();
        hook.executeBRE = (hookContext, promise) -> {
            hookContext.<RoutingContext>get(ROUTING_CONTEXT).response().putHeader("hodor", "hodor");
            cp1.flag();
            promise.complete();
        };

        getNeonBee().getHookRegistry().registerInstanceHooks(hook, CORRELATION_ID)
                .compose(v -> deployBufferResponseVerticle())
                .compose(v -> sendRequest())
                .onComplete(testContext.succeeding(response -> testContext.verify(() -> {
                    assertThat(response.statusCode()).isEqualTo(200);
                    assertThat(response.getHeader("hodor")).isEqualTo("hodor");
                    cp2.flag();
                })));
    }

    @Test
    @DisplayName("Checks that the BEFORE_RESPONSE_END hook receives a null last chunk for a no-content response")
    void checkHookReceivesNullLastChunkForNoContentResponse(VertxTestContext testContext) {
        Checkpoint cp1 = testContext.laxCheckpoint();
        Checkpoint cp2 = testContext.laxCheckpoint();
        TestHook hook = new TestHook();
        hook.executeBRE = (hookContext, promise) -> {
            testContext.verify(() -> assertThat(hookContext.<Buffer>get(LAST_CHUNK)).isNull());
            cp1.flag();
            promise.complete();
        };

        getNeonBee().getHookRegistry().registerInstanceHooks(hook, CORRELATION_ID)
                .compose(v -> deployVerticle(createDummyDataVerticle(NEONBEE_NAMESPACE + '/' + VERTICLE_NAME)
                        .withStaticResponse((Buffer) null)))
                .compose(v -> sendRequest())
                .onComplete(testContext.succeeding(response -> testContext.verify(() -> {
                    assertThat(response.statusCode()).isEqualTo(204);
                    cp2.flag();
                })));
    }

    @Test
    @DisplayName("Checks that the BEFORE_RESPONSE_END hook fails the request in case of error")
    void checkHookFailsRequestInCaseOfException(VertxTestContext testContext) {
        Checkpoint cp1 = testContext.laxCheckpoint();
        Checkpoint cp2 = testContext.laxCheckpoint();
        TestHook hook = new TestHook();
        hook.executeBRE = (hookContext, promise) -> {
            cp1.flag();
            promise.fail(new RuntimeException("Hodor"));
        };

        getNeonBee().getHookRegistry().registerInstanceHooks(hook, CORRELATION_ID)
                .compose(v -> deployBufferResponseVerticle())
                .compose(v -> sendRequest())
                .onComplete(testContext.succeeding(response -> testContext.verify(() -> {
                    assertThat(response.statusCode()).isEqualTo(500);
                    cp2.flag();
                })));
    }

    @Test
    @DisplayName("Checks that the BEFORE_RESPONSE_END hook propagates the error code in case of a DataException")
    void checkHookPropagatesErrorCodeIfDataException(VertxTestContext testContext) {
        Checkpoint cp1 = testContext.laxCheckpoint();
        Checkpoint cp2 = testContext.laxCheckpoint();
        TestHook hook = new TestHook();
        hook.executeBRE = (hookContext, promise) -> {
            cp1.flag();
            promise.fail(new DataException(403, "Hodor"));
        };

        getNeonBee().getHookRegistry().registerInstanceHooks(hook, CORRELATION_ID)
                .compose(v -> deployBufferResponseVerticle())
                .compose(v -> sendRequest())
                .onComplete(testContext.succeeding(response -> testContext.verify(() -> {
                    assertThat(response.statusCode()).isEqualTo(403);
                    cp2.flag();
                })));
    }

    private Future<?> deployBufferResponseVerticle() {
        DataVerticle<Buffer> dummy =
                createDummyDataVerticle(NEONBEE_NAMESPACE + '/' + VERTICLE_NAME).withStaticResponse(RESPONSE_BODY);
        return deployVerticle(dummy);
    }

    private Future<HttpResponse<Buffer>> sendRequest() {
        return createRequest(HttpMethod.GET, String.format("/raw/%s/%s", NEONBEE_NAMESPACE, VERTICLE_NAME)).send();
    }

    public static class TestHook {
        private BiConsumer<HookContext, Promise<Void>> executeBRE = (hookContext, promise) -> promise.complete();

        @SuppressWarnings("PMD.UnusedFormalParameter")
        @Hook(HookType.BEFORE_RESPONSE_END)
        public void test(NeonBee neonBee, HookContext hookContext, Promise<Void> promise) {
            executeBRE.accept(hookContext, promise);
        }
    }
}
