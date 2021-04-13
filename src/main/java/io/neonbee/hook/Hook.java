package io.neonbee.hook;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Flag annotation for NeonBee hook methods
 * <p>
 * NeonBee will scan for this interface during build and start-up, in order to register hook methods flagged with this
 * annotation.
 * <p>
 * Annotated methods must comply to the following method signature (ignoring the name and the result):
 * <p>
 * {@code public void hookMethod(io.neonbee.NeonBee neonbee, io.neonbee.hook.HookContext context, io.vertx.core.Promise<Void> hookPromise)}
 * <p>
 * When the processing has been completed the hook must either call Promise.complete(T) or
 * Promise.fail(java.lang.Throwable) to indicate completion.
 * <p>
 * Warning: The order in which the hook are called is non-deterministic!
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(Hooks.class)
public @interface Hook {
    /**
     * The type of this hook (startup, shutdown, etc.).
     *
     * @return the type
     */
    HookType value();
}
