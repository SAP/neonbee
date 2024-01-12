package io.neonbee.internal.tracking;

import io.vertx.core.MultiMap;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.impl.MessageImpl;

public class TestMessage<T> extends MessageImpl<T, T> {
    public TestMessage(String address, String replyAddrss, MultiMap headers, T sentBody) {
        super(address, headers, sentBody, null, false, null);
        this.replyAddress = replyAddrss;
    }

    @Override
    public T body() {
        return sentBody;
    }

    @Override
    public void reply(Object message) {}

    @Override
    public void reply(Object message, DeliveryOptions options) {}

    @Override
    public void fail(int failureCode, String message) {}
}
