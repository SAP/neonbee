# Extended Hooks / Handlers Support RFC 001

### Contents

- [Abstract](#abstract)
- [Motivation](#motivation)
- [Technical Details](#technical-details)
- [Performance Considerations](#performance-considerations)
- [Impact on Existing Functionalities](#impact-on-existing-functionalities)
- [Open Questions](#open-questions)

### Abstract

This RFC is about executing custom code in locations governed by NeonBee. Therefore, this RFC will take a closer look at
the current support of *Routing Handlers* and *Hooks*, try to differentiate them and suggest new execution points.

### Motivation

In NeonBee we have two main mechanisms two execute custom code in locations governed by NeonBee:

- Routing Handlers
- Hooks

But when two mechanisms exists, that superficially do the same thing, the following questions inevitably arise:

- Why does two different mechanisms exist at all?
- What's the purpose of Hooks and Routing Handlers?
- In which situation it's better to use a Hook instead of a Routing Handler?
- Is there maybe a situation in which a Routing Handler **MUST** be used instead of a Hook?

This RFC defines the differences between Hooks and Routing Handlers and attempts to provide guidance as to which
mechanism should be used in which situation.

### Technical Details

NeonBee is a dataflow engine. In order to create a dataflow, NeonBee offers a public API like the *DataVerticle*
interface that must be implemented to define this new flow. This public API is very data centric, because administrative
and operational tasks like authentication or endpoint registration must be configured upfront and will be managed by
NeonBee.

However, in some scenarios it is necessary to intervene in parts of the dataflow governed by NeonBee. This can be
achieved by the usage of Hooks or Routing Handlers.

| Capability                           | Routing Handler           | Hook                         |
|--------------------------------------|---------------------------|------------------------------|
| Loadable via Classpath               | yes                       | yes                          |
| Loadable via Module                  | no                        | yes                          |
| De- Registering during Runtime       | no                        | yes                          |
| Possible Execution Points            | Very limited <sup>1</sup> | Whole NeonBee code base      |
| Execution order                      | predictable               | unpredictable <sup>2</sup>   |
| Parallel execution                   | no                        | yes                          |
| Recommended for dataflow termination | yes                       | no <sup>3</sup>              |

<sup>1</sup> Due to the nature of a Routing Handler, the execution point of a custom Routing Handler is limited to the
processing phase of incoming web requests. Currently, it is even limited to the phase before authentication happens.
ErrorHandlers are even more limited, because as of now it is only possible to define one global ErrorHandler. Vert.x
also enforces a [strict order of handler types](https://vertx.io/blog/whats-new-in-vert-x-4-3/#vertx-web):

1. PLATFORM: platform handlers (LoggerHandler, FaviconHandler, etc.)
2. SECURITY_POLICY: HTTP Security policies (CSPHandler, CorsHandler, etc.)
3. BODY: Body parsing (BodyHandler)
4. AUTHENTICATION: Authn (JWTAuthHandler, APIKeyHandler, WebauthnHandler, etc.)
5. INPUT_TRUST: Input verification (CSRFHandler)
6. AUTHORIZATION: Authz (AuthorizationHandler)

<sup>2</sup> Even if all hooks were implemented synchronously, although the hook API is asynchronous, the execution
order would be unpredictable. This is because the execution order corresponds to the registration order, which is
non-deterministic. In case it is ensured that only one hook exists for a specific *HookType*, the execution order is
predictable.

<sup>3</sup> Due to the non-deterministic execution order of Hooks, it is not predictable when the termination will
happen.

#### Missing Execution Points for Hooks

Currently, the only execution point for Hooks during an incoming web request is the HookType *ONCE_PER_REQUEST*. It
would be beneficial to add execution points in the following phases of a web request:

- INCOMING_WEB_REQUEST
    - Gets executed as soon as the request hits the Router to ensure that this Hook gets really executed for **EVERY**
      incoming request.
    - Parameters: RoutingContext, CorrelationID

- INCOMING_WEB_REQUEST_AUTHENTICATED formerly ONCE_PER_REQUEST
    - Gets executed as soon as the authentication check has been passed.
    - Parameters: RoutingContext, CorrelationID

- INCOMING_WEB_REQUEST_FAILED
    - Gets executed before the first ErrorHandler gets executed to ensure that this Hook gets really executed for **
      EVERY** failing request.
    - Parameters: RoutingContext, CorrelationID, Cause

#### Missing Execution Points for Routing Handlers

Currently, the only execution point for Routing Handlers is before the authentication handler gets executed. It would
be beneficial to add an execution point after the authentication handler. It would also be beneficial to specify
multiple ErrorHandlers.

### Performance Considerations

Depends on the custom logic that gets executed.

### Impact on Existing Functionalities

In the current document for Hooks it is mentioned that the ONCE_PER_REQUEST Hook could be used for "global authorization
checks". As described above, this is only a good idea, if exactly one Hook of this type exists. It would be better to
implement global authorization checks with a custom Handler after the internal authorization handler was executed.

In order to improve the HookType names, the HookType "ONCE_PER_REQUEST" must be renamed. The name is even wrong, because
it is not executed for all requests.

### Open Questions

Maybe the support of all custom Routing Handlers should be replaced by Hooks. Of course then Hooks would require a "
wight" to enable an ordered execution. This would have the advantage, that custom code as part of NeonBee Modules can be
executed at every execution point. It maybe even improves the perfoamce, as Hooks with the same "wight" can be executed
in parallel.
