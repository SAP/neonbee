package io.neonbee;

import static io.vertx.core.Future.succeededFuture;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.function.Supplier;

import org.mockito.stubbing.Answer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.neonbee.config.NeonBeeConfig;
import io.neonbee.test.helper.OptionsHelper;
import io.neonbee.test.helper.ReflectionHelper;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.FileSystem;

public final class NeonBeeMockHelper {
    /**
     * This helper class cannot be instantiated
     */
    private NeonBeeMockHelper() {}

    /**
     * Convenience method for retrieving a minimalist mocked NeonBee instance. The returned Vert.x instance has a logger
     * and a rudimentary mock of FileSystem
     *
     * @return a mocked Vertx object
     */
    @SuppressWarnings({ "unchecked", "ReturnValueIgnored" })
    public static Vertx defaultVertxMock() {
        Vertx vertxMock = mock(Vertx.class);

        // mock deployment and undeployment of verticles
        when(vertxMock.deployVerticle((Verticle) any())).thenReturn(succeededFuture());
        when(vertxMock.deployVerticle((Verticle) any(), (DeploymentOptions) any())).thenReturn(succeededFuture());
        when(vertxMock.deployVerticle((Class<? extends Verticle>) any(), (DeploymentOptions) any()))
                .thenReturn(succeededFuture());
        doAnswer(invocation -> {
            ((Supplier<Verticle>) invocation.getArgument(0)).get();
            return succeededFuture();
        }).when(vertxMock).deployVerticle((Supplier<Verticle>) any(), (DeploymentOptions) any());
        when(vertxMock.deployVerticle((String) any())).thenReturn(succeededFuture());
        when(vertxMock.deployVerticle((String) any(), (DeploymentOptions) any())).thenReturn(succeededFuture());
        when(vertxMock.undeploy((String) any())).thenReturn(succeededFuture());

        Answer<?> handleAnswer = invocation -> {
            invocation.<Handler<AsyncResult<?>>>getArgument(invocation.getArguments().length - 1)
                    .handle(succeededFuture());
            return null;
        };
        doAnswer(handleAnswer).when(vertxMock).deployVerticle((Verticle) any(), (Handler<AsyncResult<String>>) any());
        doAnswer(handleAnswer).when(vertxMock).deployVerticle((Verticle) any(), (DeploymentOptions) any(),
                (Handler<AsyncResult<String>>) any());
        doAnswer(handleAnswer).when(vertxMock).deployVerticle((Class<? extends Verticle>) any(),
                (DeploymentOptions) any(), (Handler<AsyncResult<String>>) any());
        doAnswer(invocation -> {
            ((Supplier<Verticle>) invocation.getArgument(0)).get();
            return handleAnswer.answer(invocation);
        }).when(vertxMock).deployVerticle((Supplier<Verticle>) any(), (DeploymentOptions) any(),
                (Handler<AsyncResult<String>>) any());
        doAnswer(handleAnswer).when(vertxMock).deployVerticle((String) any(), (Handler<AsyncResult<String>>) any());
        doAnswer(handleAnswer).when(vertxMock).deployVerticle((String) any(), (DeploymentOptions) any(),
                (Handler<AsyncResult<String>>) any());
        doAnswer(handleAnswer).when(vertxMock).undeploy((String) any(), (Handler<AsyncResult<Void>>) any());

        // mock context creation
        Context contextMock = mock(Context.class);
        when(vertxMock.getOrCreateContext()).thenReturn(contextMock);
        when(contextMock.deploymentID()).thenReturn("any-deployment-guid");

        // mock file system
        FileSystem fileSystemMock = mock(FileSystem.class);
        when(vertxMock.fileSystem()).thenReturn(fileSystemMock);

        // TODO: The result of readFileBlocking is always a string with an empty JSON object.
        // This will be evaluated to an empty object for both JSON and YAML object. This is useful
        // e.g. when the NeonBeeConfig object is initialized. If other strings are needed, a more
        // sophisticated implementation of this answer is needed.
        when(fileSystemMock.readFileBlocking(any())).thenReturn(Buffer.buffer("{}"));

        // mock timers by handling them immediately, just that code in timers gets executed
        doAnswer(timerAnswer(1)).when(vertxMock).setTimer(anyLong(), any());
        doAnswer(timerAnswer(3)).when(vertxMock).setPeriodic(anyLong(), any());

        return vertxMock;
    }

    private static Answer<?> timerAnswer(int repeat) {
        return invocation -> {
            Handler<Long> handler = invocation.getArgument(1);
            for (int attempt = 0; attempt < repeat; attempt++) {
                handler.handle(4711L);
            }
            return null;
        };
    }

    /**
     * Convenience method for creating a new NeonBee instance for an (existing) Vert.x mock or instance.
     *
     * Attention: This method actually does NOT care whether the provided Vert.x instance is actually a mock or not. In
     * case you pass a "real" Vert.x instance, NeonBee will more or less start normally with the options / config
     * provided. This includes, among other things, deployment of all system verticles.
     *
     * @param vertx the Vert.x instance
     * @return the mocked NeonBee instance
     */
    public static Future<NeonBee> createNeonBee(Vertx vertx) {
        return createNeonBee(vertx, null);
    }

    /**
     * Convenience method for creating a new NeonBee instance for an (existing) Vert.x mock or instance.
     *
     * Attention: This method actually does NOT care whether the provided Vert.x instance is actually a mock or not. In
     * case you pass a "real" Vert.x instance, NeonBee will more or less start normally with the options / config
     * provided. This includes, among other things, deployment of all system verticles.
     *
     * @param vertx   the Vert.x instance
     * @param options the NeonBee options
     * @return the mocked NeonBee instance
     */
    public static Future<NeonBee> createNeonBee(Vertx vertx, NeonBeeOptions options) {
        return NeonBee.create(() -> succeededFuture(vertx), options);
    }

    /**
     * Convenience method for registering a new (empty) NeonBee instance for an (existing) Vert.x mock or instance.
     *
     * Attention: This method actually does NOT care whether the provided Vert.x instance is actually a mock or not. In
     * case you pass a "real" Vert.x instance, NeonBee will more or less start normally with the options / config
     * provided. This includes, among other things, deployment of all system verticles.
     *
     * @param vertx the Vert.x instance
     * @return the mocked NeonBee instance
     */
    public static NeonBee registerNeonBeeMock(Vertx vertx) {
        return registerNeonBeeMock(vertx, null, null);
    }

    /**
     * Convenience method for registering a new (empty) NeonBee instance for an (existing) Vert.x mock or instance.
     *
     * Attention: This method actually does NOT care whether the provided Vert.x instance is actually a mock or not. In
     * case you pass a "real" Vert.x instance, NeonBee will more or less start normally with the options / config
     * provided. This includes, among other things, deployment of all system verticles.
     *
     * @param vertx   the Vert.x instance
     * @param options the NeonBee options
     * @return the mocked NeonBee instance
     */
    public static NeonBee registerNeonBeeMock(Vertx vertx, NeonBeeOptions options) {
        return registerNeonBeeMock(vertx, options, null);
    }

    /**
     * Convenience method for registering a new (empty) NeonBee instance for an (existing) Vert.x mock or instance.
     *
     * Attention: This method actually does NOT care whether the provided Vert.x instance is actually a mock or not. In
     * case you pass a "real" Vert.x instance, NeonBee will more or less start normally with the options / config
     * provided. This includes, among other things, deployment of all system verticles.
     *
     * @param vertx  the Vert.x instance
     * @param config the NeonBee config
     * @return the mocked NeonBee instance
     */
    public static NeonBee registerNeonBeeMock(Vertx vertx, NeonBeeConfig config) {
        return registerNeonBeeMock(vertx, null, config);
    }

    /**
     * Convenience method for registering a new (empty) NeonBee instance for an (existing) Vert.x mock or instance.
     *
     * Attention: This method actually does NOT care whether the provided Vert.x instance is actually a mock or not. In
     * case you pass a "real" Vert.x instance, NeonBee will more or less start normally with the options / config
     * provided. This includes, among other things, deployment of all system verticles.
     *
     * @param vertx   the Vert.x instance
     * @param options the NeonBee options
     * @param config  the NeonBee config
     * @return the mocked NeonBee instance
     */
    public static NeonBee registerNeonBeeMock(Vertx vertx, NeonBeeOptions options, NeonBeeConfig config) {
        createLogger(); // the logger is only created internally, create one manually if required

        NeonBee neonBee = new NeonBee(vertx, Optional.ofNullable(options).orElseGet(OptionsHelper::defaultOptions));
        if (config != null) {
            neonBee.config = config;
        }

        return neonBee;
    }

    @SuppressWarnings({ "CatchAndPrintStackTrace", "PMD.AvoidPrintStackTrace" })
    private static void createLogger() {
        try {
            Logger logger = (Logger) ReflectionHelper.getValueOfPrivateStaticField(NeonBee.class, "logger");
            logger = logger == null ? LoggerFactory.getLogger(NeonBee.class) : logger;
            ReflectionHelper.setValueOfPrivateStaticField(NeonBee.class, "logger", logger);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
