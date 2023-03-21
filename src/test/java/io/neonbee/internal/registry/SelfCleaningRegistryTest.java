package io.neonbee.internal.registry;

import io.vertx.core.Future;
import io.vertx.core.Vertx;

class SelfCleaningRegistryTest extends StringRegistryTestBase {

    @Override
    protected Future<Registry<String>> createRegistry(Vertx vertx) {
        return SelfCleaningRegistry.<String>create(vertx, "registryName").map(Registry.class::cast);
    }
}
