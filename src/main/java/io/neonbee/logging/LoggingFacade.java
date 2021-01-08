package io.neonbee.logging;

import static io.neonbee.internal.handler.CorrelationIdHandler.CORRELATION_ID;
import static java.lang.StackWalker.Option.RETAIN_CLASS_REFERENCE;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.neonbee.data.DataContext;
import io.neonbee.logging.internal.LoggingFacadeImpl;
import io.vertx.ext.web.RoutingContext;

public interface LoggingFacade {

    /**
     * Masquerades any existing logger and adds functionality to. Here is a simple example on how to use the facade:
     *
     * <code>
     * private static final LoggingFacade LOGGER = LoggingFacade.masqueradeLogger(
     *         LoggerFactory.getLogger(MethodHandles.lookup().lookupClass()));
     * </code>
     *
     * In your coding you can then correlate any log message with a given correlationId:
     *
     * <code>
     * LOGGER.correlateWith("any correlation id").debug("Hello World");
     * </code>
     *
     * Note: After the log message was written, the correlation id will be removed from the facade by the log delegate.
     * This guarantees that subsequent log messages will not mix up correlation ids, even if they are executed on a
     * different thread. This means the {@link #correlateWith(String)} method has to be called each time a log message
     * should be printed with a correlation id associated.
     *
     * @param logger an instance of {@link Logger}
     * @return a logging facade, offering ways to correlate the logged messages
     */
    static LoggingFacade masqueradeLogger(Logger logger) {
        return new LoggingFacadeImpl(logger);
    }

    /**
     * Creates a new {@link Logger} with the passed name and {@link #masqueradeLogger(Logger) masquerade} it.
     *
     * @param name The name of the new logger.
     * @return a logging facade, offering ways to correlate the logged messages
     */
    static LoggingFacade create(String name) {
        return masqueradeLogger(LoggerFactory.getLogger(name));
    }

    /**
     * Creates a new {@link Logger} with the FQN of the passed class and {@link #masqueradeLogger(Logger) masquerade}
     * it.
     *
     * @param clazz Class name of the new logger.
     * @return a logging facade, offering ways to correlate the logged messages
     */
    static LoggingFacade create(Class<?> clazz) {
        return masqueradeLogger(LoggerFactory.getLogger(clazz));
    }

    /**
     * Creates a new {@link Logger} with the FQN of the class which calls this method and
     * {@link #masqueradeLogger(Logger) masquerade} it.
     *
     * @return a logging facade, offering ways to correlate the logged messages
     */
    static LoggingFacade create() {
        StackWalker walker = StackWalker.getInstance(RETAIN_CLASS_REFERENCE);
        Class<?> callerClass = walker.walk(stackStream -> stackStream
                .filter(stackframe -> !LoggingFacade.class.equals(stackframe.getDeclaringClass())).findFirst().get()
                .getDeclaringClass());

        return create(callerClass);
    }

    /**
     * This method correlates the next call to this logger with a given correlation id
     *
     * Note: After the logger logged anything the correlation id will be removed, to not mix up correlation ids. This
     * means in case you want to log multiple messages consecutively you will have to call the correlateWith method
     * providing the correlation id again. Similarly you should never call the correlateWith method and then not
     * actually trigger a log message, as this will cause a "pollution" of the logging facade.
     *
     * @param correlationId the correlationId to associate the next log message with
     * @return the logging facade for method chaining
     */
    LoggingFacade correlateWith(String correlationId);

    /**
     * Convenience method to set the correlation id of a routing context to the logging facade.
     *
     * @see #correlateWith(String)
     * @param routingContext any routing context
     * @return the logging facade for method chaining
     */
    @SuppressWarnings("PMD.AvoidThrowingNullPointerException")
    default LoggingFacade correlateWith(RoutingContext routingContext) {
        if (routingContext == null) {
            String msg = "routingContext must not be null, otherwise no correlationId can be extracted";
            throw new NullPointerException(msg);
        }
        String correlationID = Optional.ofNullable(routingContext.get(CORRELATION_ID)).map(Object::toString).get();
        return correlateWith(correlationID);
    }

    /**
     * Convenience method to set the correlation id of a data context to the logging facade.
     *
     * @see #correlateWith(String)
     * @param context any data context
     * @return the logging facade for method chaining
     */

    default LoggingFacade correlateWith(DataContext context) {
        return correlateWith(context.correlationId());
    }

    /**
     * Returns the name of the underlying logger instance.
     *
     * @return the name
     */
    String getName();

    /**
     * Check if the logger instance is enabled for the TRACE level.
     *
     * @return true if this Logger is enabled for the TRACE level, false otherwise.
     */
    boolean isTraceEnabled();

    /**
     * Log a message at the TRACE level.
     *
     * @param msg the message accompanying the exception
     */
    default void trace(String msg) {
        trace(msg, new Object[0]);
    }

    /**
     * Log a throwable at the TRACE level with an accompanying message.
     *
     * @param msg the message accompanying the exception
     * @param t   the throwable to log
     */
    default void trace(String msg, Throwable t) {
        trace(msg, new Object[] { t });
    }

    /**
     * Log a message at the TRACE level according to the specified format and arguments. If a stack trace should be
     * attached to the log message, the related throwable must be passed as the last value in the arguments array.
     * <p>
     * The placeholder in the passed format String must be marked with "{}".
     *
     * <pre>
     * logger.trace("Call to system {} failed.", "CoolSystem");
     * </pre>
     * <p>
     *
     * @param format    the format string
     * @param arguments The arguments to fill the format String.
     */
    void trace(String format, Object... arguments);

    /**
     * Check if the logger instance is enabled for the DEBUG level.
     *
     * @return True if this Logger is enabled for the DEBUG level, false otherwise.
     */
    boolean isDebugEnabled();

    /**
     * Log a message at the DEBUG level.
     *
     * @param msg the message accompanying the exception
     */
    default void debug(String msg) {
        debug(msg, new Object[0]);
    }

    /**
     * Log a throwable at the DEBUG level with an accompanying message.
     *
     * @param msg the message accompanying the exception
     * @param t   the throwable to log
     */
    default void debug(String msg, Throwable t) {
        debug(msg, new Object[] { t });
    }

    /**
     * Log a message at the DEBUG level according to the specified format and arguments. Check
     * {@link #trace(String, Object...)} for a more detailed description.
     *
     * @param format    the format string
     * @param arguments The arguments to fill the format String.
     */
    void debug(String format, Object... arguments);

    /**
     * Check if the logger instance is enabled for the INFO level.
     *
     * @return True if this Logger is enabled for the INFO level, false otherwise.
     */
    boolean isInfoEnabled();

    /**
     * Log a message at the INFO level.
     *
     * @param msg the message accompanying the exception
     */
    default void info(String msg) {
        info(msg, new Object[0]);
    }

    /**
     * Log a throwable at the INFO level with an accompanying message.
     *
     * @param msg the message accompanying the exception
     * @param t   the throwable to log
     */
    default void info(String msg, Throwable t) {
        info(msg, new Object[] { t });
    }

    /**
     * Log a message at the INFO level according to the specified format and arguments. Check
     * {@link #trace(String, Object...)} for a more detailed description.
     *
     * @param format    the format string
     * @param arguments The arguments to fill the format String.
     */
    void info(String format, Object... arguments);

    /**
     * Check if the logger instance is enabled for the WARN level.
     *
     * @return True if this Logger is enabled for the WARN level, false otherwise.
     */
    boolean isWarnEnabled();

    /**
     * Log a message at the WARN level.
     *
     * @param msg the message accompanying the exception
     */
    default void warn(String msg) {
        warn(msg, new Object[0]);
    }

    /**
     * Log a throwable at the WARN level with an accompanying message.
     *
     * @param msg the message accompanying the exception
     * @param t   the throwable to log
     */
    default void warn(String msg, Throwable t) {
        warn(msg, new Object[] { t });
    }

    /**
     * Log a message at the WARN level according to the specified format and arguments. Check
     * {@link #trace(String, Object...)} for a more detailed description.
     *
     * @param format    the format string
     * @param arguments The arguments to fill the format String.
     */
    void warn(String format, Object... arguments);

    /**
     * Check if the logger instance is enabled for the ERROR level.
     *
     * @return True if this Logger is enabled for the ERROR level, false otherwise.
     */
    boolean isErrorEnabled();

    /**
     * Log a message at the ERROR level.
     *
     * @param msg the message accompanying the exception
     */
    default void error(String msg) {
        error(msg, new Object[0]);
    }

    /**
     * Log a throwable at the ERROR level with an accompanying message.
     *
     * @param msg the message accompanying the exception
     * @param t   the throwable to log
     */
    default void error(String msg, Throwable t) {
        error(msg, new Object[] { t });
    }

    /**
     * Log a message at the ERROR level according to the specified format and arguments. Check
     * {@link #trace(String, Object...)} for a more detailed description.
     *
     * @param format    the format string
     * @param arguments The arguments to fill the format String.
     */
    void error(String format, Object... arguments);
}
