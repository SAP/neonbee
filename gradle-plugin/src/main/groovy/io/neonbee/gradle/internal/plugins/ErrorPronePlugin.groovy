package io.neonbee.gradle.internal.plugins

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.internal.logging.LoggingOutputInternal
import org.gradle.internal.logging.events.OutputEvent
import org.gradle.internal.logging.events.OutputEventListener

class ErrorPronePlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.pluginManager.apply(net.ltgt.gradle.errorprone.ErrorPronePlugin)

        def errorProne = [group: 'com.google.errorprone', name: 'error_prone_core', version: '2.10.0']
        project.dependencies.add(net.ltgt.gradle.errorprone.ErrorPronePlugin.CONFIGURATION_NAME, errorProne)

        project.tasks.withType(JavaCompile).configureEach {
            options.errorprone.allErrorsAsWarnings = true
            options.errorprone.disableWarningsInGeneratedCode = true
            options.failOnError = true
            options.compilerArgs << '-Xlint:deprecation'
            options.errorprone.errorproneArgs.add('-Xep:TypeParameterUnusedInFormals:OFF')
            options.errorprone.errorproneArgs.add('-Xep:InlineMeSuggester:OFF')
            options.errorprone.errorproneArgs.add("-XepExcludedPaths:.*/src/generated/.*")

            def exceptions = []
            doFirst {
                project.gradle.services.get(LoggingOutputInternal).addOutputEventListener(new OutputEventListener() {
                            void onOutput(OutputEvent event) {
                                if (event.toString() =~ ': warning:') {
                                    exceptions << "Error-Prone warning: ${event.toString()}"
                                }
                            }
                        })
            }
            doLast {
                if (exceptions.size() > 0) {
                    throw new GradleException(String.join('\n', exceptions))
                }
            }
        }
    }
}
