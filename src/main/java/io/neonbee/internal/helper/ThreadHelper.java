package io.neonbee.internal.helper;

import static java.lang.StackWalker.Option.RETAIN_CLASS_REFERENCE;

import java.lang.StackWalker.StackFrame;
import java.util.concurrent.atomic.AtomicReference;

public final class ThreadHelper {
    private ThreadHelper() {}

    /**
     * Retrieves the current threads context class loader or the class loader of the class this method was called from
     * was loaded with. the fallback is the ClassLoader of the global reference to the {@link ThreadHelper} class.
     *
     * @return A class loader
     */
    @SuppressWarnings("PMD.UseProperClassLoader")
    public static ClassLoader getClassLoader() {
        ClassLoader classLoader;

        classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader != null) {
            return classLoader;
        }

        // getOwnClass being called by the ThreadHelper will return the calling class of this method
        classLoader = getOwnClass().getClassLoader();
        if (classLoader != null) {
            return classLoader;
        }

        return ThreadHelper.class.getClassLoader();
    }

    /**
     * Get the class that called the {@link ThreadHelper#getOwnClass()} method by traversing the stack.
     *
     * So in case anther class called this method, the stack trace will look similar to:
     *
     * <ul>
     * <li>{@code ThreadHelper.getOwnClass()}</li>
     * <li>{@code ClassThatCalledTheThreadHelper.someMethod()}</li>
     * </ul>
     *
     * The {@link #getOwnClass()} method will return {@code ClassThatCalledTheClass} in that case. In case you want to
     * get access to the class that called your method, use {@link #getCallingClass()} instead.
     *
     * @return the {@link Class} that called this method
     */
    public static Class<?> getOwnClass() {
        StackWalker walker = StackWalker.getInstance(RETAIN_CLASS_REFERENCE);
        return walker.walk(stackStream -> stackStream.map(StackFrame::getDeclaringClass)
                .filter(declaringClass -> !ThreadHelper.class.equals(declaringClass)).findFirst().get());
    }

    /**
     * Returns the class that called the class that called the {@link ThreadHelper#getCallingClass()} method by
     * traversing the stack.
     *
     * So in case anther class called this method, the stack trace will look similar to:
     *
     * <ul>
     * <li>{@code ThreadHelper.getCallingClass()}</li>
     * <li>{@code ClassThatCalledTheThreadHelper.someMethod()}</li>
     * <li>{@code ClassThatCalledTheThreadHelper.someOtherIntermediaryMethod()}</li>
     * <li>{@code ClassThatCalledTheClass.someOtherMethod()}</li>
     * </ul>
     *
     * The {@link #getCallingClass()} method will return {@code ClassThatCalledTheClass} in that case.
     *
     * @return the {@link Class} that called the class that called this method, or null when being called from the main
     *         method
     */
    public static Class<?> getCallingClass() {
        AtomicReference<Class<?>> callingClass = new AtomicReference<>();
        StackWalker walker = StackWalker.getInstance(RETAIN_CLASS_REFERENCE);
        return walker.walk(stackStream -> stackStream.map(StackFrame::getDeclaringClass)
                .filter(declaringClass -> !ThreadHelper.class.equals(declaringClass)
                        && !callingClass.compareAndSet(null, declaringClass)
                        && !declaringClass.equals(callingClass.get()))
                .findFirst().orElse(null));
    }
}
