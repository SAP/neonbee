# NeonBee Gradle Plugin

The NeonBee Gradle Plugin is a collection of several plugins that helps to develop NeonBee applications.

* Application Plugin
* Quality Plugin
* Module Plugin
* Models Plugin

## Module Plugin
The *NeonBee Module Plugin* helps to build *NeonBee Modules*. A *NeonBee Module* is a JAR containing
`Deployables`, `Hooks` and `Models`.

⚠️ The *NeonBee Module Plugin* don't support `Models` by default. In case your component requires `Models`,
apply the *NeonBee Models Plugin*.

### Configuration
The *NeonBee Module Plugin* can be configured with the `neonbeeModule` extension.

```
neonbeeModule {
    componentName = 'example'       // will be used for maven publish archivesBaseName
    componentGroup = 'org.example'  // will be used for maven publish group
    componentVersion = '0.0.1'      // will be used for maven publish version
    neonbeeVersion = '0.10.0'       // The NeonBee version to build the module against.
}
```

The plugin can be added with the following code to your *build.gradle* of the root project:
```
plugins {
    id 'io.neonbee.gradle.module' version '0.0.1-SNAPSHOT'
}
```

## Quality Plugin
The *NeonBee Quality Plugin* helps to apply the same quality checks as applied in NeonBee. The plugin will
add several static code checkers with the same configuration as in NeonBee. The configuration files will be
copied to the `{projectDir}/gradle` folder and can be modified if required.

| Code Checker | Config Path         |
|--------------|---------------------|
| CheckStyle   | `gradle/checkstyle` |
| PMD          | `gradle/pmd`        |
| SpotBugs     | `gradle/findbugs`   |
| Spotless     | `gradle/spotless`   |
| ErrorProne   | -                   |
| Jacoco       | -                   |

### Configuration
The *NeonBee Quality Plugin* can be configured with the `neonbeeQuality` extension.

```
neonbeeQuality {
    minCodeCoverage = 80.0 // The minimum required code coverage (Default 80.0)
    severityLevel = 'ERROR' // The severity level (INFO, WARN, ERROR) for the *Violations Plugin* (Default WARN)
}
```

The plugin can be added with the following code to your `build.gradle` of the root project:
```
plugins {
    id 'io.neonbee.gradle.quality' version '0.0.1-SNAPSHOT'
}
```
⚠️The *NeonBee Quality Plugin* can only be applied to gradle projects which applied the *NeonBee Module Plugin*
or at least  the *Gradle Java Plugin*.

## Models Plugin
The *NeonBee Models Plugin* helps to compile *cds* files to *edmx* and *csn* and is required
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

It will also add the required models dependency to your *build.gradle* file.

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