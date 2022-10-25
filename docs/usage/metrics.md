# Metrics Concept

## Content

- [Metrics Concept](#metrics-concept)
  - [Content](#content)
  - [`NeonBee` Metrics](#neonbee-metrics)
    - [Configure additional registries](#configure-additional-registries)
      - [Before startup of NeonBee](#before-startup-of-neonbee)
      - [During Runtime](#during-runtime)
    - [Send custom metrics](#send-custom-metrics)
  - [`DataVerticle` Metrics](#dataverticle-metrics)
    - [DataVerticle metrics configuration](#dataverticle-metrics-configuration)

## `NeonBee` Metrics

In NeonBee, Micrometer is used to provide reporting to various backends. Micrometer provides a facade for the most
common monitoring systems. Micrometer `MeterRegistry` objects can be registered at runtime. For this purpose, a
`CompositeMeterRegistry` is added in the `VertxOptions`. Additional registries of type `MeterRegistry` can be added to this
`CompositeMeterRegistry` at runtime.

The `MetricsEndpoint` provides the Prometheus metrics via the `/metrics` endpoint. It registers the
`PrometheusMeterRegistry` when the `MetricsEndpoint` router is created.

### Configure additional registries

#### Before startup of NeonBee

To register your own Micrometer MeterRegistry the interface `MicrometerRegistryLoader` must be implemented
and the implementing class must be specified in the `io.neonbee.NeonBee.yaml` config file via the `micrometerRegistries`
array.

Example `MicrometerRegistryLoader` implementation:

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

Example `io.neonbee.NeonBee.yaml` configuration:

```yaml
---
  // Omitted other configuration values
  micrometerRegistries:
  - className: io.neonbee.config.examples.LoggingMeterMicrometerRegistryLoader
    config:
      interval: 1
```

In above example configuration, all properties of `config` are optional. This also applies to the property
`interval`, which is used here to set how often metrics are sent to the `LoggingMeterMicrometerRegistry`.

#### During Runtime

If you want to add a MeterRegistry during runtime, you can do it using the `NeonBeeConfig#getCompositeMeterRegistry()`
method.

```java
MeterRegistry yourRegistry; // Your registry to be added.
CompositeMeterRegistry compositeMeterRegistry = NeonBee.get(vertx).getConfig().getCompositeMeterRegistry();
compositeMeterRegistry.add(yourRegistry);
```

### Send custom metrics

To provide metrics in your code, here is an example of a counter:

```java
MeterRegistry registry = BackendRegistries.getDefaultNow();
Counter counter = registry.counter("TestEndpointCounter", "TestTag1", "TestValue");
counter.increment();
count = counter.count();
```

For more information, see [user defined metrics](https://vertx.io/docs/vertx-micrometer-metrics/java/#_user_defined_metrics)

## `DataVerticle` Metrics

The DataVerticle collects four metrics:

| Metric                 | Description                                                         |
|:-----------------------|:--------------------------------------------------------------------|
| **Time**               | How long it takes to retrieve the data.                             |
| **Status counter**     | How many requests completed successfully, how many requests failed. |
| **Number of requests** | Total number of all requests.                                       |
| **Active requests**    | How many requests are currently open.                               |

### DataVerticle metrics configuration

All metrics are disabled by default. You can enable the metrics globally for all `DataVerticle`s by setting the
`metrics.enabled` value to `true` in the [`NeonBeeConfig`](./neonbee.md#neonbee-config).

Example `io.neonbee.NeonBee.yaml` configuration:

```yaml
# Other settings omitted for simplicity.
metrics:
  enabled: true
```

The metrics can be also configured using the verticle configuration.

| Property                         |  Type   | Required | Description                                                                                     |
|:---------------------------------|:-------:|:--------:|:------------------------------------------------------------------------------------------------|
| `metrics.enabled`                | boolean |    No    | Whether metrics are enabled. Defaults to value of `metrics.enabled` from NeonBee config.        |
| `metrics.metricsRegistryName`    | string  |    No    | The name of the registry to forward metrics to. Defaults to registry name from NeonBee options. |
| `metrics.reportNumberOfRequests` | boolean |    No    | Enables reporting of "Request Number". Defaults to value of `metrics.enabled`.                  |
| `metrics.reportActiveRequests`   | boolean |    No    | Enables reporting of "Active Requests. Defaults to value of `metrics.enabled`.                  |
| `metrics.reportStatusCounter`    | boolean |    No    | Enables reporting of "Status Counter". Defaults to value of `metrics.enabled`.                  |
| `metrics.reportTiming`           | boolean |    No    | Enables reporting of "Timing". Defaults to value of `metrics.enabled`.                          |


If metrics are disabled globally, and you want to enable the metrics for an individual `DataVerticle`, set the
`config.metrics.enabled` property in the verticle config to `true`. In addition, you can enable only specific
metrics by specifying the metrics configuration name and a `true` value in the verticle configuration. If you
have added a metrics configuration key, only the values with the specified `true` value are enabled. All others are
disabled.

Example configuration `MyVerticle.yaml` to enable the metrics:

```yaml
config:
  metrics:
    enabled: true
    metricsRegistryName: MyRegistry
    reportNumberOfRequests: true
    reportActiveRequests: true
    reportStatusCounter: false
    reportTiming: true
```

This example configures NeonBee to forward all metrics of the `DataVerticle` named `MyVerticle` to the custom registry
`MyRegistry`, except the "Status Counter" metric. Note that setting the `config.metrics.enabled` property in this
example is actually not necessary since every single metric is specifically enabled / disabled.
