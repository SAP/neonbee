package io.neonbee.gradle.internal.plugins

import static org.gradle.api.plugins.JavaPlugin.TEST_TASK_NAME

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension

class JacocoPlugin implements Plugin<Project> {
    private static final String JACOCO_TEST_REPORTER_TASK_NAME = 'jacocoTestReport'

    @Override
    void apply(Project project) {
        project.pluginManager.apply(org.gradle.testing.jacoco.plugins.JacocoPlugin)

        project.tasks.named(TEST_TASK_NAME) {
            finalizedBy JACOCO_TEST_REPORTER_TASK_NAME
        }

        project.configure(project.extensions.getByType(JacocoPluginExtension)) {
            File reportsDirFile = project.file("${project.buildDir}/reports/jacoco")

            toolVersion = '0.8.7' // https://github.com/jacoco/jacoco/releases
            reportsDirectory = reportsDirFile

            // configure in closure, to still be able to access reportsDirectory
            project.tasks.named('jacocoTestReport') {
                dependsOn project.tasks.named(TEST_TASK_NAME)
                reports {
                    csv {
                        required.set true
                        destination project.file("${reportsDirFile}/csv/jacoco.csv")
                    }
                    xml {
                        required.set true
                        destination project.file("${reportsDirFile}/xml/jacoco.xml")
                    }
                    html {
                        required.set true
                        destination project.file("${reportsDirFile}/html")
                    }
                }

                finalizedBy project.tasks.reportCoverage
            }
        }
    }
}
