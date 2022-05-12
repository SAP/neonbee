package io.neonbee;

import static ch.qos.logback.classic.util.ContextInitializer.CONFIG_FILE_PROPERTY;
import static java.lang.System.setProperty;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

import io.neonbee.config.NeonBeeConfig;
import io.netty.util.internal.logging.InternalLoggerFactory;
import io.netty.util.internal.logging.Slf4JLoggerFactory;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.cli.CLI;
import io.vertx.core.cli.CLIException;
import io.vertx.core.cli.CommandLine;
import io.vertx.core.cli.Option;
import io.vertx.core.cli.annotations.CLIConfigurator;
import io.vertx.core.cli.impl.DefaultCommandLine;

public class Launcher {
    private static final Option HELP_FLAG =
            new Option().setLongName("help").setShortName("h").setDescription("Show help").setFlag(true).setHelp(true);

    // Attention DO NOT create a static LOGGER instance here! NeonBee needs to start up first, in order to set the right
    // logging parameterization, like the logging configuration, and the internal loggers for Netty.
    private static final String HAZELCAST_LOGGING_TYPE = "hazelcast.logging.type";

    private static final String LOG_DIR_PROPERTY = "LOG_DIR";

    @VisibleForTesting
    static final CLI INTERFACE = CLI.create(NeonBeeOptions.Mutable.class).addOption(HELP_FLAG);

    @SuppressWarnings("unused")
    private static NeonBee neonBee;

    @SuppressWarnings("checkstyle:MissingJavadocMethod")
    public static void main(String[] args) {
        CommandLine commandLine = commandLine(args);

        NeonBeeOptions.Mutable options = createNeonBeeOptions(commandLine);

        startNeonBee(options);
    }

    /**
     * Configure and start NeonBee.
     *
     * @param options NeonBeeOptions
     */
    public static void startNeonBee(NeonBeeOptions options) {
        // do not use a Logger before this!
        configureLogging(options);

        Vertx launcherVertx = Vertx.vertx();
        Future.succeededFuture().compose(unused -> NeonBeeConfig.load(launcherVertx, options.getConfigDirectory()))
                .eventually(unused -> closeVertx(launcherVertx)).compose(config -> NeonBee.create(options, config))
                .onSuccess(neonBee -> Launcher.neonBee = neonBee).onFailure(throwable -> LoggerFactory
                        .getLogger(Launcher.class).error("Failed to start NeonBee", throwable));
    }

    /**
     * Create the NeonBeeOptions from the CommandLine.
     *
     * @param commandLine the CommandLine
     * @return the NeonBeeOptions.Mutable object from the CommandLine
     */
    public static NeonBeeOptions.Mutable createNeonBeeOptions(CommandLine commandLine) {
        NeonBeeOptions.Mutable options = new NeonBeeOptions.Mutable();
        CLIConfigurator.inject(commandLine, options);
        return options;
    }

    /**
     * Create the CommandLine object from the provided arguments.
     *
     * @param args the arguments to parse
     * @return the CommandLine from the arguments
     */
    public static CommandLine commandLine(String... args) {
        CommandLine commandLine = null;
        try {
            commandLine = parseCommandLine(args);
            List<String> arguments = commandLine.allArguments();
            if (!arguments.isEmpty()) {
                throw new CLIException("Unknown option '" + arguments.get(0) + "'");
            }
        } catch (CLIException e) {
            System.err.print(e.getMessage() + ".\n\nUse --help to list all options."); // NOPMD
            System.exit(1); // NOPMD
        }

        if (commandLine.isAskingForHelp()) {
            StringBuilder builder = new StringBuilder();
            INTERFACE.usage(builder);
            System.out.print(builder); // NOPMD
            System.exit(0); // NOPMD
        }
        return commandLine;
    }

    /**
     * If anything before this method initializes a logger, this will be broken!
     *
     * Switch to the SLF4J logging facade (using Logback as a logging backend). It is required to set the logging system
     * properties before the first logger is initialized, so do it before the Vert.x initialization.
     *
     * @param options {@link NeonBeeOptions}
     */
    private static void configureLogging(NeonBeeOptions options) {
        setProperty(CONFIG_FILE_PROPERTY, options.getConfigDirectory().resolve("logback.xml").toString());
        setProperty(HAZELCAST_LOGGING_TYPE, "slf4j");
        setProperty(LOG_DIR_PROPERTY, options.getLogDirectory().toAbsolutePath().toString());
        InternalLoggerFactory.setDefaultFactory(Slf4JLoggerFactory.INSTANCE);
    }

    private static Future<Void> closeVertx(Vertx launcherVertx) {
        Promise<Void> promise = Promise.promise();
        launcherVertx.close(promise);
        return promise.future();
    }

    @VisibleForTesting
    @SuppressWarnings("PMD.UseVarargs")
    static CommandLine parseCommandLine(String[] args) {
        return new EnvironmentAwareCommandLine(INTERFACE.parse(List.of(args), true));
    }

    /**
     * An environment-aware {@link CommandLine} facade, that will, if an option is not present on the command line,
     * check for a {@code NEONBEE_} environment variable that contains the value returned for the option.
     *
     * Another change in behavior is that, if a multi-value option is present, the option will fall back to the single
     * default value, in case the option is unassigned. In the {@link DefaultCommandLine} implementation there the
     * default value will be ignored in case {@link CommandLine#getOptionValues(String)} is called.
     *
     * Does not change any of the behavior for CLI arguments.
     */
    @VisibleForTesting
    static class EnvironmentAwareCommandLine extends DefaultCommandLine {
        EnvironmentAwareCommandLine(CommandLine commandLine) {
            super(commandLine.cli());

            CLI cli = commandLine.cli();
            DefaultCommandLine defaultCommandLine = (DefaultCommandLine) commandLine;
            this.allArgs = defaultCommandLine.allArguments();
            cli.getOptions().forEach(option -> {
                this.optionValues.put(option, defaultCommandLine.getRawValuesForOption(option));
                if (defaultCommandLine.isSeenInCommandLine(option)) {
                    this.optionsSeenInCommandLine.add(option);
                }
            });
            cli.getArguments().forEach(argument -> {
                this.argumentValues.put(argument, defaultCommandLine.getRawValuesForArgument(argument));
            });
            this.valid = defaultCommandLine.isValid();
        }

        @Override
        public boolean isFlagEnabled(String name) {
            return super.isFlagEnabled(name) || hasEnvArg(cli().getOption(name));
        }

        @Override
        public boolean isSeenInCommandLine(Option option) {
            return super.isSeenInCommandLine(option) || hasEnvArg(option);
        }

        @Override
        public boolean isOptionAssigned(Option option) {
            // do not call our implementation as it will, if the option is unassigned, return the default value (if any)
            return !super.getRawValuesForOption(option).isEmpty() || hasEnvArg(option);
        }

        /**
         * There are two possible implementations for this method:
         *
         * <ul>
         * <li>Either return the command line options only, if there are any and otherwise return the single environment
         * option (if present)</li>
         * <li>Or return a concatenated list of both options</li>
         * </ul>
         *
         * This method favours the command line option and only returns the environment option (a list of one entry), if
         * the command line option is not set. This way it is more deterministic, meaning that if a command line option
         * was specified it always overrides the environment.
         *
         * Another change, compared to the {@link DefaultCommandLine} implementation is that it returns the default
         * value, in case neither a command line option, nor a environment variable was set.
         */
        @Override
        public List<String> getRawValuesForOption(Option option) {
            List<String> values = super.getRawValuesForOption(option);
            if (!values.isEmpty()) {
                return values;
            }

            if (hasEnvArg(option)) {
                return List.of(getEnvArg(option));
            }

            String value = option.getDefaultValue();
            if (value != null) {
                return List.of(value);
            }

            return Collections.emptyList();
        }

        /**
         * Returns the environment argument for a given option.
         *
         * @param option The option to return the environment argument for
         * @return The environment argument as string, or null if it is not present
         */
        @VisibleForTesting
        String getEnvArg(Option option) {
            return System.getenv(getEnvName(option));
        }

        /**
         * Checks if a given option has an environment argument set.
         *
         * @param option The option to check the environment argument for
         * @return True if there is an environment argument set for this option
         */
        @VisibleForTesting
        boolean hasEnvArg(Option option) {
            return System.getenv().containsKey(getEnvName(option));
        }

        /**
         * Converts a given option name into a environment friendly (NeonBee) name.
         *
         * @param option The option with the name to convert
         * @return The converted option name
         */
        static String getEnvName(Option option) {
            return "NEONBEE_" + option.getName().replace("-", "_").toUpperCase(Locale.ROOT);
        }
    }
}
