package io.neonbee.gradle

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.internal.logging.LoggingOutputInternal
import org.gradle.internal.logging.events.OutputEvent
import org.gradle.internal.logging.events.OutputEventListener

import io.neonbee.gradle.internal.plugins.CheckstylePlugin
import io.neonbee.gradle.internal.plugins.ConsoleReporterPlugin
import io.neonbee.gradle.internal.plugins.ErrorPronePlugin
import io.neonbee.gradle.internal.plugins.JacocoPlugin
import io.neonbee.gradle.internal.plugins.PmdPlugin
import io.neonbee.gradle.internal.plugins.SpotBugsPlugin
import io.neonbee.gradle.internal.plugins.SpotlessPlugin
import io.neonbee.gradle.internal.plugins.ViolationsPlugin

class QualityPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        QualityExtension.create(project)

        project.pluginManager.apply(PmdPlugin)
        project.pluginManager.apply(SpotBugsPlugin)
        project.pluginManager.apply(CheckstylePlugin)
        project.pluginManager.apply(SpotlessPlugin)
        project.pluginManager.apply(ViolationsPlugin)
        project.pluginManager.apply(JacocoPlugin)
        project.pluginManager.apply(ConsoleReporterPlugin)
        project.pluginManager.apply(ErrorPronePlugin)

        // Fail if JavaDoc has an error
        project.tasks.withType(Javadoc).configureEach {
            def exceptions = []
            doFirst {
                project.gradle.services.get(LoggingOutputInternal).addOutputEventListener(new OutputEventListener() {
                            void onOutput(OutputEvent event) {
                                if (event.toString() =~ " warning: ") {
                                    exceptions << "Javadoc warning: ${event.toString()}"
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
