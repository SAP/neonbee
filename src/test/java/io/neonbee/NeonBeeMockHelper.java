package io.neonbee;

import static io.vertx.core.Future.succeededFuture;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.neonbee.config.NeonBeeConfig;
import io.neonbee.test.helper.OptionsHelper;
import io.neonbee.test.helper.ReflectionHelper;
import io.vertx.core.Future;
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
    public static Vertx defaultVertxMock() {
        Vertx vertxMock = mock(Vertx.class);
        FileSystem fileSystemMock = mock(FileSystem.class);
        doAnswer(invocation -> fileSystemMock).when(vertxMock).fileSystem();

        // TODO: The result of readFileBlocking is always a string with an empty JSON object.
        // This will be evaluated to an empty object for both JSON and YAML object. This is useful
        // e.g. when the NeonBeeConfig object is initialized. If other strings are needed, a more
        // sophisticated implementation of this answer is needed.
        doAnswer(invocation -> Buffer.buffer("{}")).when(fileSystemMock).readFileBlocking(any());

        return vertxMock;
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
