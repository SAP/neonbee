package io.neonbee.test.helper;

import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;

public final class MockitoHelper {

    /**
     * Sometimes it is necessary to mock a method which accepts a {@link Handler}. The problem is, that the mocked
     * method will never call the passed handler. To solve this issue it is necessary to capture the passed
     * {@link Handler} and triggers the {@link Handler#handle(Object)} method manually.
     *
     * @param handlerPosition The position of the handler in the signature of the mocked method
     * @param asyncResult     The result with which the {@link Handler} is called.
     * @return A {@link Answer} that triggers the {@link Handler} passed to the mocked method.
     */
    public static <T> Answer<Void> callHandlerAnswer(int handlerPosition, AsyncResult<T> asyncResult) {
        return new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                Handler<AsyncResult<T>> handler = invocation.<Handler<AsyncResult<T>>>getArgument(handlerPosition);
                handler.handle(asyncResult);
                return null;
            }
        };
    }

    private MockitoHelper() {
        // Utils class no need to instantiate
    }
}
