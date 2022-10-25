# ModelRefreshVerticle

The `ModelRefreshVerticle` is a `JobVerticle` that watches a specified directory for changes and reloads the
models when a change is detected. It can be configured to trigger a model refresh in response to different types of
events, such as the creation or modification of a file in the watched directory. The `ModelRefreshVerticle` also has a
configurable check interval to specify how often it should check for changes in the directory. It defaults to 5 seconds.

The `ModelRefreshVerticle` is deployed automatically by NeonBee during startup. This can be disabled by starting NeonBee
with the [CLI option](../neonbee.md#neonbee-options) `--do-not-watch-files`.

## Configuration

See [`WatchVerticle` configuration](WatchVerticle.md#configuration), which is extended by `ModelRefreshVerticle`.
