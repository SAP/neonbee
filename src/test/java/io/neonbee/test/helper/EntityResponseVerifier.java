package io.neonbee.test.helper;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import io.neonbee.data.DataContext;
import io.neonbee.data.DataException;
import io.neonbee.entity.EntityWrapper;
import io.vertx.core.Future;
import io.vertx.junit5.VertxTestContext;

public interface EntityResponseVerifier {

    /**
     * This method provides an instance of a {@link DataResponseVerifier}, which offers assertion methods that can be
     * reused by the EntityResponseVerifier
     *
     * @return an instance of {@link DataResponseVerifier}
     */
    private DataResponseVerifier newDataVerifier() {
        return new DataResponseVerifier() {};
    }

    // default Future<Void> assertEntity(Future<EntityWrapper> response, EntityWrapper expected, VertxTestContext
    // testContext);
    // This signature is not yet supported, because Olingo's current implementation of the equals() method is broken
    // which also breaks the implementation of equals() in the EntityWrapper.

    /**
     * Compares the entity response against the logic defined in the assertHandler.
     *
     * @param response      a Future with the received response
     * @param assertHandler the assertion handler which implements the validation logic for the response
     * @param testContext   the Vert.x test context
     * @return a succeeded Future if the assertion was successful. Otherwise, the testContext fails.
     */

    default Future<Void> assertEntity(Future<EntityWrapper> response, Consumer<EntityWrapper> assertHandler,
            VertxTestContext testContext) {
        return newDataVerifier().assertData(response, assertHandler, testContext);
    }

    /**
     * Compares the entity response provided by the responseBuilder against the logic defined in the assertHandler.
     *
     * To be able to have a reference of the {@link DataContext} in the Consumer, it is required that the DataContext is
     * created inside this method. Therefore, this method also requires a response builder in which the used DataContext
     * * is provided, in case it is needed for the request, and must return a Future with the actual response.
     *
     * @param responseBuilder the {@link Function} which builds and returns a Future with the response
     * @param assertHandler   the assertion handler which implements the validation logic for the response under
     *                        consideration of the {@link DataContext}
     * @param testContext     the Vert.x test context
     * @return a succeeded Future if the assertion was successful. Otherwise, the testContext fails.
     */
    default Future<Void> assertEntity(Function<DataContext, Future<EntityWrapper>> responseBuilder,
            BiConsumer<EntityWrapper, DataContext> assertHandler, VertxTestContext testContext) {
        return newDataVerifier().assertData(responseBuilder, assertHandler, testContext);
    }

    /**
     * Compares the entity response with an expected exception.
     *
     * @param response    a Future with the received response
     * @param exception   the exception that is expected and compared to the received response
     * @param testContext the Vert.x test context
     * @return a succeeded Future if the assertion was successful. Otherwise, the testContext fails.
     */
    default Future<Void> assertEntityFailure(Future<EntityWrapper> response, DataException exception,
            VertxTestContext testContext) {
        return newDataVerifier().assertDataFailure(response, exception, testContext);
    }

    /**
     * Compares the entity response against the logic defined in the assertHandler.
     *
     * @param response      a Future with the received response
     * @param assertHandler the assertion handler which implements the expected exception
     * @param testContext   the Vert.x test context
     * @return a succeeded Future if the assertion was successful. Otherwise, the testContext fails.
     */
    default Future<Void> assertEntityFailure(Future<EntityWrapper> response, Consumer<DataException> assertHandler,
            VertxTestContext testContext) {
        return newDataVerifier().assertDataFailure(response, assertHandler, testContext);
    }

    /**
     * Compares the entity response provided by the responseBuilder against the logic defined in the assertHandler.
     *
     * To be able to have a reference of the {@link DataContext} in the Consumer, it is required that the DataContext is
     * created inside this method. Therefore, this method also requires a response builder in which the used DataContext
     * * is provided, in case it is needed for the request, and must return a Future with the actual response.
     *
     * @param responseBuilder the {@link Function} which builds and returns a Future with the response
     * @param assertHandler   the assertion handler which implements an exception that is used for the comparison with
     *                        the actual exception from the response under consideration of the {@link DataContext}
     * @param testContext     the Vert.x test context
     * @return a succeeded Future if the assertion was successful. Otherwise, the testContext fails.
     */
    default Future<Void> assertEntityFailure(Function<DataContext, Future<EntityWrapper>> responseBuilder,
            BiConsumer<DataException, DataContext> assertHandler, VertxTestContext testContext) {
        return newDataVerifier().assertDataFailure(dc -> responseBuilder.apply(dc), assertHandler, testContext);
    }
}
