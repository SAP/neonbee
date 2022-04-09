# NeonBee Gradle Plugin

The NeonBee Gradle Plugin helps to build NeonBee Modules.

The plugin can be added with the following code to your *build.gradle* of the root project:
```
plugins {
    id 'io.neonbee.gradle.base' version '0.0.1-SNAPSHOT'
}
```

## Models Plugin
The *models* plugin helps to compile *cds* files to *edmx* and *csn* and is required
if the module contains *EntityVerticles*.

In case you need *models* in your NeonBee Module run the following command:
```
./gradlew initModels
```

This will add a *models* sub-project to your NeonBee module with the following content:
 * build.gradle
 * package.json
 * Example.cds
 * .gitignore

## Plugin Development

### Formatter
There is a spotless groovy formatter with some default settings configured.
The configured rules maybe not the best rules, feel free to adjust them.

### Tests
Until now no tests for the plugin exists, except manual tests.

To run and test the plugin, you need to ...
1. ... publish your modified plugin version to maven local via `./gradlew publishToMavenLocal`.
2. ... create a new empty Gradle project.
3. ... add your plugin version to the new gradle project.

Don't forget to add `mavenLocal()` to the `repositories` of the `pluginManagement`.

The `settings.gradle` should look like this:
```
pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
    }
}
```