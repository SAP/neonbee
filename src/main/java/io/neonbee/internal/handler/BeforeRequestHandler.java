package io.neonbee.internal.handler;

import static io.neonbee.hook.HookType.BEFORE_REQUEST;

import io.vertx.ext.web.handler.PlatformHandler;

/**
 * The only purpose of this class is to mask the HooksHandler, which is a USER handler, as a PLATFORM handler. Because
 * the handler chain cannot start with a USER handler.
 */
public class BeforeRequestHandler extends HooksHandler implements PlatformHandler {
    /**
     * Creates a new BeforeRequestHandler that serves as execution point for BEFORE_REQUEST hooks.
     */
    public BeforeRequestHandler() {
        super(BEFORE_REQUEST);
    }
}
