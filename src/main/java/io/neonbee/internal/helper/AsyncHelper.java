package io.neonbee.internal.helper;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.vertx.core.AsyncResult;
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
     * CompositeFuture.all again).
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
     * CompositeFuture.join again).
     *
     * @param futures The futures to be checked for completeness
     * @return A {@link CompositeFuture}, succeeded if any of the passed futures succeeded, failing otherwise.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static CompositeFuture anyComposite(List<? extends Future<?>> futures) {
        return CompositeFuture.any((List<Future>) (Object) futures);
    }

    /**
     * Type safe CompositeFuture future (as soon as Vert.x applies this interface signature, switch to
     * CompositeFuture.join again).
     *
     * @param futures A list of futures to be converted
     * @return A {@link CompositeFuture} containing the passed futures
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static CompositeFuture joinComposite(List<? extends Future<?>> futures) {
        return CompositeFuture.join((List<Future>) (Object) futures);
    }

    /**
     * Returns an array of async. results for a given {@link CompositeFuture}.
     *
     * @param compositeFuture the composite future to get the results for
     * @return an array of (untyped) {@link AsyncResult}
     */
    public static AsyncResult<?>[] asyncResults(CompositeFuture compositeFuture) {
        return typedAsyncResults(compositeFuture);
    }

    /**
     * Returns a list of async. results for a given {@link CompositeFuture}.
     *
     * @param compositeFuture the composite future to get the results for
     * @return a list of (untyped) {@link AsyncResult}
     */
    public static List<AsyncResult<?>> asyncResultList(CompositeFuture compositeFuture) {
        return Arrays.asList(asyncResults(compositeFuture));
    }

    /**
     * Returns a typed array of async. results for a given {@link CompositeFuture}.
     *
     * @param <T>             the shared type of all async. results
     * @param compositeFuture the composite future to get the results for
     * @return an array of {@link AsyncResult}
     */
    public static <T> AsyncResult<T>[] typedAsyncResults(CompositeFuture compositeFuture) {
        @SuppressWarnings("unchecked")
        AsyncResult<T>[] asyncResults = new AsyncResult[compositeFuture.size()];
        Arrays.setAll(asyncResults, index -> new AsyncResult<T>() {
            @Override
            public T result() {
                return compositeFuture.resultAt(index);
            }

            @Override
            public Throwable cause() {
                return compositeFuture.cause(index);
            }

            @Override
            public boolean succeeded() {
                return compositeFuture.succeeded(index);
            }

            @Override
            public boolean failed() {
                return compositeFuture.failed(index);
            }
        });
        return asyncResults;
    }

    /**
     * Returns a typed list of async. results for a given {@link CompositeFuture}.
     *
     * @param <T>             the shared type of all async. results
     * @param compositeFuture the composite future to get the results for
     * @return a list of {@link AsyncResult}
     */
    public static <T> List<AsyncResult<T>> typedAsyncResultList(CompositeFuture compositeFuture) {
        return Arrays.asList(typedAsyncResults(compositeFuture));
    }

    /**
     * Checks whether all async. results of a given vararg array have been successful.
     *
     * @param asyncResults the varargs array of {@link AsyncResult} to check
     * @return true if all async. results are a success
     */
    public static boolean allSucceeded(AsyncResult<?>... asyncResults) {
        return Arrays.stream(asyncResults).allMatch(AsyncResult::succeeded);
    }

    /**
     * Checks whether any async. results of a given vararg array have been successful.
     *
     * @param asyncResults the varargs array of {@link AsyncResult} to check
     * @return true if any async. results are a success
     */
    public static boolean anySucceeded(AsyncResult<?>... asyncResults) {
        return Arrays.stream(asyncResults).anyMatch(AsyncResult::succeeded);
    }

    /**
     * Checks whether all async. results of a given vararg array have failed.
     *
     * @param asyncResults the varargs array of {@link AsyncResult} to check
     * @return true if all async. results have failed
     */
    public static boolean allFailed(AsyncResult<?>... asyncResults) {
        return Arrays.stream(asyncResults).allMatch(AsyncResult::failed);
    }

    /**
     * Checks whether any async. results of a given vararg array failed.
     *
     * @param asyncResults the varargs array of {@link AsyncResult} to check
     * @return true if any async. results have failed
     */
    public static boolean anyFailed(AsyncResult<?>... asyncResults) {
        return Arrays.stream(asyncResults).anyMatch(AsyncResult::failed);
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
