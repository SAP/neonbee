## **How To:** Disable Tests on GitHub only

Use the following JUnit 5 annotation, to disable a test only for the voter on GitHub:

```java
@DisabledIfEnvironmentVariable(named = "GITHUB_ACTIONS", matches = "true", disabledReason = "provide any reason here")
```

Before you disable the test on the GitHub workflow, it maybe makes sense trying to `@Isolate` the test first. This sometimes fixes any issues with the concurrent execution:

```java
@Isolated("provide any reason here")
```