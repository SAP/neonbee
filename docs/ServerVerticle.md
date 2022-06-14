# ServerVerticle configuration

## Configuration

### Handler factories

Handler factories are used to register routing handlers on the root router in the ServerVerticle. A handler factory must
implement the `io.neonbee.internal.handler.factories.RoutingHandlerFactory` interface and must be configured in the
handlerFactories section of the ServerVerticle configuration. The configured handlerFactories are loaded and added in
the configured order.
The order of handler factories has to take the priority of the returned handlers into account.
See `io.vertx.ext.web.impl.RouteState.weight` If the order is violated, an Exception is thrown.

#### Default configuration

```yaml
handlerFactories:
- io.neonbee.internal.handler.factories.LoggerHandlerFactory
- io.neonbee.internal.handler.factories.InstanceInfoHandlerFactory
- io.neonbee.internal.handler.factories.CorrelationIdHandlerFactory
- io.neonbee.internal.handler.factories.TimeoutHandlerFactory
- io.neonbee.internal.handler.factories.SessionHandlerFactory
- io.neonbee.internal.handler.factories.CacheControlHandlerFactory
- io.neonbee.internal.handler.factories.DisallowingFileUploadBodyHandlerFactory
```
