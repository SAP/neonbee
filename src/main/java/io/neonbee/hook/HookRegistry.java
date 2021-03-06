package io.neonbee.hook;

import java.util.Collection;
import java.util.Map;

import io.neonbee.logging.LoggingFacade;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;

/**
 * This component manages NeonBee hook, exposing mechanisms for registrations and un-registration and triggering the
 * execution of hook within a given {@linkplain HookContext}.
 */
public interface HookRegistry {

    /**
     * Registers all the hook declared in every class of the collection parameter. For each of the classes, a new
     * instance will be created, and all hook-annotated methods will be registered.
     *
     * @param hookClass     the class to register hook for.
     * @param correlationId The correlationId to log
     * @return an asynchronous collection of hook registration outcomes.
     */
    default Future<Collection<HookRegistration>> registerHooks(Class<?> hookClass, String correlationId) {
        try {
            return registerInstanceHooks(hookClass.getConstructor().newInstance(), correlationId);
        } catch (Exception e) {
            LoggingFacade.create().correlateWith(correlationId)
                    .error("Could not initialize object for class {} containing hook.", hookClass.getName(), e);
            return Future.failedFuture(e);
        }
    }

    /**
     * Registers all the hook declared in the object instance passed in, using this particular instance to invoke the
     * hook.
     *
     * @param instance      the instance to register hook for.
     * @param correlationId The correlationId to log
     * @return an asynchronous collection of hook registration outcomes.
     */
    Future<Collection<HookRegistration>> registerInstanceHooks(Object instance, String correlationId);

    /**
     * Executes all hook matching the given {@code HookContext::getHookType}. Can be used for hook that don't require
     * parameters.
     *
     * @param type the HookType.
     * @return an asynchronous collection of hook execution outcomes.
     */
    default CompositeFuture executeHooks(HookType type) {
        return executeHooks(type, Map.of());
    }

    /**
     * Executes all hook matching the given {@code HookContext::getHookType}.
     *
     * @param type       the HookType.
     * @param parameters The parameters to inject into the HookContext.
     * @return an asynchronous collection of hook execution outcomes.
     */
    CompositeFuture executeHooks(HookType type, Map<String, Object> parameters);

    /**
     * Returns all the hook registrations currently present in this HookRegistry.
     *
     * @return hook registrations.
     */
    Future<Collection<HookRegistration>> getHookRegistrations();
}
