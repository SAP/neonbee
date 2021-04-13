package io.neonbee.hook;

public enum HookType {
    /**
     * The bootstrap hook is called when a new NeonBee instance was created, even before the NeonBee configuration is
     * loaded, or any configurations on the event bus have been done, or system / web verticle have been deployed.
     * <p>
     * Note: For security reasons this hook requires the module to be present in the classpath of NeonBee on startup!
     * Placing a module in the work directories /modules directory, will cause the hook NOT to be called!
     */
    BEFORE_BOOTSTRAP,

    /**
     * The startup hook is called after the NeonBee instance has been initialized successfully (all configurations where
     * loaded, system / web verticle have been deployed, the event bus was configured).
     */
    AFTER_STARTUP,

    /**
     * This hook is called once for each web request before it is dispatched to the appropriate endpoint for handling.
     * <p>
     * Note: This hook is intended for the implementation of cross-cutting concerns (for example logging or global
     * authorization checks). In case this hook handles the provided {@linkplain #ROUTING_CONTEXT} in the
     * {@linkplain HookContext} it may not call the {@code hookPromise}, to break the chain.
     * <p>
     * Available parameters in the {@linkplain HookContext}:
     * <p>
     * {@link #ROUTING_CONTEXT}: {@linkplain io.vertx.ext.web.RoutingContext}
     */
    ONCE_PER_REQUEST,

    /**
     * The shutdown hook is called before the associated Vert.x instance to a NeonBee object is closed / shut down.
     */
    BEFORE_SHUTDOWN;

    /**
     * Name for the routing context parameter of HookType {@link #ONCE_PER_REQUEST}.
     */
    public static final String ROUTING_CONTEXT = "routingContext";
}
