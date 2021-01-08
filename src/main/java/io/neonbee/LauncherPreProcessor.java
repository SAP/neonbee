package io.neonbee;

import java.nio.file.Path;

/**
 * A pre-processor, which will be executed before NeonBee initialization.
 */
@FunctionalInterface
public interface LauncherPreProcessor {

    /**
     * Executed the launcher pre-processor.
     *
     * @param options the NeonBeeOptions to retrieve e.g.: the {@link Path} of the configuration folder.
     */
    void execute(NeonBeeOptions options);
}
