# LoggerManagerVerticle

The `LoggerManagerVerticle` is a verticle that manages the logging settings for a NeonBee instance. It is a
`DataVerticle` that allows the logging settings to be retrieved and updated via HTTP requests. It also listens for
update requests through the event bus and applies the updated logging settings to all instances in the cluster. As
it is also a system verticle, it will be automatically deployed on all NeonBee instances during bootstrap.

To use the `LoggerManagerVerticle`, you will need to deploy it in your NeonBee application and make HTTP requests to the
specified endpoint to retrieve or update the logging settings. The `loggers` query parameter can be used to retrieve
the logging settings for specific logger names, and the `local` query parameter can be used to specify that the update
should only be applied to the current instance, rather than distributed to the entire cluster. In more advanced
usage, the `LoggerManagerVerticle` also supports updating the logging settings through the event bus by publishing a
message to the event bus address `LoggerManagerVerticleChangeLogLevel` with the updated settings as the message body.

## Example

Here is an example how you could send an HTTP request to update the logging settings using the `LoggerManagerVerticle`
with a curl command:

```shell
curl -X PUT -H "Content-Type: application/json" -d '[{"name":"com.example.MyLogger","configuredLevel":"DEBUG"}]' \
      http://localhost:8080/raw/neonbee/LogLevel
```

This would send an HTTP PUT request to the endpoint where the `LoggerManagerVerticle` is registered with the updated
logging configurations for `com.example.MyLogger` in the request body. Note, that multiple logger configurations can
be passed to update multiple logger settings.

You can also update the logging settings through the event bus by publishing a message with the updated settings as the
message body:

```java
vertx.eventBus().publish("LoggerManagerVerticleChangeLogLevel", updates);
```

This would publish a message to the `LoggerManagerVerticleChangeLogLevel` address with the updated logging settings in
the message body, which the `LoggerManagerVerticle` would receive and apply to all instances in the cluster.