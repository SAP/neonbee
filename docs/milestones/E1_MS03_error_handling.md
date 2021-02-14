## 🚀 Milestone: Improve error handling and logging
 🌌 Road to Version 1.0

### 📝 Milestone Description

This milestone is all about improvements to the error handling, traceability and readability of log messages and monitoring.

Error handling in the Data/EntityVerticle communication should be re-evaluated. Currently errors sometimes are very hard to track and it's unclear how they are generally handled: The data part of NeonBee provides a specific DataException class, Data/EntityVerticles may return with failedFutures or throw runtime exceptions of any kind. So far there is no clean concept on how those types of errors are handled though out NeonBee or propagated to / exposed by the endpoints. Additionally Vert.x covers some of the failures with its `NoStackTraceThrowable`. Generally it should be checked whether its often enough to log a `NoStackTraceThrowable` if a cause with a full stack trace is provided already.

Also logs in NeonBee are currently not very consistent to each other. That is on the one hand side from a log-level point of view, as there is no clean guideline on which kinds of log qualify as which. This means some logs are printed on a info level, clearly being used for tracing / debugging, while other logs are on the debug level, clearly being relevant to the end-user of NeonBee thus more likely being info logs. Similarly on the other hand side, the format of log messages is more or less arbitrary at the moment, sometimes and sometimes not ended with a colon, technically written error message or user language, etc. A general guideline should be maintained and used throughout NeonBee.

Traceability of log messages is provided by the correlation id in NeonBee. However the asynchronous communication sometimes makes it hard to trace certain messages throughout a cluster anyways. For this purpose a DataVerticle tracing logging has been recently introduced. However after writing a first log analysis tool locally, a lot of redundant information is logged on an info log level, so either the format of the log messages, the level of logging or the whole concept itself should be reevaluated, maybe in favour of the new tracing API introduced by Vert.x 4.

Last but not least monitoring is a next to completely neglected topic in NeonBee so far. The idea having a reactive dataflow engine, without the capability of knowing if scaling is necessary, how much the event bus is occupied, etc. strikes as a big blocker moving forward. MicroMeter integration or other means reading system metrics, should provide an insight into the resource consumption / system utilization and again should expose their information via an API.

For everything said above a convenient API interface should be provided to a future admin cockpit implementation. Useful endpoints include: reading log messages and setting log levels, fetching logs of a certain correlation id and analyzing it for metrics like communication failures, enable / disable tracing for single or multiple cluster nodes, reading monitoring metric data and the like.

## Tasks / Features

- Clean concept for error handling in the inter-verticle communication and towards the endpoints (maybe using the hins of the first milestone in this epic)
- Provide guidelines when which log level should be used and how log messages are supposed to be structured / written
- Improve traceability of log messages, for instance evaluating the new tracing API of Vert.x 4
- Improve tracing support of DataVerticles by improving the log format and log level, or deprecating it in favour of the new tracing API
- Provide monitoring metrics for critical system measures and expose them via a API
- Make setting the log level, reading logs, enabling / disabling tracing, etc. available to a future admin ui via an API endpoint of some sorts