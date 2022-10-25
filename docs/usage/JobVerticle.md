# JobVerticle

The `JobVerticle` is a Vert.x verticle that is used to schedule and execute jobs. It is meant to be extended by concrete
implementations that define the actual logic of the job to be executed.

The `JobVerticle` has a [`JobSchedule`](#JobSchedule) that determines when the job should be executed. It also has a
number of methods that can be overridden to customize its behavior. The getName method returns the name of the job,
which is used in log messages and monitoring. The execute method is called to execute the job's logic.

To use a `JobVerticle`, you would create a concrete implementation that defines the logic of the job and deploy it
to NeonBee's Vert.x instance. The `JobVerticle` will take care of scheduling and executing the job based on the specified
`JobSchedule`.

Here is an example of a simple `JobVerticle` that logs a message every minute:

```java
import io.neonbee.job.JobVerticle;
import io.neonbee.job.JobSchedule;

import java.time.Duration;

public class MyJobVerticle extends JobVerticle {
    public MyJobVerticle() {
        super(new JobSchedule(Duration.ofMinutes(1)));
    }

    @Override
    public void execute(DataContext dataContext) {
        LOGGER.info("Hello, world!");
    }
}
```

The `JobVerticle` will automatically schedule and execute the job every minute.

## JobSchedule

A JobSchedule is a class that allows you to specify when a job should be run. A job is a piece of code that is executed
at a specific time or interval. You can create a JobSchedule in a number of ways, for example:

- To run a job once immediately, you can create a `JobSchedule` without any arguments: `new JobSchedule()`.
- To run a job once at a specific time, you can pass an `Instant` object to the constructor: `new JobSchedule(Instant.
  ofEpochMilli(1234567890))`.
- To run a job at a specific interval, you can pass a `Duration` object to the constructor: `new JobSchedule(Duration.
  ofMinutes(5))`.
- To run a job at a specific interval, ending at a specific time, you can pass a `Duration` and an `Instant` object to
  the constructor: `new JobSchedule(Duration.ofMinutes(5), Instant.ofEpochMilli(1234567890))`.
- To run a job at a specific interval, starting at a specific time, you can pass an `Instant` and a `Duration` object to
  the constructor: `new JobSchedule(Instant.ofEpochMilli(1234567890), Duration.ofMinutes(5))`.
- To run a job at a specific interval, starting at a specific time and ending at a specific time, you can pass an
  `Instant`, a `Duration`, and another `Instant` object to the constructor: `new JobSchedule(Instant.ofEpochMilli(1234567890), Duration.ofMinutes(5), Instant.ofEpochMilli(1234567890))`.

You can also specify a `TemporalAdjuster` object to run the job at a custom interval. A `TemporalAdjuster` is a
functional interface that allows you to adjust a Temporal object (like an `Instant`) to a new value based on its
current value. For example, you could use a `TemporalAdjuster` to run a job every other day:

```java
TemporalAdjuster everyOtherDay = temporal -> temporal.plus(Duration.ofDays(2));
JobSchedule jobSchedule = new JobSchedule(everyOtherDay);
```

You can use a `JobSchedule` to deploy a `JobVerticle`, which is a type of Vert.x verticle that executes a job based on the
schedule. When you deploy a `JobVerticle`, it will execute the job according to the schedule you specified. You can stop a
`JobVerticle` by calling the stop() method on it.




