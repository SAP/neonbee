## How To: Write an intercepting agent for debug purpose

Sometimes it might be helpful to hook into literally **any** Java class of your choice for debug purpose. This is where a [*Java Agent*](https://www.baeldung.com/java-instrumentation) will come in handy. Utilizing Gradle it is super easy to write one, here is what you need to do:

1. Create a subfolder `agent` in the root directory to put your Java Agent in
2. Use the following basic `build.gradle` file, to prepare yourself doing byte code manipulation:

   ```groovy
   plugins {
       id 'java'
   }

   repositories {
       mavenCentral()
   }

   dependencies {
       compileOnly group: 'org.slf4j', name: 'slf4j-api', version: '1.7.29'
       compileOnly group: 'net.bytebuddy', name: 'byte-buddy', version: '1.11.15'
   }

   tasks.withType(Jar) {
       manifest {
           attributes['Manifest-Version'] = '1.0'
           attributes['Premain-Class'] = 'io.neonbee.agent.AnyAgent'
           attributes['Can-Redefine-Classes'] = 'true'
           attributes['Can-Retransform-Classes'] = 'true'
       }
   }
   ```

   *Note*: How we have to define a `Premain-Class` in the manifest. We chose `io.neonbee.agent.AnyAgent`.
3. Create the respective Java file in the `src/main/java/io/neonbee/agent/AnyAgent` directory of the `agent` subdirectory. For example:

   ```java
   package io.neonbee.agent;

   import static net.bytebuddy.matcher.ElementMatchers.isConstructor;

   import java.lang.instrument.Instrumentation;
   import java.lang.reflect.Constructor;

   import net.bytebuddy.agent.builder.AgentBuilder;
   import net.bytebuddy.asm.Advice;
   import net.bytebuddy.asm.Advice.Argument;
   import net.bytebuddy.asm.Advice.Origin;
   import net.bytebuddy.asm.Advice.This;
   import net.bytebuddy.matcher.ElementMatchers;

   public class AnyAgent {
       public static void premain(String arguments, Instrumentation instrumentation) {
           new AgentBuilder.Default().type(ElementMatchers.named("io.vertx.core.impl.VertxImpl"))
                   .transform((builder, typeDescription, classLoader, module) -> builder
                           .visit(Advice.to(VertxImplConstructorInterceptor.class).on(isConstructor())))
                   .installOn(instrumentation);

           new AgentBuilder.Default().type(ElementMatchers.named("io.vertx.core.impl.VertxThread"))
                   .transform((builder, typeDescription, classLoader, module) -> builder
                           .visit(Advice.to(VertxThreadConstructorInterceptor.class).on(isConstructor())))
                   .installOn(instrumentation);
       }

       public static class VertxImplConstructorInterceptor {
           @Advice.OnMethodExit
           public static void intercept(@Origin Constructor<?> m, @This Object inst) throws Exception {
               new IllegalStateException("VERTX IMPL INIT!!").printStackTrace();
           }
       }

       public static class VertxThreadConstructorInterceptor {
           @Advice.OnMethodExit
           public static void intercept(@Origin Constructor<?> m, @This Object inst, @Argument(1) String name)
                   throws Exception {
               new IllegalStateException("VERTX THREAD INIT!! " + name + " (" + ((Thread) inst).getId() + ")")
                       .printStackTrace();
           }
       }
   }
   ```
4. Next we have to add the following plugin into our root projects `build.gradle`s `plugin { ... }` block:

   ```groovy
   id 'com.zoltu.application-agent' version '1.0.14'
   ```

   Add the following dependency to our `build.gradle`s `dependencies { ... }`:

   ```groovy
   agent project(':agent')
   ```

   And add the following `include` statement into our `settings.gradle`:

   ```groovy
   include 'agent'
   ```

At the next start of the virtual machine, e.g. after running `./gradlew test` the instrumentation will be performed and the code associated to your `premain` method will get executed.
