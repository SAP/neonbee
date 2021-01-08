package io.neonbee.hook;

/**
 * This interface is a generic way to pass parameters to different hook. It exposes a Map-like interface for parameter
 * retrieval.
 */
@SuppressWarnings("TypeParameterUnusedInFormals")
public interface HookContext {
    /**
     * This method allows client code to access hook parameters.
     *
     * @param name the name of the parameter to get
     * @param <T>  the expected type of the parameter to be returned
     * @return the parameter, if such a mapping exists, otherwise {@code null}.
     */
    <T> T get(String name);

    /**
     * This method allows clients to check the hook type for this hook context.
     *
     * @return the event type
     */
    HookType getHookType();
}
