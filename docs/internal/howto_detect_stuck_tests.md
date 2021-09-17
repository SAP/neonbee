## **How To:** Detect stuck tests

With asynchronous execution of tests a missed `@Timeout` annotation and/or not signaling completion could easily lead to tests getting stuck. With the amount of unit tests, it can be hard to determine which tests get stuck when. A easy *non-intrusive* way of determining which tests are currently running / stuck, without having to enable full `showStandardStreams` in `build.gradle`, is to add a `beforeTest` and `afterTest` closure in `build.gradle`. To do so add the following variable above the `test { ... }` block:

```groovy
def runningTests = java.util.Collections.synchronizedSet(new java.util.HashSet())
```

Into the `test { ... }` block then add:

```groovy
beforeTest { descriptor ->
    runningTests.add(descriptor)
    logger.lifecycle("Start execution of test: " + descriptor)
    logger.lifecycle("Currently running: " + runningTests)
}
afterTest { descriptor, result ->
    runningTests.remove(descriptor)
    logger.lifecycle("Finished execution of test: " + descriptor)
    logger.lifecycle("Still running: " + runningTests)
}
```

To report which tests are currently still running. As soon as as a single stuck test has been identified, it often helps to log its console outputs:

```groovy
onOutput { descriptor, event ->
    if (descriptor.className == 'io.neonbee.test.AnyTestClassName') {
        logger.lifecycle("debug: " + event.message)
    }
}
```

Alternatively all standard output / error logs can be enabled using the following `testLogging` property:

```groovy
testLogging { showStandardStreams = true }
```

*Attention:* Enabling all standard outputs will generate a huge log file!