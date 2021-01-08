package io.neonbee.hook.internal;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.UUID;

import io.neonbee.hook.HookRegistration;
import io.neonbee.hook.HookType;
import io.vertx.core.Future;

public final class DefaultHookRegistration implements HookRegistration {
    private final String id;

    private final HookType type;

    private final DefaultHookRegistry registry;

    private final Method hookMethod;

    private final Object relatedObject;

    DefaultHookRegistration(DefaultHookRegistry registry, Object relatedObject, Method hookMethod, HookType type) {
        this.registry = registry;
        this.relatedObject = relatedObject;
        this.hookMethod = hookMethod;
        this.type = type;
        this.id = UUID.randomUUID().toString();
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getName() {
        return String.format("%s::%s", relatedObject.getClass().getName(), hookMethod.getName());
    }

    @Override
    public HookType getType() {
        return type;
    }

    Method getHookMethod() {
        return hookMethod;
    }

    Object getRelatedObject() {
        return relatedObject;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DefaultHookRegistration that = (DefaultHookRegistration) o;
        return getHookMethod().equals(that.getHookMethod()) && getRelatedObject().equals(that.getRelatedObject())
                && getType() == that.getType();
    }

    @Override
    public int hashCode() {
        return Objects.hash(getHookMethod(), getRelatedObject(), getType());
    }

    @Override
    public Future<Void> unregister() {
        registry.hookRegistry.get(getType()).remove(this);
        return Future.succeededFuture();
    }
}
