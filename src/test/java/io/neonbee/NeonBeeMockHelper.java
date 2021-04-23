package io.neonbee;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.neonbee.config.NeonBeeConfig;
import io.neonbee.test.helper.ReflectionHelper;
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
     * Convenience method for registering a new (empty) NeonBee instance for an (existing) Vert.x mock.
     *
     * @param vertx   the Vert.x instance
     * @param options the NeonBee options
     * @return the mocked NeonBee instance
     */
    @SuppressWarnings({ "CatchAndPrintStackTrace", "PMD.AvoidPrintStackTrace" })
    public static NeonBee registerNeonBeeMock(Vertx vertx, NeonBeeOptions options) {
        createLogger(); // the logger is only created internally, create one manually if required

        return new NeonBee(vertx, options);
    }

    /**
     * Convenience method for registering a new (empty) NeonBee instance for an (existing) Vert.x mock.
     *
     * @param vertx   the Vert.x instance
     * @param options the NeonBee options
     * @param config  the NeonBee config
     * @return the mocked NeonBee instance
     */
    public static NeonBee registerNeonBeeMock(Vertx vertx, NeonBeeOptions options, NeonBeeConfig config) {
        createLogger(); // the logger is only created internally, create one manually if required

        NeonBee neonBee = new NeonBee(vertx, options);
        neonBee.config = config;
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
