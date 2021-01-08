package io.neonbee.test.helper;

import static com.google.common.truth.Truth.assertThat;
import static io.vertx.core.Future.future;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import io.neonbee.data.DataContext;
import io.neonbee.data.DataException;
import io.neonbee.data.internal.DataContextImpl;
import io.vertx.core.Future;
import io.vertx.junit5.VertxTestContext;

public interface DataResponseVerifier {
    /**
     * Compares the data response with an expected value.
     *
     * @param response      a Future with the received response
     * @param expectedValue the value which is expected to match the response
     * @param testContext   the Vert.x test context
     * @param <T>           the type of the DataResponse
     * @return a succeeded Future if the the assertion was successful. Otherwise, the testContext fails.
     */
    default <T> Future<Void> assertDataEquals(Future<T> response, T expectedValue, VertxTestContext testContext) {
        return assertData(response, (Consumer<T>) r -> assertThat(r).isEqualTo(expectedValue), testContext);
    }

    /**
     * Compares the data response against the logic defined in the assertHandler.
     *
     * @param response      a Future with the received response
     * @param assertHandler the assertion handler which implements the validation logic for the response
     * @param testContext   the Vert.x test context
     * @param <T>           the type of the DataResponse
     * @return a succeeded Future if the the assertion was successful. Otherwise, the testContext fails.
     */
    default <T> Future<Void> assertData(Future<T> response, Consumer<T> assertHandler, VertxTestContext testContext) {
        return assertData(ctx -> response, (obj, ctx) -> assertHandler.accept(obj), testContext);
    }

    /**
     * Compares the data response provided by the responseBuilder against the logic defined in the assertHandler.
     *
     * To be able to have a reference of the {@link DataContext} in the Consumer, it is required that the DataContext is
     * created inside this method. Therefore, this method also requires a response builder in which the used DataContext
     * is provided, in case it is needed for the request, and must return a Future with the actual response.
     *
     * @param responseBuilder the {@link Function} which contains the request logic and returns a Future with the
     *                        response
     * @param assertHandler   the assertion handler which implements the validation logic for the response under
     *                        consideration of the {@link DataContext}
     * @param testContext     the Vert.x test context
     * @param <T>             the type of the DataResponse
     * @return a succeeded Future if the the assertion was successful. Otherwise, the testContext fails.
     */
    default <T> Future<Void> assertData(Function<DataContext, Future<T>> responseBuilder,
            BiConsumer<T, DataContext> assertHandler, VertxTestContext testContext) {
        DataContext dataContext = new DataContextImpl();
        return future(promise -> responseBuilder.apply(dataContext).onComplete(testContext.succeeding(response -> {
            testContext.verify(() -> assertHandler.accept(response, dataContext));
            promise.complete();
        })));
    }

    /**
     * Compares the data response with an expected exception.
     *
     * @param response    a Future with the received response
     * @param exception   the exception that is expected and compared to the received response
     * @param testContext the Vert.x test context
     * @return a succeeded Future if the the assertion was successful. Otherwise, the testContext fails.
     */
    default Future<Void> assertDataFailure(Future<?> response, DataException exception, VertxTestContext testContext) {
        return assertDataFailure(response, r -> assertThat(r).isEqualTo(exception), testContext);
    }

    /**
     * Compares the data response against the logic defined in the assertHandler.
     *
     * @param response      a Future with the received response
     * @param assertHandler the assertion handler which implements the expected exception
     * @param testContext   the Vert.x test context
     * @return a succeeded Future if the the assertion was successful. Otherwise, the testContext fails.
     */
    default Future<Void> assertDataFailure(Future<?> response, Consumer<DataException> assertHandler,
            VertxTestContext testContext) {
        return assertDataFailure(ctx -> response, (obj, ctx) -> assertHandler.accept(obj), testContext);
    }

    /**
     * Compares the data response provided by the responseBuilder against the logic defined in the assertHandler.
     *
     * To be able to have a reference of the {@link DataContext} in the Consumer, it is required that the DataContext is
     * created inside this method. Therefore, this method also requires a response builder in which the used DataContext
     * is provided, in case it is needed for the request, and must return a Future with the actual response.
     *
     * @param responseBuilder the {@link Function} which contains the request logic and returns a Future with the
     *                        response
     * @param assertHandler   the assertion handler which implements an exception that is used for the comparison with
     *                        the actual exception from the response under consideration of the {@link DataContext}
     * @param testContext     the Vert.x test context
     * @return a succeeded Future if the the assertion was successful. Otherwise, the testContext fails.
     */
    default Future<Void> assertDataFailure(Function<DataContext, Future<?>> responseBuilder,
            BiConsumer<DataException, DataContext> assertHandler, VertxTestContext testContext) {
        DataContext dataContext = new DataContextImpl();
        return future(promise -> responseBuilder.apply(dataContext).onComplete(testContext.failing(failure -> {
            testContext.verify(() -> assertThat(failure).isInstanceOf(DataException.class))
                    .verify(() -> assertHandler.accept((DataException) failure, dataContext));
            promise.complete();
        })));
    }
}
