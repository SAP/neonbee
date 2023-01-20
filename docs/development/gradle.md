# Gradle Tasks

## Content

- [Gradle Tasks](#Gradle-Tasks)
    - [Content](#Content)
    - [Purpose](#Purpose)
    - [Gradle Build Tasks](#Gradle Build Tasks)
    - [Gradle Docker Tasks](#Gradle-Docker-Tasks)
    - [Gradle Documentation Tasks](#Gradle-Documentation-Tasks)
    - [Gradle Release Tasks](#Gradle-Release-Tasks)
    - [Gradle Verification Tasks](#Gradle-Verification-Tasks)

## Purpose

This document describes the custom gradle tasks and their particularities used for the automation of the repetitive tasks in NeonBee.

## Gradle Build Tasks
The Gradle **build** task is responsible for building the project, including running all tests, producing the production artifacts. In our project, the build task is overriden by a custom build task and it requires the following tasks to be executed:
  - **coreJar** - builds and packages NeonBee as a *neonbee-core.jar* and places it into the build folder. This jar does not contain any dependent jars
  - **distTar** - builds and packages a NeonBee dist .tar.gz archive including folder structure, start scripts etc. into the build folder
  - **javadocJar** - builds the jar for the Javadoc
  - **shadowJar** - builds a fat/uber jar. The shadowDistZip and shadowDistTar tasks are removed to ensure that our own (standard) dist jar build is used.
  - **sourcesJar** - builds the *neonbee-core-sources.jar*, containing only the source code
  - **testJar** - creates the *neonbee-core-test.jar* of all the test binaries
  - **testJavadocJar** - builds the jar for the test Javadoc
  - **testSourcesJar** - builds the test source jar, *neonbee-core-test-sources.jar*

## Gradle Docker Tasks
- docker - builds the docker image based on the defined configuration

## Gradle Documentation Tasks
- javadoc - generates HTML API documentation for Java classes

## Gradle Release Tasks
- changelog - generates the changelog containing all the commit messages
- release - this task requires a project parameter, *-PnextVersion=<nextVersion>*, it updates the NeonBee version in *build.gradle* to *nextVersion* and also updates the *Changelog.*. Furthemore, it creates a commit with the changed files for release

## Gradle Verification Tasks
- **jacocoTestReport** - provides code coverage report for the project. The task requires the tests to run before generating the report. When the test task executes the `unitTest` and `longRunningTest` tasks, the jacocoTestReport task will read both of the two `*.exec` files as input
- **longRunningTest** - executes the JUnit tests which require more than 5s to execute. These tests are filtered by applying the `@Tag('longRunningTest)` annotations. Tha task extends the test task by providing, in addition, a default global timeout of 30s for these 'long running' tests. The task can also be executed with a project parameter `-PlongRunningTestTimeout=<timeout>`, to override the default timeout of 30s to *timeout*
- **sonarqube** - runs SonarQube analysis. The task requires some global properties to be configured, like the sonar server and the absolute paths of the JaCoCo and JUnit reports
- **test** - executes all the JUnit tests. The task accepts a project parameter, *-PtestPlan=<default/withGlobalTimeout>*. If the task is called with `-PtestPlan=withGlobalTimeout`, the `unitTest` and `longRunningUnitTest` will be executed. In addition, it is also possible to provide a project parameter to modify the global timeout for each of these tasks by using `-PlongRunningTestTimeout=<timeout>` and/or  `-PunitTestTimeout=<timeout>`
- **unitTest** - executes the JUnit tests which don't requre more than 5s to execute. The task extends the test task by providing, a default global timeout of 5s for the JUnit tests, excluding the long running ones. It can also be executed with a project parameter `-PunitTestTimeout=<timeout>`, to override the default timeout of 5s to *timeout*
- **violations** - parses report files from static code analysis and fails the build depending on the violations found, like failed JUnit tests, PMD violations, "bug patterns" identified by SpotBugs
