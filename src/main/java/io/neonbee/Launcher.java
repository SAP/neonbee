package io.neonbee;

import java.util.List;
import java.util.Locale;
import java.util.ServiceLoader;

import com.google.common.annotations.VisibleForTesting;

import io.vertx.core.cli.CLI;
import io.vertx.core.cli.CLIException;
import io.vertx.core.cli.CommandLine;
import io.vertx.core.cli.Option;
import io.vertx.core.cli.annotations.CLIConfigurator;
import io.vertx.core.cli.impl.DefaultCommandLine;

public class Launcher {
    private static final Option HELP_FLAG =
            new Option().setLongName("help").setShortName("h").setDescription("Show help").setFlag(true).setHelp(true);

    @VisibleForTesting
    static final CLI INTERFACE = CLI.create(NeonBeeOptions.Mutable.class).addOption(HELP_FLAG);

    @SuppressWarnings("unused")
    private static NeonBee neonBee;

    @SuppressWarnings("checkstyle:MissingJavadocMethod")
    public static void main(String[] args) {
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
            System.out.print(builder.toString()); // NOPMD
            System.exit(0); // NOPMD
        }

        try {
            NeonBeeOptions.Mutable options = new NeonBeeOptions.Mutable();
            CLIConfigurator.inject(commandLine, options);

            ServiceLoader<LauncherPreProcessor> loader = ServiceLoader.load(LauncherPreProcessor.class);
            loader.forEach(processor -> processor.execute(options));

            NeonBee.create(options).onSuccess(neonBee -> {
                Launcher.neonBee = neonBee;
            }).onFailure(throwable -> {
                System.err.println("Failed to start NeonBee '" + throwable.getMessage() + "'"); // NOPMD
            });
        } catch (Exception e) {
            System.err.println("Error occurred during launcher pre-processing. " + e.getMessage()); // NOPMD
            System.exit(1); // NOPMD
        }
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

        /**
         * There are two possible implementations for this method:
         *
         * - Either return the command line options only, if there are any and otherwise return the single environment
         * option (if present) - Or return a concatenated list of both options
         *
         * This method favours the command line option and only returns the environment option (a list of one entry), if
         * the command line option is not set. This way it is more deterministic, meaning that if a command line option
         * was specified it always overrides the environment, same as for the {@link #getRawValueForOption(Option)}
         * method.
         */
        @Override
        public List<String> getRawValuesForOption(Option option) {
            List<String> values = super.getRawValuesForOption(option);
            if (!values.isEmpty()) {
                return values;
            }

            return hasEnvArg(option) ? List.of(getEnvArg(option)) : values;
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
            return "NEONBEE_" + option.getName().replaceAll("-", "_").toUpperCase(Locale.ROOT);
        }
    }
}
