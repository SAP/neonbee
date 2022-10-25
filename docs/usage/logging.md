# Logging

The `LoggingFacade` interface of NeonBee provides a simple and consistent way to log messages at different levels of
severity in an application. It serves as a facade for various logging frameworks, such as `logback` or `log4j`.
This means that the implementation of the `LoggingFacade` can be swapped out with a different logging framework
without affecting the rest of the application.

To use the `LoggingFacade`, you first need to create an instance of it. You can do this using the `LoggingFacade.create()`
method, which will return an instance of `LoggingFacade` that is configured to use the default logging framework for the
application.

Once you have an instance of LoggingFacade, you can use it to log messages at different levels (e.g. `trace`,
`debug`, `info`, `warn`, `error`). For example:

```java
LoggingFacade logger = LoggingFacade.create();
logger.info("This is an info message");
logger.warn("This is a warning message");
logger.error("This is an error message");
```

These methods take a message in the form of a string, as well as optional arguments for formatting the message
using placeholders. There are also versions of these methods that take a Throwable object as an additional argument,
which can be used to log an exception along with the message. For example:

```java
// Log an exception at the error level
try {
    // Do something that might throw an exception
    doSomething();
} catch (Exception e){
    logger.error("doSomething apparently failed. The stacktrace might help to figure out the cause.", e)
}
```

In addition to the logging methods, the `LoggingFacade` interface defines methods for checking if a particular logging
level is enabled, such as `isTraceEnabled` or `isErrorEnabled`. This can be useful for avoiding the overhead of
constructing a log message if it will not be logged due to the logging level being disabled. For example:

```java
// Check if the debug level is enabled
if (logger.isDebugEnabled()) {
    // Log a message at the debug level
    logger.debug("Received request {} from client {}", requestId, clientIp);
}
```

The `LoggingFacade` interface also provides a `correlateWith` method, which allows the user to specify a correlation id
for the log messages. This correlation id will be included in the log messages as a marker, which can be useful for
tracking log messages related to a specific request or operation. For example:

```java
String correlationId = UUID.randomUUID().toString();
logger.correlateWith(correlationId).info("This is an info message with a correlation ID");
logger.correlateWith(correlationId).warn("This is a warning message with a correlation ID");
logger.correlateWith(correlationId).error("This is an error message with a correlation ID");
```

Finally, the LoggingFacade interface defines a `getName` method, which returns the name of the logger associated with
this LoggingFacade instance.
