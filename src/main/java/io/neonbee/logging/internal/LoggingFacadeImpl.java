package io.neonbee.logging.internal;

import org.slf4j.Logger;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import io.neonbee.logging.LoggingFacade;

public class LoggingFacadeImpl implements LoggingFacade {
    @VisibleForTesting
    static final Marker DEFAULT_MARKER = MarkerFactory.getDetachedMarker("noCorrelationIDAvailable");

    private final Logger logger;

    @VisibleForTesting
    Marker currentMarker = DEFAULT_MARKER;

    public LoggingFacadeImpl(Logger logger) {
        this.logger = logger;
    }

    @Override
    public LoggingFacade correlateWith(String correlationId) {
        if (Strings.isNullOrEmpty(correlationId)) {
            currentMarker = DEFAULT_MARKER;
        } else if (currentMarker == null || !correlationId.equals(currentMarker.getName())) {
            currentMarker = MarkerFactory.getDetachedMarker(correlationId);
        }
        return this;
    }

    @Override
    public String getName() {
        return logger.getName();
    }

    @Override
    public boolean isTraceEnabled() {
        return logger.isTraceEnabled();
    }

    @Override
    public void trace(String format, Object... arguments) {
        logger.trace(currentMarker, format, arguments);
    }

    @Override
    public boolean isDebugEnabled() {
        return logger.isDebugEnabled();
    }

    @Override
    public void debug(String format, Object... arguments) {
        logger.debug(currentMarker, format, arguments);
    }

    @Override
    public boolean isInfoEnabled() {
        return logger.isInfoEnabled();
    }

    @Override
    public void info(String format, Object... arguments) {
        logger.info(currentMarker, format, arguments);
    }

    @Override
    public boolean isWarnEnabled() {
        return logger.isWarnEnabled();
    }

    @Override
    public void warn(String format, Object... arguments) {
        logger.warn(currentMarker, format, arguments);
    }

    @Override
    public boolean isErrorEnabled() {
        return logger.isErrorEnabled();
    }

    @Override
    public void error(String format, Object... arguments) {
        logger.error(currentMarker, format, arguments);
    }
}
