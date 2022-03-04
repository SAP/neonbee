package io.neonbee.test.helper;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;

import io.neonbee.NeonBeeInstanceConfiguration;
import io.neonbee.NeonBeeOptions;
import io.neonbee.NeonBeeProfile;

public final class OptionsHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger(OptionsHelper.class);

    private static NeonBeeInstanceConfiguration defaultInstanceConfiguration() {
        return internalDefaultInstanceConfiguration(null);
    }

    // use a trick here to create a new NeonBeeInstanceConfiguration and use it in the method to retrieve the defaults
    @SuppressWarnings({ "UnusedVariable", "PMD.UnusedFormalParameter" })
    private static NeonBeeInstanceConfiguration internalDefaultInstanceConfiguration(
            @NeonBeeInstanceConfiguration Void nothing) {
        try {
            return OptionsHelper.class.getDeclaredMethod("internalDefaultInstanceConfiguration", Void.class)
                    .getParameters()[0].getAnnotation(NeonBeeInstanceConfiguration.class);
        } catch (NoSuchMethodException | SecurityException e) {
            throw new RuntimeException(e);
        }
    }

    public static NeonBeeOptions.Mutable defaultOptions() {
        return options(Optional.empty());
    }

    public static NeonBeeOptions.Mutable options(Optional<NeonBeeInstanceConfiguration> config) {
        return options(config.orElseGet(OptionsHelper::defaultInstanceConfiguration));
    }

    public static NeonBeeOptions.Mutable options(NeonBeeInstanceConfiguration config) {
        int port;
        try {
            port = SystemHelper.getFreePort();
        } catch (IOException e) {
            LOGGER.error("Error while finding a free port for server verticle.", e);
            throw new RuntimeException(e);
        }

        NeonBeeOptions.Mutable options = new NeonBeeOptions.Mutable();

        options.setActiveProfiles(Arrays.<NeonBeeProfile>asList(config.activeProfiles()))
                .setClusterConfigResource(config.clusterConfigFile()).setClustered(config.clustered())
                .setClusterPort(config.clusterPort()).setDisableJobScheduling(config.disableJobScheduling())
                .setDoNotWatchFiles(config.doNotWatchFiles()).setEventLoopPoolSize(config.eventLoopPoolSize())
                .setIgnoreClassPath(config.ignoreClassPath()).setServerPort(port)
                .setWorkerPoolSize(config.workerPoolSize())
                .setWorkingDirectory(Paths.get(config.workingDirectoryPath()));
        if (!Strings.isNullOrEmpty(config.instanceName())) {
            options.setInstanceName(config.instanceName());
        }

        return options;
    }
}
