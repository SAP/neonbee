# Hooks

A hook is a method that is annotated with `@Hook` and is called at a specific point in the lifecycle of a NeonBee
instance. These points, or [hook types](#hook-types), are represented by the `HookType` enum and include events such
as _before startup_, _after startup_, _before shutdown_, and more. Hooks are useful for implementing cross-cutting
concerns, such as logging or global authorization checks, that need to be executed at specific times in an application's
execution. Hooks are registered in a central place, the `HookRegistry`, which is responsible for managing the
registration and execution of hook methods within a NeonBee application.

To register a hook, you need to do the following:

1. Implement the hook method according to the specified signature:

    ```java
    public void hookMethod(NeonBee neonbee, HookContext context, Promise<Void> hookPromise)
    ```

2. Annotate the hook method with `@Hook` and specify the type of hook it is using the value attribute. For example:

    ```java
   public class MyHookClass {

        @Hook(HookType.AFTER_STARTUP)
        public void myStartupHook(NeonBee neonbee, HookContext context, Promise<Void> hookPromise) {
            // code for startup hook goes here
            hookPromise.complete();
        }
    }
    ```

3. The hook method should perform its task and then either call `hookPromise.complete()` to indicate that it has
   completed successfully or `hookPromise.fail(Throwable)` to indicate that it has failed.
4. In order for NeonBee to discover and register the hook, you need to use the `HookRegistry` to register the hook
   programmatically. To do this, you can use the `registerHooks` method and pass in the class containing the hook
   method. The `HookRegistry` will create a new instance of the class and register all the hook methods contained
   within it.

For example:

```java
HookRegistry hookRegistry = neonBee.getHookRegistry();
hookRegistry.registerHooks(MyHookClass.class, "correlationId");
```

Note: The order in which the hooks are called is **non-deterministic**, so you should not rely on the execution order of
hooks.

## Hook Types

* `BEFORE_BOOTSTRAP`: This hook is called when a new NeonBee instance is created, before the NeonBee configuration is
  loaded, or any configurations on the event bus have been done, or system/web verticles have been deployed. Note: For
  security reasons, this hook requires the module to be present in the class path of NeonBee on startup. Placing a
  module in the work directories/modules directory will cause the hook NOT to be called.
* `AFTER_STARTUP`: This hook is called after the NeonBee instance has been initialized successfully (all
  configurations were loaded, system/web verticles have been deployed, the event bus was configured).
* `ONCE_PER_REQUEST`: This hook is called once for each web request before it is dispatched to the appropriate
  endpoint for handling. Note: This hook is intended for the implementation of cross-cutting concerns (for example,
  logging or global authorization checks). In case this hook handles the provided routing context in the HookContext,
  it may not call the hookPromise, to break the chain.
* `BEFORE_SHUTDOWN`: This hook is called before the associated Vert.x instance to a NeonBee object is closed/shut down.
* `NODE_ADDED`: This hook is called when a node has been added to the cluster.
* `NODE_LEFT`: This hook is called when a node has left the cluster.
