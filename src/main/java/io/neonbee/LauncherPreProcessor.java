package io.neonbee;

import java.nio.file.Path;

import io.vertx.core.Promise;
import io.vertx.core.Vertx;

/**
 * A pre-processor, which will be executed before NeonBee initialization.
 */
@FunctionalInterface
public interface LauncherPreProcessor {

    /**
     * Execute the launcher pre-processor.
     *
     * @param options the NeonBeeOptions to retrieve e.g.: the {@link Path} of the configuration folder.
     */
    void execute(NeonBeeOptions options);

    /**
     * Execute the launcher pre-processor.
     *
     * @param vertx   {@link Vertx} instance
     * @param options the NeonBeeOptions to retrieve e.g.: the {@link Path} of the configuration folder.
     * @param promise a promise which should be called when the LauncherPreProcessor is complete.
     */
    default void execute(Vertx vertx, NeonBeeOptions options, Promise<Void> promise) {
        execute(options);
        promise.complete();
    }
}
