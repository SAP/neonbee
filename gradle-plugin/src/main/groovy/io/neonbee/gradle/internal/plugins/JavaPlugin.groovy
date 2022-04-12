package io.neonbee.gradle.internal.plugins

import static org.gradle.api.plugins.JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME
import static org.gradle.api.plugins.JavaPlugin.JAVADOC_TASK_NAME
import static org.gradle.api.plugins.JavaPlugin.TEST_IMPLEMENTATION_CONFIGURATION_NAME
import static org.gradle.api.plugins.JavaPlugin.TEST_TASK_NAME

import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.testing.logging.TestExceptionFormat

import io.neonbee.gradle.BasePlugin
import io.neonbee.gradle.ModuleExtension

class JavaPlugin implements Plugin<Project> {
    void apply(Project project) {
        project.pluginManager.apply(org.gradle.api.plugins.JavaPlugin)
        ModuleExtension config = ModuleExtension.get(project)

        // apply and configure other, third-party plugins
        configureJavaPlugin(project)

        // register / modify plugin specific tasks
        configureTestTask(project)
        configureBundleTasks(project)

        // Add NeonBee dependencies
        project.afterEvaluate {
            // implementation dependencies
            def neonBeeCore = [group: 'io.neonbee', name: 'neonbee-core', version: config.neonbeeVersion]
            project.dependencies.add(IMPLEMENTATION_CONFIGURATION_NAME, neonBeeCore)

            def neonBeeCoreTest = [group: 'io.neonbee', name: 'neonbee-core-test', version: config.neonbeeVersion]
            project.dependencies.add(TEST_IMPLEMENTATION_CONFIGURATION_NAME, neonBeeCoreTest)
        }
    }

    static void configureJavaPlugin(Project project) {
        // configure extensions
        project.configure(project.extensions.getByType(JavaPluginExtension)) {
            sourceCompatibility = JavaVersion.VERSION_11
        }

        project.tasks.named(JAVADOC_TASK_NAME) {
            options.addBooleanOption('html5', true)
        }
    }

    static void configureTestTask(project) {
        // the models configuration is created by the base plugin, we need the models built by other sub-projects as input here
        project.tasks.named(TEST_TASK_NAME) {
            // as models are required for the unit tests
            dependsOn BasePlugin.getModelsConfiguration(project), 'cleanTest'


            useJUnitPlatform()
            testLogging {
                events = [
                    'passed',
                    'skipped',
                    'failed',
                    'standardOut',
                    'standardError'
                ]
                exceptionFormat = TestExceptionFormat.FULL // full display of exceptions

                // don't show complete standard out and standard error of the tests on the console.
                // show only verbose output for failing tests.
                showStandardStreams = false
            }

            File reportsDirFile = project.file("${project.buildDir}/reports/junit")
            reports {
                junitXml {
                    required.set true
                    destination = project.file("${reportsDirFile}/xml")
                }
                html {
                    required.set true
                    destination = project.file("${reportsDirFile}/html")
                }
            }
        }
    }

    static void configureBundleTasks(Project project) {
        TaskProvider<Task> jar = BundleTasks.registerBundleJarTask(project)
        TaskProvider<Task> sourcesJar = BundleTasks.registerBundleSourcesJarTask(project)
        TaskProvider<Task> javadocJar = BundleTasks.registerBundleJavaDocJarTask(project)

        TaskProvider<Task> testJar = BundleTasks.registerBundleTestJarTask(project)
        TaskProvider<Task> testSourcesJar = BundleTasks.registerBundleTestSourcesJarTask(project)

        // finally add all new / modified bundle tasks to the build task
        project.tasks.named(JavaBasePlugin.BUILD_TASK_NAME) {
            dependsOn jar
            dependsOn sourcesJar
            dependsOn javadocJar

            dependsOn testJar
            dependsOn testSourcesJar
        }
    }
}
