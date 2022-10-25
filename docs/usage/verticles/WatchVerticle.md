# WatchVerticle

The `WatchVerticle` is a `JobVerticle` that wraps the Java WatchService API. It allows you to monitor changes to a
specified directory, such as the creation, modification, or deletion of files or directories within it. By default, the
`WatchVerticle` checks for changes every 500 milliseconds, but this interval can be customized by providing a different
value as an argument to the constructor.

The `WatchVerticle` provides three methods that can be overridden by subclasses to specify custom behavior in response to
the three types of events that the WatchService API can detect: `observedCreate`, `observedModify`, and
`observedDelete`. Subclasses can also override the start method to specify any additional initialization or setup that
needs to be done before the `WatchVerticle` begins monitoring the directory.

The `WatchVerticle` is scanning the directory in a specified interval and executes the implemented actions according
to the related filesystem events which are found during the scan. If there is a new scan, the corresponding actions
will be executed, while the actions of the previous scan might still be in progress. To wait for old events to
complete before new scans are started, the constructor parameter `parallelProcessing` can be set to `false`. By
default, it is `true`.

Additionally, the `WatchVerticle` can be configured to handle existing files in the watched directory when it is started,
by setting the `handleExisting` parameter to `true`. This means that the `WatchVerticle` will treat existing files as if
they had just been created and will process them accordingly.

The `WatchVerticle` also provides a utility method, `isCopyLogic`, which can be used to parse configuration options and
determine whether a subclass should treat events as copies rather than moves. This can be useful in scenarios where
the WatchService API does not provide enough information to distinguish between file moves and copies. By default,
`isCopyLogic` returns false, indicating that the `WatchVerticle` should treat events as moves rather than copies.

## Configuration

- The config file must be placed in the working directory inside a directory called `config` and the filename must be matching the full qualified class name of the verticle, i.e. **`config/io.neonbee.internal.verticle.WatchVerticle.yaml`**.
- The top-level `config` key is mandatory.

You can use the following options to configure the `WatchVerticle`.

| Property     | Type   | Required | Description                                                                                                                 | Default |
|--------------|--------|:--------:|-----------------------------------------------------------------------------------------------------------------------------|---------|
| `watchLogic` | string |    No    | Whether to listen only on `CREATE`, or also on `MODIFY` events. If both should be enabled, the value must be set to `copy`. | `~`     |

**Default Configuration of `watchLogic`:**

```yaml
---
config:
  watchLogic: ~
```

## Reference Implementations

- [ModelRefreshVerticle](./ModelRefreshVerticle.md)
- [DeployerVerticle](./DeployerVerticle.md)
