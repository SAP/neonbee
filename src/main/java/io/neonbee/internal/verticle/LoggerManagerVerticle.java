package io.neonbee.internal.verticle;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.impl.StaticLoggerBinder;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;

import com.google.common.base.Strings;

import io.neonbee.NeonBeeDeployable;
import io.neonbee.data.DataAction;
import io.neonbee.data.DataContext;
import io.neonbee.data.DataMap;
import io.neonbee.data.DataQuery;
import io.neonbee.data.DataRequest;
import io.neonbee.data.DataVerticle;
import io.neonbee.internal.codec.LoggerConfigurationsMessageCodec;
import io.neonbee.logging.LoggingFacade;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageCodec;
import io.vertx.core.json.JsonArray;

/**
 * This is a system verticle, which will be deployed on each NeonBee. instance.
 *
 * This verticle is responsible for returning and updating the current logging settings of the current NeonBee.
 *
 * This verticle will be called by the broadcast verticle.
 */
@SuppressWarnings({ "PMD.MoreThanOneLogger", "PMD.AvoidInstantiatingObjectsInLoops" })
@NeonBeeDeployable(namespace = NeonBeeDeployable.NEONBEE_NAMESPACE)
public class LoggerManagerVerticle extends DataVerticle<LoggerConfigurations> {
    public static final String QUALIFIED_NAME =
            DataVerticle.createQualifiedName(NeonBeeDeployable.NEONBEE_NAMESPACE, LoggerManagerVerticle.NAME);

    private static final String NAME = "_LoggerManager";

    private static final String LOGGER_DELIMITER = ",";

    private static final String LOGGERS = "loggers";

    private static final LoggerContext LOGGER_CONTEXT =
            (LoggerContext) StaticLoggerBinder.getSingleton().getLoggerFactory();

    private static final LoggingFacade LOGGER = LoggingFacade.create();

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public MessageCodec<LoggerConfigurations, LoggerConfigurations> getMessageCodec() {
        return new LoggerConfigurationsMessageCodec();
    }

    @Override
    public Future<LoggerConfigurations> retrieveData(DataQuery query, DataMap require, DataContext context) {
        String loggersParameter = query.getParameter(LOGGERS);
        if (!Strings.isNullOrEmpty(loggersParameter)) {
            String[] loggers = loggersParameter.split(LOGGER_DELIMITER);
            return Future.succeededFuture(new LoggerConfigurations(Arrays.stream(loggers)
                    .map(LoggerManagerVerticle::getLoggerConfiguration).collect(Collectors.toList())));
        } else {
            return Future.succeededFuture(new LoggerConfigurations(getLoggerConfigs()));
        }
    }

    @Override
    public Future<LoggerConfigurations> updateData(DataQuery query, DataContext context) {
        LoggerConfigurations entries = LoggerConfigurations
                .fromJson(Optional.ofNullable(query.getBody()).map(Buffer::toJsonArray).orElse(new JsonArray()));
        entries.getConfigurations().stream().forEach(entry -> {
            LOGGER.correlateWith(context).debug("Change log level for {} to {}.", entry.getName(),
                    entry.getConfiguredLevel());
            Level level = Level.valueOf(entry.getConfiguredLevel());
            Optional.ofNullable(getLogger(entry.getName())).ifPresent(logger -> logger.setLevel(level));
        });
        return Future.succeededFuture();
    }

    /**
     * Retrieve a single logger by name.
     */
    private static Logger getLogger(String name) {
        return LOGGER_CONTEXT.getLogger(name);
    }

    /**
     * Retrieve complete logger configuration.
     */
    private static List<LoggerConfiguration> getLoggerConfigs() {
        return LOGGER_CONTEXT.getLoggerList().stream().map(LoggerManagerVerticle::getLoggerConfiguration).sorted()
                .collect(Collectors.toList());
    }

    /**
     * Retrieve a single logger configuration.
     *
     * @param logger logger, whose configuration should be returned.
     * @return the logging configuration as {@link LoggerConfiguration}
     */
    public static LoggerConfiguration getLoggerConfiguration(Logger logger) {
        return new LoggerConfiguration(logger.getName(), logger.getLevel(), logger.getEffectiveLevel());
    }

    /**
     * Retrieve a single logger configuration.
     *
     * @param loggerName the name of the logger as string
     * @return the logging configuration as {@link LoggerConfiguration}
     */
    public static LoggerConfiguration getLoggerConfiguration(String loggerName) {
        return getLoggerConfiguration(LOGGER_CONTEXT.getLogger(loggerName));
    }

    /**
     * this is a convenience method to build a log level update {@link DataRequest}, which is broadcasting request.
     *
     * @param configs new logger configurations
     * @return a pre-built {@link DataRequest}
     */
    public static DataRequest buildChangeLoggerConfigurationRequest(LoggerConfigurations configs) {
        DataQuery query = new DataQuery(DataAction.UPDATE, configs.toJson().toBuffer());
        return new DataRequest(QUALIFIED_NAME, query).setBroadcasting(true);
    }
}
