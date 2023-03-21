package io.neonbee;

import static io.neonbee.test.helper.OptionsHelper.defaultOptions;
import static io.neonbee.test.helper.ReflectionHelper.createObjectWithPrivateConstructor;
import static io.vertx.core.Future.succeededFuture;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.function.Supplier;

import org.mockito.stubbing.Answer;

import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.neonbee.NeonBeeInstanceConfiguration.ClusterManager;
import io.neonbee.config.NeonBeeConfig;
import io.neonbee.entity.EntityVerticle;
import io.neonbee.health.HealthCheckRegistry;
import io.neonbee.internal.registry.Registry;
import io.neonbee.internal.registry.WriteSafeRegistry;
import io.vertx.core.AsyncResult;
import io.vertx.core.Closeable;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.file.FileSystem;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.shareddata.Lock;
import io.vertx.core.shareddata.SharedData;
import io.vertx.micrometer.impl.VertxMetricsFactoryImpl;

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
        VertxInternal vertxMock = mock(VertxInternal.class);

        doNothing().when(vertxMock).addCloseHook(any(Closeable.class));

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
        ContextInternal contextMock = mock(ContextInternal.class);
        when(vertxMock.getOrCreateContext()).thenReturn(contextMock);
        when(contextMock.deploymentID()).thenReturn("any-deployment-guid");

        // mock file system
        FileSystem fileSystemMock = mock(FileSystem.class);
        when(vertxMock.fileSystem()).thenReturn(fileSystemMock);

        // TODO: The result of readFileBlocking is always a string with an empty JSON object.
        // This will be evaluated to an empty object for both JSON and YAML object. This is useful
        // e.g. when the NeonBeeConfig object is initialized. If other strings are needed, a more
        // sophisticated implementation of this answer is needed.
        when(fileSystemMock.exists(any())).thenReturn(succeededFuture(true));
        when(fileSystemMock.exists(any(), any())).thenAnswer(invocation -> {
            invocation.<Handler<AsyncResult<?>>>getArgument(invocation.getArguments().length - 1)
                    .handle(succeededFuture(true));
            return fileSystemMock;
        });
        when(fileSystemMock.existsBlocking(any())).thenReturn(true);
        Buffer dummyBuffer = Buffer.buffer("{}");
        when(fileSystemMock.readFile(any())).thenReturn(succeededFuture(dummyBuffer));
        when(fileSystemMock.readFile(any(), any())).thenAnswer(invocation -> {
            invocation.<Handler<AsyncResult<?>>>getArgument(invocation.getArguments().length - 1)
                    .handle(succeededFuture(dummyBuffer));
            return fileSystemMock;
        });
        when(fileSystemMock.readFileBlocking(any())).thenReturn(dummyBuffer);
        when(fileSystemMock.readDir(any())).thenReturn(succeededFuture(List.of()));
        when(fileSystemMock.readDir(any(), any(Handler.class))).thenAnswer(invocation -> {
            invocation.<Handler<AsyncResult<?>>>getArgument(invocation.getArguments().length - 1)
                    .handle(succeededFuture(List.of()));
            return fileSystemMock;
        });
        when(fileSystemMock.readDirBlocking(any())).thenReturn(List.of());

        // mock event bus
        EventBus eventBusMock = mock(EventBus.class);
        when(vertxMock.eventBus()).thenReturn(eventBusMock);

        // mock timers by handling them immediately, just that code in timers gets executed
        doAnswer(timerAnswer(1)).when(vertxMock).setTimer(anyLong(), any());
        doAnswer(timerAnswer(3)).when(vertxMock).setPeriodic(anyLong(), any());

        // mock execute blocking by invoking the handlers immediately
        Answer<?> executeHandlerAnswer = invocation -> {
            Promise<?> promise = Promise.promise();
            invocation.<Handler<Promise<?>>>getArgument(0).handle(promise);
            invocation.<Handler<AsyncResult<?>>>getArgument(invocation.getArguments().length - 1)
                    .handle(promise.future());
            return null;
        };
        doAnswer(executeHandlerAnswer).when(vertxMock).executeBlocking(any(), anyBoolean(), any());
        doAnswer(executeHandlerAnswer).when(vertxMock).executeBlocking(any(), any());

        Answer<?> executeFutureAnswer = invocation -> {
            Promise<?> promise = Promise.promise();
            invocation.<Handler<Promise<?>>>getArgument(0).handle(promise);
            return promise.future();
        };
        doAnswer(executeFutureAnswer).when(vertxMock).executeBlocking(any(), anyBoolean());
        doAnswer(executeFutureAnswer).when(vertxMock).executeBlocking(any());

        // mock shared data
        SharedData sharedDataMock = mock(SharedData.class);
        when(vertxMock.sharedData()).thenReturn(sharedDataMock);

        // mock local locks (and always grant them)
        when(sharedDataMock.getLocalLock(any())).thenReturn(succeededFuture(mock(Lock.class)));
        doAnswer(invocation -> {
            invocation.<Handler<AsyncResult<Lock>>>getArgument(1).handle(succeededFuture(mock(Lock.class)));
            return null;
        }).when(sharedDataMock).getLocalLock(any(), any());

        // mock global locks (and always grant them)
        when(sharedDataMock.getLock(any())).thenReturn(succeededFuture(mock(Lock.class)));
        doAnswer(invocation -> {
            invocation.<Handler<AsyncResult<Lock>>>getArgument(1).handle(succeededFuture(mock(Lock.class)));
            return null;
        }).when(sharedDataMock).getLock(any(), any());

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
        return NeonBee.create((vertxOptions) -> {
            if (vertxOptions.getMetricsOptions() != null) {
                new VertxMetricsFactoryImpl().metrics(vertxOptions);
            }
            return succeededFuture(vertx);
        }, ClusterManager.FAKE.factory(), options, null);
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
        return registerNeonBeeMock(vertx, defaultOptions());
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
        return registerNeonBeeMock(vertx, options, new NeonBeeConfig());
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
        return registerNeonBeeMock(vertx, defaultOptions(), config);
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
    @SuppressWarnings("PMD.EmptyCatchBlock")
    public static NeonBee registerNeonBeeMock(Vertx vertx, NeonBeeOptions options, NeonBeeConfig config) {
        try {
            Constructor<HealthCheckRegistry> hcrc =
                    HealthCheckRegistry.class.getDeclaredConstructor(Vertx.class, Registry.class);
            Registry<String> healthRegistry = new WriteSafeRegistry<>(vertx, HealthCheckRegistry.REGISTRY_NAME);
            Registry<String> entityRegistry = new WriteSafeRegistry<>(vertx, EntityVerticle.REGISTRY_NAME);
            HealthCheckRegistry hcr = createObjectWithPrivateConstructor(hcrc, vertx, healthRegistry);
            return new NeonBee(vertx, options, config, new CompositeMeterRegistry(), hcr, entityRegistry);
        } catch (NoSuchMethodException | InvocationTargetException | InstantiationException
                | IllegalAccessException e) {
            // Can't happen the constructor exists ..
            throw new RuntimeException(e);
        }
    }
}
