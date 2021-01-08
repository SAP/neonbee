package io.neonbee.internal.tracking;

import io.neonbee.data.DataContext;

/**
 * An interface to abstract the behavior for tracking.
 *
 * Different tracking strategies can be implemented, e.g. logging to an appender, forward data to a data sink etc.
 *
 */
@SuppressWarnings("checkstyle:MissingJavadocMethod")
public interface TrackingDataHandlingStrategy {
    void handleOutBoundRequest(DataContext context);

    void handleInBoundRequest(DataContext context);

    void handleOutBoundReply(DataContext context);

    void handleInBoundReply(DataContext context);
}
