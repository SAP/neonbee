# NeonBee

## NeonBee Options

NeonBee comes with a command line interface for starting and configuring NeonBee. In addition to CLI parameters, the
NeonBee options can also be provided via environment variables, which always takes precedence. See the following
table for a full list of configuration options.

| CLI parameter                                | Environment Variable                  | Description                                                                           |
|----------------------------------------------|---------------------------------------|---------------------------------------------------------------------------------------|
| `--event-loop-pool-size` or `-elps`          | `NEONBEE_EVENT_LOOP_POOL_SIZE`        | Set the number of threads `(> 0)` for the event loop pool.                            |
| `--worker-pool-size` or `-wps`               | `NEONBEE_WORKER_POOL_SIZE`            | Set the number of threads for the worker pool. Default is `20`.                       |
| `--instance-name` or `-name`                 | `NEONBEE_INSTANCE_NAME`               | Set the NeonBee instance name. Must have at least one character.                      |
| `--working-directory` or `-cwd`              | `NEONBEE_WORKING_DIRECTORY`           | Set the working directory. Default is `"working_dir/".`                               |
| `--ignore-classpath` or `-no-cp`             | `NEONBEE_IGNORE_CLASSPATH`            | Ignore verticle / models on the class path. Default is `false`.                       |
| `--disable-job-scheduling` or `-no-jobs`     | `NEONBEE_DISABLE_JOB_SCHEDULING`      | Do not schedule any job verticle. Default is `false`.                                 |
| `--do-not-watch-files` or `-no-watchers`     | `NEONBEE_DO_NOT_WATCH_FILES`          | Do not watch files. Default is `false`.                                               |
| `--cluster-port` or `-clp`                   | `NEONBEE_CLUSTER_PORT`                | Set the port of the event bus. Default is a random port.                              |
| `--clustered` or `-cl`                       | `NEONBEE_CLUSTERED`                   | Start in cluster mode. Default is `false`.                                            |
| `--cluster-config` or `-cc`                  | `NEONBEE_CLUSTER_CONFIG`              | Set the cluster configuration file path.                                              |
| `--cluster-truststore` or `-cts`             | `NEONBEE_CLUSTER_TRUSTSTORE`          | Set the cluster truststore file path (must be PKCS #12).                                              |
| `--cluster-truststore-password` or `-cts-pw` | `NEONBEE_CLUSTER_TRUSTSTORE_PASSWORD` | Set the cluster truststore password.                                                  |
| `--cluster-keystore` or `-cks`               | `NEONBEE_CLUSTER_KEYSTORE`            | Set the cluster keystore file path (must be PKCS #12).                                |
| `--cluster-keystore-password` or `-cks-pw`   | `NEONBEE_CLUSTER_KEYSTORE_PASSWORD`   | Set the cluster keystore password.                                                    |
| `--cluster-manager` or `-cm`                 | `NEONBEE_CLUSTER_MANAGER`             | Set the cluster manager factory to create the cluster manager. Default is `hazelcast` |
| `--active-profiles` or `-ap`                 | `NEONBEE_ACTIVE_PROFILES`             | Set the active profiles. If not set, the default value is `ALL`.                      |
| `--module-jar-paths` or `-mjp`               | `NEONBEE_MODULE_JARS`                 | Set the module JAR paths to be loaded during startup.                                 |
| `--metrics-registry-name` or `-mrn`          | `NEONBEE_METRICS_REGISTRY_NAME`       | Set the metrics registry by name. See [metrics](./metrics.md) for details.            |
| `--server-port` or `-port`                   | `NEONBEE_SERVER_PORT`                 | Set the HTTP(S) port of the server verticle.                                          |
| `--server-host` or `-sh`                     | `NEONBEE_SERVER_HOST`                 | Set the host of the server verticle                                                   |

**Note:** For the `--active-profiles` option, only the [available profiles](#neonbee-profiles) are allowed to set.
Multiple profiles can be set by separating them with commas, e.g. `--active-profiles INCUBATOR,STABLE,CORE`. If this
option is not set, the default value is `ALL`.

**Note:** Truststore option can only be set in combination with keystore option and vice versa. Setting the key- and
truststore options automatically activates the eventbus encryption.

The default for the event loop pool size is `2 x number of available processors` on the host. This is the same as
the Vert.x default.

## NeonBee Deployable

A `NeonBeeDeployable` is a flag annotation that can be applied to a verticle in order to indicate that it should be
deployed by the NeonBee application. More specifically, verticles supporting this mechanism are `DataVerticle`s,
`EntityVerticle`s, and [`JobVerticle`](./JobVerticle.md)s. When the application is started, NeonBee will scan for
classes that are annotated with `@NeonBeeDeployable` and deploy them. This can be useful for automatically deploying
verticles that are part of the application, without having to manually specify them in the configuration or command
line arguments.

The `NeonBeeDeployable` interface has several options that can be specified when the annotation is applied to a
verticle class:

* `namespace`: This specifies the namespace of the deployable verticle. This should be a lower-case, forward
  slash-separated string of unlimited length, using only Latin letters and numbers.
* `profile`: This specifies the [profiles](#neonbee-profiles) that the deployable verticle belongs to.
* `autoDeploy`: This specifies whether the deployable verticle should be automatically deployed when the application
  is started. If set to `false`, the verticle will not be deployed unless it is explicitly specified in the
  configuration. An instable verticle (i.e., with profile `INCUBATOR`) should set this to `false`.

To use the `@NeonBeeDeployable` annotation, it can be applied to a verticle like this:

```java
@NeonBeeDeployable(namespace = "my/namespace", profile = NeonBeeProfile.WEB, autoDeploy = true)
public class BeeVerticle extends DataVerticle<JsonArray> {
  // Verticle implementation goes here
}
```

This will cause the `BeeVerticle` class to be deployed by the NeonBee application during startup, as long as the
`WEB` profile is active.

## NeonBee Profiles

NeonBee profiles are a way to configure the deployment of different components or features in a NeonBee instance.
The available profiles are:

* `ALL`: Deploys all available components.
* `CORE`: Deploys core components such as database access, authentication, and authorization.
* `STABLE`: Deploys stable components that have passed thorough testing and are ready for production use.
* `INCUBATOR`: Deploys components that are still being developed and are not yet ready for production use.
* `WEB`: Deploys web components such as HTTP endpoints.
* `NO_WEB`: Prevents the deployment of web components.

To annotate a verticle with a profile, the [`@NeonBeeDeployable` annotation](#neonbee-deployable) can be used.

## NeonBee Config

In contrast to [NeonBee options](#neonbee-options), the NeonBee config is persistent configuration in a file. While
NeonBee options contain information that must be specified when starting NeonBee, such as the port of the server to
start on and the cluster to connect to, which may vary from cluster node to cluster node, the NeonBee config
contains information that is mostly shared between different cluster nodes or that you want to specify before
starting NeonBee.

The NeonBee config must be located in a `config` folder of the working directory. The file must be either in yaml or
json and the name of the file is the fully qualified name of NeonBee (`io.neonbee.NeonBee.yaml`).

| Property                                        |  Type   | Required | Description                                                                                                                   |
| :---------------------------------------------- | :-----: |:--------:|:------------------------------------------------------------------------------------------------------------------------------|
| `eventBusCodecs`                                | integer |    No    | Sets the event bus codecs to be loaded with NeonBee.                                                                          |
| `eventBusTimeout`                               | integer |    No    | Sets the event bus timeout in seconds. Default is 30 seconds.                                                                 |
| `deploymentTimeout`                             | integer |    No    | Sets a timeout in seconds when pending deployments will fail. Default is 30 seconds, negative values disable the timeout.     |
| `modelsDeploymentTimeout`                       | integer |    No    | Overrides the default deployment timeout for model deployments.                                                               |
| `moduleDeploymentTimeout`                       | integer |    No    | Overrides the default deployment timeout for module deployments.                                                              |
| `verticleDeploymentTimeout`                     | integer |    No    | Overrides the default deployment timeout for verticle deployments.                                                            |
| `defaultThreadingModel`                         | string  |    No    | Sets the default [threading model](https://vertx.io/docs/apidocs/io/vertx/core/ThreadingModel.html) used to deploy verticles. |
| [`health`](#health)                             | object  |    No    | Sets health config.                                                                                                           |
| [`metrics`](#metrics)                           | object  |    No    | Sets metrics config.                                                                                                          |
| [`micrometerRegistries`](#micrometerregistries) | object  |    No    | Sets the list of Micrometer registries for metrics forwarding.                                                                |
| [`platformClasses`](#platformclasses)           | object  |    No    | Sets classes available by the platform.                                                                                       |
| `trackingDataHandlingStrategy`                  | string  |    No    | The class to load for tracking data handling. Default: `io.neonbee.internal.tracking.TrackingDataLoggingStrategy`             |
| `timeZone`                                      | string  |    No    | Sets the timezone used in NeonBee. Default is `UTC`.                                                                          |
| `jsonMaxStringSize`                             | string  |    No    | Set the maximum string length (in chars or bytes, depending on input context) to parse JSON input strings or buffers.         |

### `health`

Global settings for health checking.

| Property                  |  Type   | Required | Description                                                              |
|:--------------------------|:-------:| :------: |:-------------------------------------------------------------------------|
| `enabled`                 | boolean |    No    | Enables / disables the health checking. Default is `true` (enabled).     |
| `timeout`                 | integer |    No    | Sets the global timeout for all health checks. Default is 1 second.      |
| `collectClusteredResults` | boolean |    No    | Collect HealthCheck results from other cluster nodes. Default is `true`. |

Note: the `enabled` property can be overridden by any node-specific health check configuration. See [health](./health.md)
for details.

### `metrics`

Global settings for metrics collecting and forwarding.

| Property  |  Type   | Required | Description                                |
| :-------- | :-----: | :------: | :----------------------------------------- |
| `enabled` | boolean |    No    | Enables / disables the metrics forwarding. |

### `micrometerRegistries`

This property is a list of fully qualified class names and additional configuration. All classes must implement the
`MicrometerRegistryLoader` interface. NeonBee tries to load all these Micrometer registries and forwards the metrics
to each of them. The latter only happens if `metrics.enabled` is set to `true`.

| Property    |  Type  | Required | Description                            |
| :---------- | :----: | :------: | :------------------------------------- |
| `className` | string |   Yes    | The full qualified class name to load. |
| `config`    | object |    No    | Additional configuration.              |

### `platformClasses`

Platform classes are classes to be considered "provided" by the system class loader. This option is only relevant
in case NeonBee modules are used. NeonBee modules will attempt to find platform classes in the system class
loader first, before loading them (*self-first*) from their own (so called) *module class-loader*. This way, you can
prevent incompatibility issues across modules and also have modules with a much smaller runtime footprint.

Default platform classes set by NeonBee:

```yml
platformClasses:
  - "io.vertx.*"
  - "io.neonbee.*"
  - "org.slf4j.*"
  - "org.apache.olingo.*"
```

The default can be overridden by specifying your own platform classes. With that, the default can be disabled by setting

```yml
platformClasses: []
```

in the NeonBee config file.

### Example Configuration

`config/io.neonbee.NeonBee.yaml`

```yml
---
eventBusTimeout: 110

platformClasses:
  - "io.vertx.*"
  - "io.neonbee.*"
  - "org.slf4j.*"
  - "org.apache.olingo.*"

micrometerRegistries:
  - className: org.example.DynatraceRegistryLoader
    config:
      interval: 1m

health:
  enabled: true
  timeout: 5
```
