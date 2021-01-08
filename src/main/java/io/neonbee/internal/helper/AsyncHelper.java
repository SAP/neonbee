package io.neonbee.internal.helper;

import java.util.function.Consumer;
import java.util.function.Supplier;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;

/**
 * A helper that simplifies the integration of non asynchronous code into an asynchronous operation.
 */
public final class AsyncHelper {

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

    private AsyncHelper() {}
}
