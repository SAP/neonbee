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

## DataVerticle metrics

The DataVerticle collects four metrics:

| metric | discription |
|---|---|
| Time | How long it takes to retrieve the data. |
| Status counter | How many requests completed successfully, how many requests failed |
| Number of requests | Total number of all requests |
| Active requests | How many requests are currently open |

### DataVerticle metrics configuration

All metrics are disabled by default. You can enable the metrics globally for all DataVerticles by setting the `metrics.enabled` value to `true` in the `NeonBeeConfig`.

**io.neonbee.NeonBee.yaml:**

```yaml
# Other settings omitted for simplicity.
metrics:
    enabled: true
```

The metrics can be also configured using the verticle configuration. To enable the metrics for an individual DataVerticle, it is necessary to add the `config.metrics.enabled` property with the value `true`. In addition, you can enable only specific metrics by specifying the metrics configuration name and a true value in the verticle configuration. If you have added a metrics configuration key, only the values with the specified `true` value are enabled. All others are disabled.
Complete yaml example configuration to enable the metrics:
```yaml
config:
    metrics:
        enabled: true
        meterRegistryName: SomeRegistryName
        reportNumberOfRequests: true
        reportActiveRequests: true
        reportStatusCounter: true
        reportTiming: true
```

