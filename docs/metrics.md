# Metrics Concept

## Content

- [Metrics](#Metrics)
  - [configure additional metrics registries](#configure-additional-metrics-registries)
    - [when launching NeonBee](#when-launching-NeonBee)
    - [during runtime](#during-runtime)
  - [add metrics to your code](#add-metrics-to-your-code)


## Metrics
In NeonBee, Micrometer is used to provide reporting to various backends. Micrometer provides a facade for the most
common monitoring systems.
Micrometer `MeterRegistry` objects can be registered at runtime. For this purpose, a `CompositeMeterRegistry` is added in
the `VertxOptions`. Additional `MeterRegistry` can be added to this `CompositeMeterRegistry` at runtime.

The `MetricsEndpoint`, which by default provides the Prometheus metrics under the /metrics path, registers the
`PrometheusMeterRegistry` when the `MetricsEndpoint` router is created.

## configure additional metrics registries
### when launching NeonBee
To register own Micrometer MeterRegistry interface `MicrometerRegistryLoader` must be implemented
and the implementing class must be specified in the configuration `io.neonbee.NeonBee.yaml` in the `micrometerRegistries`
array.

Example MicrometerRegistryLoader implementation:
```java
package io.neonbee.config.examples;

import io.micrometer.core.instrument.logging.LoggingMeterRegistry;
import io.neonbee.config.metrics.MicrometerRegistryLoader;

public static class LoggingMeterMicrometerRegistryLoader implements MicrometerRegistryLoader {
    @Override
    public MeterRegistry load(JsonObject config) {
        return new LoggingMeterRegistry();
    }
}
```

Example io.neonbee.NeonBee.yaml configuration:
```yaml
---
  // Omitted other configuration values
  micrometerRegistries:
  - className: io.neonbee.config.examples.LoggingMeterMicrometerRegistryLoader
    config:
      key: value
```

### during runtime
If you want to add a MeterRegistry during runtime, you can do it using the `NeonBeeConfig#getCompositeMeterRegistry()`
method.

```java
MeterRegistry yourRegistry; // Your registry to be added.
CompositeMeterRegistry compositeMeterRegistry = NeonBee.get(vertx).getConfig().getCompositeMeterRegistry();
compositeMeterRegistry.add(yourRegistry);
```
## add metrics to your code

To provide metrics in your code, here is an example of a counter:
```java
MeterRegistry registry = BackendRegistries.getDefaultNow();
Counter counter = registry.counter("TestEndpointCounter", "TestTag1", "TestValue");
counter.increment();
count = counter.count();
```
For more information, see [user defined metrics](https://vertx.io/docs/vertx-micrometer-metrics/java/#_user_defined_metrics)