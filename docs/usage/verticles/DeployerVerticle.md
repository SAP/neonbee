# DeployerVerticle

The `DeployerVerticle` is a `JobVerticle` that watches a specified directory for new Java Archive (JAR) files, and deploys them
if they are valid NeonBee modules. When a JAR file is deleted from the watched directory, the `DeployerVerticle` undeploys the
corresponding NeonBee module.

The `DeployerVerticle` is deployed automatically by NeonBee during startup. This can be disabled by starting NeonBee
with the [CLI option](../neonbee.md#neonbee-options) `--do-not-watch-files`.

The `DeployerVerticle` has three methods for reacting to events in the watched directory: `observedCreate`,
`observedModify`, and `observedDelete`. `observedCreate` is called when a new file is created in the watched directory,
`observedModify` is called when an existing file in the watched directory is modified, and `observedDelete` is called when
a file is deleted from the watched directory. See [WatchVerticle](WatchVerticle.md) for more details.

The DeployerVerticle uses the `DeployableModule` class to create a Deployment object, which represents a deployed
NeonBee module. The Deployment object is then added to a map of deployed modules, keyed by the path of the corresponding
JAR file. When a JAR file is deleted, the `DeployerVerticle` removes the corresponding Deployment object from the map and
undeploys the module.

In addition, the `DeployerVerticle` has an optional configuration option called `watchLogic`, which determines how the
verticle should handle file events when a file is copied into the watched directory.

- If `watchLogic` is set to `"copy"`, the `DeployerVerticle` will only react to `observedCreate` events when a file is copied.
- If `watchLogic` is not set or is set to any other value, the `DeployerVerticle` will react to both
  `observedCreate` and `observedModify` events when a file is copied.

## Configuration

See [`WatchVerticle` configuration](WatchVerticle.md#configuration), which is extended by `DeployerVerticle`.