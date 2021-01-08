package io.neonbee.hook.internal;

import java.util.Map;

import io.neonbee.hook.HookContext;
import io.neonbee.hook.HookType;

public final class DefaultHookContext implements HookContext {
    private final HookType hookType;

    private final Map<String, Object> params;

    private DefaultHookContext(HookType hookType, Map<String, Object> params) {
        this.hookType = hookType;
        this.params = Map.copyOf(params);
    }

    @Override
    @SuppressWarnings({ "TypeParameterUnusedInFormals", "unchecked" })
    public <T> T get(String name) {
        return (T) params.get(name);
    }

    @Override
    public HookType getHookType() {
        return hookType;
    }

    /**
     * Creates a HookContext without additional parameters.
     *
     * @param hookType the hook type for the HookContext.
     * @return a HookContext, containing the passed in HookType.
     */
    public static HookContext withoutParameters(HookType hookType) {
        return of(hookType, Map.of());
    }

    /**
     * Creates a HookContext with the additional parameters specified in the Map parameter.
     *
     * @param hookType the hook type for the HookContext.
     * @param params   the params for the HookContext.
     * @return a HookContext, containing the HookType and all the additional params.
     */
    public static HookContext of(HookType hookType, Map<String, Object> params) {
        return new DefaultHookContext(hookType, params);
    }
}
