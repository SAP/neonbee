package io.neonbee.health;

import java.util.List;

import io.neonbee.NeonBee;
import io.vertx.core.Vertx;

public class DummyHealthCheckProvider implements HealthCheckProvider {
    @Override
    public List<AbstractHealthCheck> get(Vertx vertx) {
        return List.of(new DummyHealthCheck(NeonBee.get(vertx)));
    }
}
