package io.neonbee.internal.tracking;

import io.neonbee.data.DataContext;
import io.neonbee.logging.LoggingFacade;

/**
 * A default implementation for tracking data handling, which logs the tracking data to a log appender.
 */
public class TrackingDataLoggingStrategy implements TrackingDataHandlingStrategy {
    private static final LoggingFacade LOGGER = LoggingFacade.create();

    @Override
    public void handleOutBoundRequest(DataContext context) {
        if (LOGGER.isInfoEnabled()) {
            LOGGER.correlateWith(context).info("Send request: {}{}", System.lineSeparator(), context.pathAsString());
        }
    }

    @Override
    public void handleInBoundRequest(DataContext context) {
        LOGGER.correlateWith(context).info("Receive request: {}{}", System.lineSeparator(), context.pathAsString());
    }

    @Override
    public void handleOutBoundReply(DataContext context) {
        LOGGER.correlateWith(context).info("Send reply: {}{}", System.lineSeparator(), context.pathAsString());
    }

    @Override
    public void handleInBoundReply(DataContext context) {
        context.updateResponseTimestamp();
        LOGGER.correlateWith(context).info("Receive reply: {}{}", System.lineSeparator(), context.pathAsString());
    }
}
