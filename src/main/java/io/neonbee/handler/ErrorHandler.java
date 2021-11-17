package io.neonbee.handler;

import io.neonbee.NeonBee;
import io.vertx.core.Future;

/**
 * The class which implements this interface MUST have a default constructor. Otherwise NeonBee can't instantiate the
 * class during bootstrap phase.
 */
@SuppressWarnings("NM_SAME_SIMPLE_NAME_AS_INTERFACE")
public interface ErrorHandler extends io.vertx.ext.web.handler.ErrorHandler {

    /**
     * The purpose of this method is to offer the possibility to also executed blocking code during the initialization
     * of the ErrorHandler. This is beneficial in case that the error template needs to be read from a database or file.
     *
     * @param neonBee The related NeonBee instance
     * @return a Future with a reference of this ErrorHandler
     */
    Future<ErrorHandler> initialize(NeonBee neonBee);
}
