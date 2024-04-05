package io.neonbee.config;

import java.util.List;
import java.util.Objects;

import io.neonbee.registry.Registry;
import io.vertx.core.Future;
import io.vertx.core.Vertx;

public class RegistryTestImpl implements Registry<String> {

    public RegistryTestImpl(Vertx vertx) {
        Objects.requireNonNull(vertx, "vertx parameter is null.");
    }

    @Override
    public Future<Void> register(String key, String value) {
        return null;
    }

    @Override
    public Future<Void> unregister(String key, String value) {
        return null;
    }

    @Override
    public Future<List<String>> get(String key) {
        return null;
    }
}
