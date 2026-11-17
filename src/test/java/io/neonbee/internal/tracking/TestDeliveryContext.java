package io.neonbee.internal.tracking;

import io.vertx.core.eventbus.DeliveryContext;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.ReplyFailure;

public class TestDeliveryContext<T> implements DeliveryContext<T> {
    private final TestMessage<T> message;

    TestDeliveryContext(TestMessage<T> message) {
        this.message = message;
    }

    @Override
    public Message<T> message() {
        return message;
    }

    @Override
    public void next() {}

    @Override
    public boolean send() {
        return message.isSend();
    }

    @Override
    public Object body() {
        return message.body();
    }

    @Override
    public boolean fail(int failureCode, String message) {
        return false;
    }

    @Override
    public boolean fail(ReplyFailure failure, int failureCode, String message) {
        return false;
    }
}
