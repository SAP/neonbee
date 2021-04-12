package io.neonbee.internal.helper;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

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
     * @return A {@link CompositeFuture}, succeeded if all of the passed futures succeeded, failing otherwise.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static CompositeFuture allComposite(List<? extends Future<?>> futures) {
        return CompositeFuture.all((List<Future>) (Object) futures);
    }

    /**
     * Type safe CompositeFuture future (as soon as Vert.x applies this interface signature, switch to
     * {@link CompositeFuture#join(List)} again).
     *
     * @param futures A list of futures to be converted
     * @return A {@link CompositeFuture} containing the passed futures
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static CompositeFuture joinComposite(List<? extends Future<?>> futures) {
        return CompositeFuture.join((List<Future>) (Object) futures);
    }

    /**
     * Runs a task and returns the result in an asynchronous fashion. The consumer is responsible for completing the
     * passed in promise when the execution of the task is completed.
     *
     * @param vertx     the underlying Vert.x instance
     * @param asyncTask the Promise consumer that contains the task logic
     * @param <T>       the return type of the task
     * @return a Future representing the asynchronous result of the consumer logic.
     */
    public static <T> Future<T> executeBlocking(Vertx vertx, Consumer<Promise<T>> asyncTask) {
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

    /**
     * Runs a supplier and returns the result in an asynchronous fashion.
     *
     * @param vertx            the underlying Vert.x instance
     * @param blockingSupplier the supplier that will be executed
     * @param <T>              the return type of the supplier
     * @return a Future representing the asynchronous result of the supplier logic.
     */
    public static <T> Future<T> executeBlocking(Vertx vertx, Supplier<T> blockingSupplier) {
        return executeBlocking(vertx, p -> p.complete(blockingSupplier.get()));
    }
}
