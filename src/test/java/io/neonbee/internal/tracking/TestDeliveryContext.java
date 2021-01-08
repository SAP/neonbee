package io.neonbee.internal.tracking;

import io.vertx.core.eventbus.DeliveryContext;
import io.vertx.core.eventbus.Message;

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
}
