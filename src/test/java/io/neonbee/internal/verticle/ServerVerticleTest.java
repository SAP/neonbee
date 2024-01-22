package io.neonbee.internal.verticle;

import static com.google.common.truth.Truth.assertThat;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import io.neonbee.internal.handler.DefaultErrorHandler;
import io.neonbee.internal.handler.factories.RoutingHandlerFactory;
import io.neonbee.test.base.NeonBeeTestBase;
import io.neonbee.test.helper.WorkingDirectoryBuilder;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxTestContext;

class ServerVerticleTest extends NeonBeeTestBase {

    @Test
    void testMaximumInitialLineAndCookieSizes(VertxTestContext testCtx) {
        Checkpoint checkpoint = testCtx.checkpoint(4);

        // positive case, both initial line and headers have a small size
        createRequest(HttpMethod.GET, "/any404").putHeader("smallHeader", "x")
                .send(testCtx.succeeding(response -> testCtx.verify(() -> {
                    assertThat(response.statusCode()).isEqualTo(404);
                    checkpoint.flag();
                })));

        // positive edge case for the initial line, assuming we are sending a HTTP GET request, the initial line will be
        // in form of "GET /<URI> HTTP/1.1", so the minimum initial line (incl. the leading slash) takes up 14 bytes,
        // leaving the rest for the URI. By default the URI may be 4096 bytes long, that leaves 4082 bytes for the URI!
        // Note: Since the upgrade to Vert.x 4.0 we cannot send the full length of 4096 bytes, we have to send one byte
        // less. See https://github.com/eclipse-vertx/vert.x/commit/9363774e996a9549261ff2e30aa55f1e1cbe20a6
        createRequest(HttpMethod.GET, "/" + "x".repeat(4081)).send(testCtx.succeeding(response -> testCtx.verify(() -> {
            assertThat(response.statusCode()).isEqualTo(404);
            checkpoint.flag();
        })));

        // negative edge case for the initial line
        createRequest(HttpMethod.GET, "/" + "x".repeat(4083)).send(testCtx.succeeding(response -> testCtx.verify(() -> {
            assertThat(response.statusCode()).isEqualTo(414); // URI too long
            checkpoint.flag();
        })));

        // negative case for the header, all headers may not exceed 8192 bytes
        createRequest(HttpMethod.GET, "/any404").putHeader("largeHeader", "x".repeat(10000))
                .send(testCtx.succeeding(response -> testCtx.verify(() -> {
                    assertThat(response.statusCode()).isEqualTo(431);
                    checkpoint.flag();
                })));
    }

    @Test
    void testLargerMaximumInitialLineAndCookieSizesConfig(VertxTestContext testCtx) {
        Checkpoint checkpoint = testCtx.checkpoint(2);

        // by default the initial line length may only be 4096 bytes
        createRequest(HttpMethod.GET, "/" + "x".repeat(4083)).send(testCtx.succeeding(response -> testCtx.verify(() -> {
            assertThat(response.statusCode()).isEqualTo(404);
            checkpoint.flag();
        })));

        // by default the maximum header size may only be 8196 bytes
        createRequest(HttpMethod.GET, "/any404").putHeader("largeHeader", "x".repeat(10000))
                .send(testCtx.succeeding(response -> testCtx.verify(() -> {
                    assertThat(response.statusCode()).isEqualTo(404);
                    checkpoint.flag();
                })));
    }

    @Test
    void testGetErrorHandlerDefault(Vertx vertx, VertxTestContext testCtx) throws Exception {
        Checkpoint cp = testCtx.checkpoint(2);

        ServerVerticle.createErrorHandler(null, vertx).onComplete(testCtx.succeeding(clazz -> testCtx.verify(() -> {
            assertThat(clazz).isInstanceOf(DefaultErrorHandler.class);
            cp.flag();
        })));

        ServerVerticle.createErrorHandler("Hugo", vertx).onComplete(testCtx.failing(t -> testCtx.verify(() -> {
            assertThat(t).isInstanceOf(ClassNotFoundException.class);
            assertThat(t).hasMessageThat().contains("Hugo");
            cp.flag();
        })));
    }

    @Override
    protected WorkingDirectoryBuilder provideWorkingDirectoryBuilder(TestInfo testInfo, VertxTestContext testContext) {
        if ("testLargerMaximumInitialLineAndCookieSizesConfig"
                .equals(testInfo.getTestMethod().map(Method::getName).orElse(null))) {
            return WorkingDirectoryBuilder.standard().setCustomTask(root -> {
                Path serverVerticleConfigPath = root.resolve(WorkingDirectoryBuilder.CONFIG_DIR)
                        .resolve(ServerVerticle.class.getName() + ".json");
                JsonObject config = new JsonObject().put("config",
                        new JsonObject().put("maxHeaderSize", 32768).put("maxInitialLineLength", 8192));
                testContext.verify(() -> {
                    Buffer oldConfig = Buffer.buffer(Files.readAllBytes(serverVerticleConfigPath));
                    Buffer newConfig = oldConfig.toJsonObject().mergeIn(config, true).toBuffer();
                    Files.write(serverVerticleConfigPath, newConfig.getBytes());
                });
            });
        } else {
            return super.provideWorkingDirectoryBuilder(testInfo, testContext);
        }
    }

    @Test
    void testInstantiateHandler(VertxTestContext testContext) {
        ServerVerticle.instantiateHandler(TestRoutingHandlerFactory.class.getName())
                .onComplete(testContext.succeeding(clazz -> testContext.verify(() -> {
                    assertThat(clazz).isInstanceOf(Handler.class);
                    testContext.completeNow();
                })));
    }

    @Test
    void testFailingInstantiateHandler(VertxTestContext testContext) {
        ServerVerticle.instantiateHandler(TestFailingRoutingHandlerFactory.class.getName())
                .onComplete(testContext.failing(throwable -> testContext.verify(() -> {
                    assertThat(throwable).hasMessageThat()
                            .isEqualTo(TestFailingRoutingHandlerFactory.EXCEPTION_MESSAGE);
                    testContext.completeNow();
                })));
    }

    @Test
    void testExptionInstantiateHandler(VertxTestContext testContext) {
        ServerVerticle.instantiateHandler(TestThrowingRoutingHandlerFactory.class.getName())
                .onComplete(testContext.failing(throwable -> testContext.verify(() -> {
                    assertThat(throwable).isInstanceOf(IllegalStateException.class);
                    assertThat(throwable).hasMessageThat()
                            .isEqualTo(TestThrowingRoutingHandlerFactory.EXCEPTION_MESSAGE);
                    testContext.completeNow();
                })));
    }

    public static class TestRoutingHandlerFactory implements RoutingHandlerFactory {

        @Override
        public Future<Handler<RoutingContext>> createHandler() {
            return Future.succeededFuture(event -> {});
        }
    }

    public static class TestFailingRoutingHandlerFactory implements RoutingHandlerFactory {

        public static final String EXCEPTION_MESSAGE = "test failing createHandler.";

        @Override
        public Future<Handler<RoutingContext>> createHandler() {
            return Future.failedFuture(EXCEPTION_MESSAGE);
        }
    }

    public static class TestThrowingRoutingHandlerFactory implements RoutingHandlerFactory {

        public static final String EXCEPTION_MESSAGE = "Test exception thrown in createHandler.";

        @Override
        public Future<Handler<RoutingContext>> createHandler() {
            throw new IllegalStateException(EXCEPTION_MESSAGE);
        }
    }
}
