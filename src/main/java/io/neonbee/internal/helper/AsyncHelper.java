package io.neonbee.internal.helper;

import java.util.List;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;

/**
 * A helper that simplifies the integration of asynchronous code like working with {@linkplain Future} and
 * {@linkplain Handler} and integrating non-asynchronous code into an asynchronous operations.
 */
public final class AsyncHelper {
    /**
     * This helper class cannot be instantiated.
     */
    private AsyncHelper() {}

    /**
     * Type safe CompositeFuture future (as soon as Vert.x applies this interface signature, switch to
     * {@link CompositeFuture#all(List)} again).
     *
     * @param futures The futures to be checked for completeness
     * @return A {@link CompositeFuture}, succeeded if all the passed futures succeeded, failing otherwise.
     * @deprecated Vert.x now supports Lists containing typed Futures
     */
    @Deprecated(forRemoval = true)
    public static CompositeFuture allComposite(List<? extends Future<?>> futures) {
        return Future.all(futures);
    }

    /**
     * Type safe CompositeFuture future (as soon as Vert.x applies this interface signature, switch to
     * {@link CompositeFuture#join(List)} again).
     *
     * @param futures A list of futures to be converted
     * @return A {@link CompositeFuture} containing the passed futures
     * @deprecated Vert.x now supports Lists containing typed Futures
     */
    @Deprecated(forRemoval = true)
    public static CompositeFuture joinComposite(List<? extends Future<?>> futures) {
        return Future.join(futures);
    }

    /**
     * Runs a task that does not return any result in an asynchronous fashion.
     *
     * @param vertx     the underlying Vert.x instance
     * @param asyncTask the runnable that executes the task logic
     * @return a Future representing the asynchronous result of the consumer logic
     */
    public static Future<Void> executeBlocking(Vertx vertx, ThrowingRunnable<Exception> asyncTask) {
        return vertx.executeBlocking(promise -> {
            try {
                asyncTask.run();
                promise.complete();
            } catch (Exception e) {
                promise.fail(e);
            }
        });
    }

    /**
     * Runs a supplier and returns the result in an asynchronous fashion.
     *
     * @param vertx            the underlying Vert.x instance
     * @param blockingSupplier the supplier that will be executed
     * @param <T>              the return type of the supplier
     * @return a Future representing the asynchronous result of the supplier logic
     */
    public static <T> Future<T> executeBlocking(Vertx vertx, ThrowingSupplier<T, Exception> blockingSupplier) {
        return executeBlocking(vertx, promise -> {
            try {
                promise.complete(blockingSupplier.get());
            } catch (Exception e) {
                promise.fail(e);
            }
        });
    }

    /**
     * Runs a task and returns the result in an asynchronous fashion. The consumer is responsible for completing the
     * passed in promise when the execution of the task is completed.
     *
     * @param vertx     the underlying Vert.x instance
     * @param asyncTask the Promise consumer that contains the task logic
     * @param <T>       the return type of the task
     * @return a Future representing the asynchronous result of the consumer logic
     */
    public static <T> Future<T> executeBlocking(Vertx vertx, ThrowingConsumer<Promise<T>, Exception> asyncTask) {
        Promise<T> asyncTaskPromise = Promise.promise();
        vertx.executeBlocking(promise -> {
            try {
                asyncTask.accept(promise);
            } catch (Exception e) {
                promise.fail(e);
            }
        }, asyncTaskPromise);
        return asyncTaskPromise.future();
    }

    @FunctionalInterface
    public interface ThrowingRunnable<E extends Exception> {
        /**
         * Run this operation.
         *
         * @throws E the exception that this runnable may throws
         */
        void run() throws E;
    }

    @FunctionalInterface
    public interface ThrowingConsumer<T, E extends Exception> {
        /**
         * Performs this operation on the given argument.
         *
         * @param t the input argument
         * @throws E the exception this consumer may throws
         */
        void accept(T t) throws E;
    }

    @FunctionalInterface
    public interface ThrowingSupplier<T, E extends Exception> {
        /**
         * Gets a result.
         *
         * @throws E the exception this supplier may throws
         * @return a result
         */
        T get() throws E;
    }
}
