package io.neonbee.logging.internal;

import org.slf4j.Logger;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import io.neonbee.logging.LoggingFacade;

@SuppressWarnings({ "PMD.ExcessivePublicCount", "PMD.CyclomaticComplexity", "PMD.TooManyMethods" })
public class LoggingFacadeImpl implements LoggingFacade {
    @VisibleForTesting
    static final Marker DEFAULT_MARKER = MarkerFactory.getDetachedMarker("noCorrelationIdAvailable");

    @VisibleForTesting
    static final UnsupportedOperationException UNSUPPORTED_OPERATION_EXCEPTION = new UnsupportedOperationException(
            "Using masqueraded log messages on this facade supplying an own marker is not supported");

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
        return logger.isTraceEnabled(currentMarker);
    }

    @Override
    public boolean isTraceEnabled(Marker marker) {
        throw UNSUPPORTED_OPERATION_EXCEPTION;
    }

    @Override
    public void trace(String msg) {
        logger.trace(currentMarker, msg);
    }

    @Override
    public void trace(String format, Object arg) {
        logger.trace(currentMarker, format, arg);
    }

    @Override
    public void trace(String format, Object arg1, Object arg2) {
        logger.trace(currentMarker, format, arg1, arg2);
    }

    @Override
    public void trace(String format, Object... arguments) {
        logger.trace(currentMarker, format, arguments);
    }

    @Override
    public void trace(String msg, Throwable t) {
        logger.trace(currentMarker, msg, t);
    }

    @Override
    public void trace(Marker marker, String msg) {
        throw UNSUPPORTED_OPERATION_EXCEPTION;
    }

    @Override
    public void trace(Marker marker, String format, Object arg) {
        throw UNSUPPORTED_OPERATION_EXCEPTION;
    }

    @Override
    public void trace(Marker marker, String format, Object arg1, Object arg2) {
        throw UNSUPPORTED_OPERATION_EXCEPTION;
    }

    @Override
    public void trace(Marker marker, String format, Object... argArray) {
        throw UNSUPPORTED_OPERATION_EXCEPTION;
    }

    @Override
    public void trace(Marker marker, String msg, Throwable t) {
        throw UNSUPPORTED_OPERATION_EXCEPTION;
    }

    @Override
    public boolean isDebugEnabled() {
        return logger.isDebugEnabled(currentMarker);
    }

    @Override
    public boolean isDebugEnabled(Marker marker) {
        throw UNSUPPORTED_OPERATION_EXCEPTION;
    }

    @Override
    public void debug(String msg) {
        logger.debug(currentMarker, msg);
    }

    @Override
    public void debug(String format, Object arg) {
        logger.debug(currentMarker, format, arg);
    }

    @Override
    public void debug(String format, Object arg1, Object arg2) {
        logger.debug(currentMarker, format, arg1, arg2);
    }

    @Override
    public void debug(String format, Object... arguments) {
        logger.debug(currentMarker, format, arguments);
    }

    @Override
    public void debug(String msg, Throwable t) {
        logger.debug(currentMarker, msg, t);
    }

    @Override
    public void debug(Marker marker, String msg) {
        throw UNSUPPORTED_OPERATION_EXCEPTION;
    }

    @Override
    public void debug(Marker marker, String format, Object arg) {
        throw UNSUPPORTED_OPERATION_EXCEPTION;
    }

    @Override
    public void debug(Marker marker, String format, Object arg1, Object arg2) {
        throw UNSUPPORTED_OPERATION_EXCEPTION;
    }

    @Override
    public void debug(Marker marker, String format, Object... arguments) {
        throw UNSUPPORTED_OPERATION_EXCEPTION;
    }

    @Override
    public void debug(Marker marker, String msg, Throwable t) {
        throw UNSUPPORTED_OPERATION_EXCEPTION;
    }

    @Override
    public boolean isInfoEnabled() {
        return logger.isInfoEnabled(currentMarker);
    }

    @Override
    public boolean isInfoEnabled(Marker marker) {
        throw UNSUPPORTED_OPERATION_EXCEPTION;
    }

    @Override
    public void info(String msg) {
        logger.info(currentMarker, msg);
    }

    @Override
    public void info(String format, Object arg) {
        logger.info(currentMarker, format, arg);
    }

    @Override
    public void info(String format, Object arg1, Object arg2) {
        logger.info(currentMarker, format, arg1, arg2);
    }

    @Override
    public void info(String format, Object... arguments) {
        logger.info(currentMarker, format, arguments);
    }

    @Override
    public void info(String msg, Throwable t) {
        logger.info(currentMarker, msg, t);
    }

    @Override
    public void info(Marker marker, String msg) {
        throw UNSUPPORTED_OPERATION_EXCEPTION;
    }

    @Override
    public void info(Marker marker, String format, Object arg) {
        throw UNSUPPORTED_OPERATION_EXCEPTION;
    }

    @Override
    public void info(Marker marker, String format, Object arg1, Object arg2) {
        throw UNSUPPORTED_OPERATION_EXCEPTION;
    }

    @Override
    public void info(Marker marker, String format, Object... arguments) {
        throw UNSUPPORTED_OPERATION_EXCEPTION;
    }

    @Override
    public void info(Marker marker, String msg, Throwable t) {
        throw UNSUPPORTED_OPERATION_EXCEPTION;
    }

    @Override
    public boolean isWarnEnabled() {
        return logger.isWarnEnabled(currentMarker);
    }

    @Override
    public boolean isWarnEnabled(Marker marker) {
        throw UNSUPPORTED_OPERATION_EXCEPTION;
    }

    @Override
    public void warn(String msg) {
        logger.warn(currentMarker, msg);
    }

    @Override
    public void warn(String format, Object arg) {
        logger.warn(currentMarker, format, arg);
    }

    @Override
    public void warn(String format, Object arg1, Object arg2) {
        logger.warn(currentMarker, format, arg1, arg2);
    }

    @Override
    public void warn(String format, Object... arguments) {
        logger.warn(currentMarker, format, arguments);
    }

    @Override
    public void warn(String msg, Throwable t) {
        logger.warn(currentMarker, msg, t);

    }

    @Override
    public void warn(Marker marker, String msg) {
        throw UNSUPPORTED_OPERATION_EXCEPTION;
    }

    @Override
    public void warn(Marker marker, String format, Object arg) {
        throw UNSUPPORTED_OPERATION_EXCEPTION;
    }

    @Override
    public void warn(Marker marker, String format, Object arg1, Object arg2) {
        throw UNSUPPORTED_OPERATION_EXCEPTION;
    }

    @Override
    public void warn(Marker marker, String format, Object... arguments) {
        throw UNSUPPORTED_OPERATION_EXCEPTION;
    }

    @Override
    public void warn(Marker marker, String msg, Throwable t) {
        throw UNSUPPORTED_OPERATION_EXCEPTION;
    }

    @Override
    public boolean isErrorEnabled() {
        return logger.isErrorEnabled(currentMarker);
    }

    @Override
    public boolean isErrorEnabled(Marker marker) {
        throw UNSUPPORTED_OPERATION_EXCEPTION;
    }

    @Override
    public void error(String msg) {
        logger.error(currentMarker, msg);
    }

    @Override
    public void error(String format, Object arg) {
        logger.error(currentMarker, format, arg);
    }

    @Override
    public void error(String format, Object arg1, Object arg2) {
        logger.error(currentMarker, format, arg1, arg2);
    }

    @Override
    public void error(String format, Object... arguments) {
        logger.error(currentMarker, format, arguments);
    }

    @Override
    public void error(String msg, Throwable t) {
        logger.error(currentMarker, msg, t);
    }

    @Override
    public void error(Marker marker, String msg) {
        throw UNSUPPORTED_OPERATION_EXCEPTION;
    }

    @Override
    public void error(Marker marker, String format, Object arg) {
        throw UNSUPPORTED_OPERATION_EXCEPTION;
    }

    @Override
    public void error(Marker marker, String format, Object arg1, Object arg2) {
        throw UNSUPPORTED_OPERATION_EXCEPTION;
    }

    @Override
    public void error(Marker marker, String format, Object... arguments) {
        throw UNSUPPORTED_OPERATION_EXCEPTION;
    }

    @Override
    public void error(Marker marker, String msg, Throwable t) {
        throw UNSUPPORTED_OPERATION_EXCEPTION;
    }
}
