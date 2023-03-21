package io.neonbee.internal.registry;

import static io.vertx.core.Future.succeededFuture;

import io.vertx.core.Future;
import io.vertx.core.Vertx;

class NonLockingRegistryTest extends StringRegistryTestBase {

    @Override
    protected Future<Registry<String>> createRegistry(Vertx vertx) {
        return succeededFuture(new NonLockingRegistry<>(vertx, "testRegistry"));
    }
}
