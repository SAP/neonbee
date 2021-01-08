package io.neonbee.hook;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Container annotation type for multiple Hooks bound to the same method (e.g. startup and shutdown being handled be the
 * same method)
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Hooks {
    /**
     * The {@link Hook}s bound to this method.
     *
     * @return {@link Hook}s bound to this method
     */
    Hook[] value();
}
