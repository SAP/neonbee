# Development Conventions and Guidelines

## Guidelines

- [Development Conventions and Guidelines](#development-conventions-and-guidelines)
  - [Avoid final for local variables](#avoid-final-for-local-variables)
  - [Naming convention for `NeonBee`, variables and using `NeonBee` in class names](#naming-convention-for-neonbee-variables-and-using-neonbee-in-class-names)
  - [Referring to `NeonBee` vs. `Vertx` in method signatures / `NeonBee.get(Vertx)` or `NeonBee.getVertx()`](#referring-to-neonbee-vs.-vertx-in-method-signatures--neonbee.getvertx-or-neonbee.getvertx?)
  - [`Context` parameters in method signatures](#context-parameters-in-method-signatures)
  - [Usage of Correlation ID in method signatures](#usage-of-correlation-id-in-method-signatures)

## Avoid final for local variables

In our opinion the only advantage of declaring local variables as final arises, when the type is primitive or immutable. In all other situations, the final modifier is worthless.

It is also not required to be thread safe, because Vert.x is handling this. It is not necessary to use variables in lambda expressions, because with Java 8+ these variables implicitly become final.

But adding final to declarations comes with disadvantages:

- More effort when overriding is desired.
- Increase of complexity, declarations should be short, simple and clean.

## Naming convention for `NeonBee`, variables and using `NeonBee` in class names

There is only one correct spelling for NeonBee which is "**NeonBee**", with a capital N and a capital B, without a whitespace between Neon and Bee, **NeonBee**:

- **Wrong:**
  - Neonbee
  - neonbee
  - Neon Bee
  - neon bee
  - Neon-Bee
  - NeonB
- **Right:**
  - NeonBee

Developing in and with NeonBee, you often need to refer to the `NeonBee` instance either through a local variable, a NeonBee themed class name or a parameter in a method signature. To keep it convenient and consistent, there is only one correct convention on how to name a variable of type `NeonBee` and it is: **`neonBee`**. A couple of examples:

- **Wrong:**
  - `NeonBee neonbee = ...`
  - `NeonBee NeonBee = ...`
  - `NeonBee neonB = ...`
  - `NeonBee nB = ...`
  - `NeonBee nb = ...`
  - `Future<NeonBee> neonbeeFuture = ...`
  - `Future<NeonBee> nbFut = ...`
  - `public void start(NeonBee nb) { ... }`
- **Right:**
  - `NeonBee neonBee = ...`
  - `Future<NeonBee> neonBeeFuture = ...`
  - `public void start(NeonBee neonBee) { ... }`

A notable corner case and exception to the above rule, is when using NeonBee in a static final variable name. According to our [Checkstyle](code_quality.md) rules, they have to be defined in upper case and separated by underscores. However we avoid spelling NeonBee as `NEON_BEE` and go for `NEONBEE` instead, so:

- **Wrong:**
  - `NEON_BEE`
  - `NEON_BEE_CONSTANT`
  - `NEONB`
  - `NB`
- **Right:**
  - `NEONBEE`
  - `NEONBEE_CONSTANT`

It should generally be *avoided* to use `NeonBee` in any class names. Notable exceptions are classes in the `io.neonbee` package, such as `NeonBeeDeployable` and `NeonBeeOptions`. As the class name is already scoped via the package name, to be a NeonBee class, a double mention is not needed. Examples:

- **Wrong:**
  - `class NeonBeeDataPackage`
  - `class NeonBeeJobHandler`
  - `class NeonBeeVerticle`
- **Right:**
  - `class DataPackage`
  - `class JobHandler`
  - `class PurposeVerticle`

## Referring to `NeonBee` vs. `Vertx` in method signatures / `NeonBee.get(Vertx)` or `NeonBee.getVertx()`?

In Vert.x related applications it is very common to require a Vert.x instance, especially when performing essentially any asynchronous activity. One can either retrieve such a already started Vert.x instance, by calling `Vertx.currentContext().owner()` when on being on a `VertxThread`, such as when in a verticle or worker context, but a more common pattern is that the Vert.x instance is provided by a local field or method, such as the `protected final Vertx vertx` field and the `public Vertx getVertx()` method in verticles or by being passed in as the first parameter of a method signature, which especially holds true for static methods, e.g. `public static void runAsync(Vertx vertx)`. For method signatures in particular, the only accepted convention is to have the Vert.x object passed in as *the first* of all parameters and denoted exactly how given above `Vertx vertx`, so:

- **Wrong:**
  - `void runAsync(Vertx vert_x)`
  - `void runAsync(Vertx v)`
  - `void runAsync(String name, Vertx vertx)`
- **Right:**
  - `void runAsync(Vertx vertx)`
  - `void runAsync(Vertx vertx, String name)`

So naturally throughout NeonBee, we often times refer to Vert.x ourselves, applying any of the above patterns. NeonBee, similar to Vert.x, comes with a own instance of `NeonBee`. A NeonBee instance has a direct relation to at least one `Vertx` instance. Also similar to Vert.x, you can get access to the current NeonBee instance via `NeonBee.get()` (as a equivalent to `Vertx.vertx()`). You also always can retrieve the associated Vert.x instance from NeonBee via the instance method `neonBee.getVertx()` or the NeonBee instance from a given Vert.x instance calling `NeonBee.get(vertx)`. Calling `NeonBee.get()` is equivalent of getting Vert.x through the current context and then passing it to `NeonBee.get(vertx)`.

As we learned, NeonBee and Vert.x instances can be retrieved from each other interchangeably, the question arises, which reference should I use in my method signatures? Should I use:

1. `void runAsync(NeonBee neonBee)` and if I need a Vert.x instance I call `neonBee.getVertx()`,
2. `void runAsync(Vertx vertx)` and if I need a NeonBee instance I call `NeonBee.get(vertx)`,
3. should I not pass it at all and rely on that I am being called on a Vert.x context and use `NeonBee.get()` or `Vertx.currentContext().owner()`,
4. or should I pass both `void runAsync(Vertx vertx, NeonBee neonBee)`, or `void runAsync(NeonBee neonBee, Vertx vertx)` in case I need it?

Well let's rule out the obvious answers first: **Do never** pass both a Vert.x and a NeonBee instance in any of the two orders, or no instance, in case there is no local reference to a NeonBee or Vert.x instance already available, such as in verticles for example. So 3. and 4. are out.

In regards to 1. or 2. the answer is: *It depends!* Generally we develop NeonBee, thus `NeonBee` is our leading instance and should be favored / preferred when being used in public APIs and the like. Similar to the convention when using Vert.x in a method signature however, it *must* always be the first parameter of a signature, if used, so:

- **Wrong:**
  - `void retrieveFrom(NeonBee neonBee)`
  - `void retrieveFrom(NeonBee nB)`
  - `void retrieveFrom(String name, NeonBee neonBee)`
- **Right:**
  - `void retrieveFrom(NeonBee neonBee)`
  - `void retrieveFrom(NeonBee neonBee, String name)`

When it comes to internal methods, or methods which generally only require a Vert.x instance to operate, the answer is not too clear. As mentioned above, we develop NeonBee and thus, NeonBee should get preference if both are needed. But what is for methods that *would* work without a NeonBee as well? Helper stuff like `Future<Buffer> readFile(...)` should it be `Future<Buffer> readFile(Vertx, Path)` or `Future<Buffer> readFile(NeonBee, Path)`? Well we need to consider the following:

Often times NeonBee extends Vert.x's functionalities, by providing "better defaults", configuration options via configuration files or build-in functionalities, like a simplified communication between verticles. These functions will not work, if no NeonBee instance is started. Thus the interfaces should reflect that as well. In our example above, in case we read the file, from NeonBee's working directory and thus need to access the `NeonBeeOption`s, it makes sense to favour the `Future<Buffer> readFile(NeonBee, Path)` signature. In case it is a convenience wrapper around Vert.x's own file read, `Future<Buffer> readFile(Vertx, Path)` is fine in that case.

As a rule of thumb, maybe a couple of more arguments and examples pro. / con. using NeonBee vs. Vert.x, to help your decision process:

- We develop NeonBee, so NeonBee should be the leading object
- It is easier and more efficient to get Vert.x from NeonBee (`neonBee.getVertx()`), than it is vice-versa (`NeonBee.get(vertx)`, which requires access to a global hash map).
- Often times it makes more sense, from an end-user perspective:
  - Vert.x doesn't deal with model files, thus `EntityModelManager.registerModels` registers these models with NeonBee and not with Vert.x, thus the only right option in this case is using `EntityModelManager.registerModels(NeonBee, ...)`.
  - NeonBee has an own deployment logic, scanning the class path, registering verticles and structuring verticles and models in NeonBee modules. Thus deploying them via the `Deployable` interface, `Deployable.deploy` deploys something to a NeonBee instance (like again for models, or modules) not only to Vert.x. So even if `DeployableVerticle.deploy(Vertx)` would make sense `DeployableModels.deploy(Vertx)` *would not* make sense from an end-users perspective (even though we *could* map to NeonBee), thus the only right choice is to use `Deployable.deploy(NeonBee)`  in this case.
- At the moment NeonBee deals with one exclusive Vert.x instance, in future NeonBee might need to deal with multiple Vert.x's at the same time, e.g. for clustering. This can be reflected in the `NeonBee.getVertx` method, but not in the `NeonBee.get(Vertx)` one, because a Vert.x instance will always have a one-to-one relation to a NeonBee instance.

## `Context` parameters in method signatures

Similar to how common it is to deal with Vert.x / NeonBee instances and that it makes sense to put them as the first parameter into any method signature if used:

- `void readFile(Vertx vertx, Path file)`
- `void deployVerticle(NeonBee neonBee, Verticle verticle)`

When developing with Vert.x another very common term is a `Context` object. So an object that is used to retrieve context information about, routing, the thread or in NeonBee's case data in form of our `DataContext`. Similar to the NeonBee / Vert.x instances itself, these context objects often times need to be "dragged-trough" different methods. For better readability, we also have a convention for contexts in signatures. In contrast to the instance variables, context parameters should always go ***last*** into a method signature. Thus:

- **Wrong:**
  - `void retrieveData(DataContext context, DataRequest request)`
  - `void retrieveData(DataContext context, NeonBee neonBee)`
  - `void retrieveData(NeonBee neonBee, DataContext context, DataRequest request)`
  - `void retrieveData(DataContext dcontext, DataRequest request, RoutingContext rcontext)`
- **Right:**
  - `void retrieveData(DataContext context)`
  - `void retrieveData(DataRequest request, DataContext context)`
  - `void retrieveData(NeonBee neonBee, DataContext context)`
  - `void retrieveData(NeonBee neonBee, DataRequest request, DataContext context)`
  - `void retrieveData(DataRequest request, DataContext dataContext, RoutingContext routingContext)`

As for the last example, providing a method with *multiple* contexts to deal with, should generally be tried to be avoided, as dealing with multiple context objects makes it very confusing for the end-user, which values to be retrieved from which context object. Rather think if one context should be made part of the other context.

## Usage of Correlation ID in method signatures

Inside NeonBee we took measures to correlate log messages, for instance with a request. This allows for much easier log analysis, especially in highly distributed applications, as developed with NeonBee. Correlated messages will be assigned a so called `correlationId`, which is then part of every log message printed to the logs. This is currently done by using a `LoggingFacade` object and by using its `LoggingFacade.correlateWith` method. As for how the correlation from log messages to the ID works, the `correlateWith` method has to be repetitively called for every log message. Another drawback of this approach is, that often times we *pollute* method signatures with just another `String correlationId` parameter, that we pass through to every sub-sub-sub place, that it needs to be, in order to get logged. So what you will see inside of many NeonBee's own methods is a picture that looks like this:

 - `void getFile(Vertx vertx, Path path, String correlationId)`
 - `void refreshData(NeonBee neonBee, String correlationId)`

Going forward we would like to *no longer* propagate the correlation ID using method signatures, but switch to a context based approach, that is entirely handled by NeonBee. So essentially NeonBee will take care of propagating the correlation ID of a given message, throughout its lifetime, e.g. after it is being send via the event bus. Thus the correlation ID can be retrieved from the context by the `LoggingFacade` and no longer needs to be passed in via a `correlateWith` method call. This will make signatures much more clean to use.

Also in many other cases, correlating log messages makes it only slightly more convenient to analyzing the log messages. Say for example when deploying verticles through the `DeployableVerticle` classes. Sure, it'd make sense to correlate all given messages with an ID, however, `DeployableVerticle` already uses a own logger instance, meaning everything happening is already grouped by the `DeployableVerticle` class. And looking at all the log messages, it will get clear from the context, which log messages correlate to each other. So there is no need of "polluting" the method signatures of `DeployableVerticle` just to path through the correlation ID. If, for any reason, there could be confusion in what failed, e.g. because multiple deployments ran in parallel, the log messages should get generally improved, e.g.:

- `Deploying verticle 'ABC' started ...`
- `Deploying verticle 'XYZ' started ...`
- `Deploying verticle failed`
- `Deploying verticle succeeded`

These log messages are ambiguous, because the order of log messages doesn't necessarily correlate, which of the deployments failed and / or succeeded, but rather, which of the operations finished more quickly. In this case "correlation" would be the lazy way of solving the ambiguity, however it is much better to provide the context of the log messages yourself, thus, the single log message will get more clean:

- `Deploying verticle 'ABC' started ...`
- `Deploying verticle 'XYZ' started ...`
- `Deploying verticle 'XYZ' failed`
- `Deploying verticle 'ABC' succeeded`

No need for a correlation ID in this case.