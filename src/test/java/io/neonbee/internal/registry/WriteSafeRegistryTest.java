package io.neonbee.internal.registry;

import static io.vertx.core.Future.succeededFuture;

import io.vertx.core.Future;
import io.vertx.core.Vertx;

public class WriteSafeRegistryTest extends StringRegistryTestBase {

    @Override
    protected Future<Registry<String>> createRegistry(Vertx vertx) {
        return succeededFuture(new WriteSafeRegistry<>(vertx, "testRegistry"));
    }
}
