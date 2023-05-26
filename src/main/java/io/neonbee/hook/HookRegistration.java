package io.neonbee.hook;

import io.vertx.core.Future;

/**
 * The outcome of registering a Hook.
 */
public interface HookRegistration {
    /**
     * The hook identifier.
     *
     * @return the hook identifier
     */
    String getId();

    /**
     * The hook name.
     *
     * @return the hook name
     */
    String getName();

    /**
     * The hook type.
     *
     * @return the hook type
     */
    HookType getType();

    /**
     * Unregister the hook.
     *
     * @return A future that is succeed if the hook was successfully undeployed, otherwise a failed future.
     */
    Future<Void> unregister();
}
