# ServerVerticle

The `ServerVerticle` is a verticle that exposes endpoints using the HTTP(S) protocol. NeonBee deploys the
`ServerVerticle` automatically if the `WEB` [profile](./../neonbee.md#neonbee-profiles) is active. When the
`ServerVerticle` is started, it creates a router and mounts the specified endpoints onto it. It then creates an HTTP
server using the router. When the `ServerVerticle` is stopped, the HTTP server is closed.

## Configuration

- The config file must be placed in the working directory inside a directory called `config` and the filename must be matching the full qualified class name of the verticle, i.e. **`config/io.neonbee.internal.verticle.ServerVerticle.yaml`**.
- The top-level `config` key is mandatory.

The `ServerVerticle` extends the `HttpServerOptions` of Vert.x, which can be configured on the top-level `config`
property. Some defaults are overridden, and additional config options are provided (see below).

| Property                                      |  Type   | Required |                      Default                      | Description                                                                                |
|:----------------------------------------------|:-------:| :------: | :-----------------------------------------------: |:-------------------------------------------------------------------------------------------|
| `port`                                        | integer |    No    |                      `8080`                       | Sets the port on which the application server listens.                                     |
| `useAlpn`                                     | boolean |    No    |                      `true`                       | Whether to use application-layer protocol negotiation or not.                              |
| [`sessionHandling`](#sessionhandling)         | string  |    No    |                      `NONE`                       | Sets the type of session handling. Must be one of one of: `NONE`, `LOCAL`, or `CLUSTERED`. |
| `sessionTimeout`                              | integer |    No    |                       `30`                        | Session timeout in minutes.                                                                |
| `sessionCookieName`                           | string  |    No    |               `neonbee-web.session`               | Sets the name of the session cookie.                                                       |
| `sessionCookiePath`                           | string  |    No    |                       `/`                         | Sets the path of the session cookie.                                                       |
| `secureSessionCookie`                         | boolean |    No    |                      `false`                      | Whether to set the `secure` flag of the session cookie.                                    |
| `httpOnlySessionCookie`                       | boolean |    No    |                      `false`                      | Whether to set the `HttpOnly` flag of the session cookie.                                  |
| `sessionCookieSameSitePolicy`                 | string  |    No    |                      `null`                       | Which `SameSite` policy to use for the cookie. One of: `null`, `NONE`, `STRICT` or `LAX`.  |
| `minSessionIdLength`                          | integer |    No    |                       `32`                        | The minimum length of the session ID.                                                      |
| `decompressionSupported`                      | boolean |    No    |                      `true`                       | Enables server-side decompression of request bodies.                                       |
| `compressionSupported`                        | boolean |    No    |                      `true`                       | Enables server-side support for compression.                                               |
| `compressionLevel`                            | integer |    No    |                      `true`                       | Sets the level of compression, if compression is enabled.                                  |
| [`correlationStrategy`](#correlationstrategy) | integer |    No    |                 `REQUEST_HEADER`                  | Sets the correlation strategy. Must be one of `REQUEST_HEADER` or `GENERATE_UUID`.         |
| `timeout`                                     | integer |    No    |                       `30`                        | Sets the router timeout in seconds.                                                        |
| `timeoutStatusCode`                           | integer |    No    |                       `504`                       | Sets the HTTP status code for the timeout.                                                 |
| `maxHeaderSize`                               | integer |    No    |                      `8192`                       | Sets the maximum length of all HTTP headers in bytes.                                      |
| `maxInitialLineLength`                        | integer |    No    |                      `4096`                       | Sets the maximum initial line length of the HTTP header.                                   |
| `errorHandler`                                | integer |    No    | `io.neonbee.internal.handler.DefaultErrorHandler` | Configures the error handler. Must be a full qualified class name.                         |
| [`endpoints`](#endpoints)                     |  array  |    No    |                     See below                     | Configures the endpoints.                                                                  |
| [`authenticationChain`](#authenticationchain) |  array  |    No    |                       `[]`                        | Configures the authentication chain.                                                       |
| [`handlerFactories`](#handlerfactories)       | object  |    No    |                     See below                     | Registers routing handlers on the root router.                                             |
| [`cors`](#cors)                               | object  |    No    |                     See below                     | Configures the CORS handler.                                                               |

---

### `sessionHandling`

- `NONE`: No session handling.
- `LOCAL`: Local session handling or in clustered operation on each cluster node. Sessions are stored locally in a shared local map and only available on this instance.
- `CLUSTERED`: Clustered session handling in a shared map across the whole cluster.

---

### `correlationStrategy`

- `GENERATE_UUID`: Generates a random UUID for every incoming request.
- `REQUEST_HEADER`: Tries to obtain a correlation id from the request header key `X-CorrelationID`. If fetching the correlation id is not possible, a random UUID is generated and used.

---

### `endpoints`

The following properties can be set on the endpoint config.

| Property                                |  Type   | Required | Default  | Description                                                                                                                         |
| :-------------------------------------- | :-----: | :------: | :------: | :---------------------------------------------------------------------------------------------------------------------------------- |
| `type`                                  | string  |   Yes    |          | The full qualified name of the endpoint.                                                                                            |
| `enabled`                               | boolean |    No    |  `true`  | Enables the endpoint.                                                                                                               |
| `basePath`                              | string  |   Yes    |          | The base path to map this endpoint.                                                                                                 |
| `authenticationChain`                   | object  |    No    |   `~`    | Configures the authentication chain. Setting this to an empty list will result in no authentication check performed.                |
| `uriConversion`                         | string  |    No    | `STRICT` | Sets namespace and service name URI mapping. Must be one of `STRICT`, or `LOOSE` based on CDS. Only supported by `ODataV4Endpoint`. |
| `exposeHiddenVerticles`                 | boolean |    No    | `false`  | Whether hidden verticles should be exposed or not.                                                                                  |
| [`exposedVerticles`](#exposedverticles) | object  |    No    |   `~`    | Block and Allow list of verticles to expose. Only supported by `ODataV4Endpoint` and `RawEndpoint`.                                 |

### `cors`

The following properties can be set on the cors config. If no `origins` **and** no `relativeOrigins` is set, the CORS handler won't be added.

| Property           |  Type   | Required |  Default   | Description                                            |
|:-------------------|:-------:|:--------:|:----------:|:-------------------------------------------------------|
| `enabled`          | boolean |    No    |  `false`   | Enables the CORS handler.                              |
| `origins`          |  array  |    No    |    `~`     | Set the list of allowed static origins.                |
| `relativeOrigins`  |  array  |    No    |    `~`     | Set the list of allowed relative origins.              |
| `allowedMethods`   |  array  |    No    |    `~`     | Set a set of allowed methods.                          |
| `allowedHeaders`   |  array  |    No    |    `~`     | Set a set of allowed headers.                          |
| `exposedHeaders`   |  array  |    No    |    `~`     | Set a set of exposed headers.                          |
| `maxAgeSeconds`    | integer |    No    |    `~`     | Set how long the browser should cache the information. |
| `allowCredentials` | boolean |    No    |  `false`   | Set whether credentials are allowed or not.            |

#### `exposedVerticles`

A block / allow list of verticles to expose via this endpoint. Defaults to empty, meaning that all verticles are exposed.

| Property | Type  | Required | Default | Description                                                                       |
| :------- | :---: | :------: | :-----: | :-------------------------------------------------------------------------------- |
| `block`  | array |    No    |  `[]`   | A list of regular expressions matching verticle names that should not be exposed. |
| `allow`  | array |    No    |  `[]`   | A list of regular expressions matching verticle names that should be exposed.     |

**Default Configuration of `endpoints`:**

```yaml
config:
  endpoints:
      # provides an OData V4 compliant endpoint, for accessing entity verticle data
    - type: io.neonbee.endpoint.odatav4.ODataV4Endpoint
      enabled: true
      basePath: /odata/
      authenticationChain: ~
      uriConversion: STRICT
      exposedVerticles:
          block: [any_allow_list_of_regexp_here]
          allow: [any_block_list_of_regexp_here]

      # provides a REST endpoint (JSON, text, binary), for accessing data verticles
    - type: io.neonbee.endpoint.raw.RawEndpoint
      enabled: true
      basePath: /raw/
      authenticationChain: ~
      exposeHiddenVerticles: false
      exposedVerticles:
          block: [any_allow_list_of_regexp_here]
          allow: [any_block_list_of_regexp_here]

      # provides a Prometheus scraping endpoint for Micrometer.io metrics
    - type: io.neonbee.endpoint.metrics.MetricsEndpoint
      enabled: true
      basePath: /metrics/
      authenticationChain: ~

      # provides a Health endpoint
    - type: io.neonbee.endpoint.health.HealthEndpoint
      enabled: true
      basePath: /health/
      authenticationChain: ~
```

---

### `authenticationChain`

The definition of the authentication chain, which can be used to secure any configured [endpoint](#endpoints).

| Property   |  Type  | Required | Default | Description                                                  |
| :--------- | :----: | :------: | :-----: | :----------------------------------------------------------- |
| `type`     | string |   Yes    |         | The type of the authentication handler supported by NeonBee. |
| `provider` | object |   Yes    |         | The type of the authentication handler supported by NeonBee. |

Additional authentication handler config can be derived from the specific handler implementations.

#### `authenticationChain[].type`

The type of the authentication handler supported by NeonBee. The following options can be set.

- `BASIC`: HTTP Basic authentication.
- `DIGEST`: HTTP Basic authentication using `.digest` file format to perform authentication.
- `JWT`: JSON Web Token (JWT) authentication.
- `OAUTH2`: OAuth2 authentication. Suitable for AuthCode flows.
- `REDIRECT`: Handle authentication by redirecting user to a custom login page.

#### `authenticationChain[].provider`

NeonBee creates a Vert.x `AuthenticationProvider` based on this config.

| Property |  Type  | Required | Default | Description                                                   |
| :------- | :----: | :------: | :-----: | :------------------------------------------------------------ |
| `type`   | string |   Yes    |         | The type of the authentication provider supported by NeonBee. |

Additional authentication provider config can be derived from the specific handler implementations.

##### `authenticationChain[].provider.type`

The type of the authentication provider supported by NeonBee. The following options can be set.

- `HTDIGEST`: An authentication provider using a `.htdigest` file as store.
- `HTPASSWD`: An authentication provider using a `.htpasswd` file as store.
- `JWT`: JSON Web Token (JWT)-based authentication provider. The additionalConfig must fit to `io.vertx.ext.auth.jwt.JWTAuthOptions`.
- `OAUTH2`: OAuth2 based authentication provider. The additionalConfig must fit to `io.vertx.ext.auth.oauth2.OAuth2Options`.

---

### `handlerFactories`

Handler factories are used to register routing handlers on the root router in the `ServerVerticle`. A handler factory must
implement the [`io.neonbee.internal.handler.factories.RoutingHandlerFactory`](./src/main/java/io/neonbee/internal/handler/factories/RoutingHandlerFactory.java)
interface and must be configured in the handlerFactories config option of the `ServerVerticle` configuration. The configured
handlerFactories are loaded and added in the configured order.

The order of the handler factories must take into account the priority of the returned handlers. See [`io.vertx.ext.web.impl.RouteState.weight`](https://github.com/vert-x3/vertx-web/blob/937c60b86548ab51fd2c2f64ff81fc723289dde6/vertx-web/src/main/java/io/vertx/ext/web/impl/RouteState.java#L56) for more details. If the order is violated, an Exception is thrown.

**Default Configuration of `handlerFactories`:**

```yaml
config:
  handlerFactories:
    - io.neonbee.internal.handler.factories.LoggerHandlerFactory
    - io.neonbee.internal.handler.factories.InstanceInfoHandlerFactory
    - io.neonbee.internal.handler.factories.CorrelationIdHandlerFactory
    - io.neonbee.internal.handler.factories.TimeoutHandlerFactory
    - io.neonbee.internal.handler.factories.SessionHandlerFactory
    - io.neonbee.internal.handler.factories.CacheControlHandlerFactory
    - io.neonbee.internal.handler.factories.CorsHandlerFactory
    - io.neonbee.internal.handler.factories.DisallowingFileUploadBodyHandlerFactory
```
