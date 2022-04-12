package io.neonbee.gradle.internal.plugins

import org.gradle.api.Plugin
import org.gradle.api.Project

import com.github.ksoichiro.console.reporter.ConsoleReporterExtension
import io.neonbee.gradle.QualityExtension

class ConsoleReporterPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.pluginManager.apply(com.github.ksoichiro.console.reporter.ConsoleReporterPlugin)

        QualityExtension qualityConfig = QualityExtension.get(project)

        project.afterEvaluate {
            project.configure(project.extensions.getByType(ConsoleReporterExtension)) {
                jacoco {
                    // Set this property to false if you don't need JaCoCo report.
                    // Even if this is true, reporting will not work without applying jacoco plugin.
                    enabled true

                    // Set this property to false if you want to see console report always.
                    onlyWhenCoverageTaskExecuted false

                    // Set this property to false if you want to see console report
                    // just after each project's jacocoTestReport task.
                    // If set to true, all reports will be shown at the end of builds.
                    reportAfterBuildFinished true

                    // Set this property to true if you want to treat a lack of the minimum coverage as an build error.
                    // This property sees thresholdError property, and if the coverage has fallen below this value
                    // the plugin will throw an exception to cause a build error.
                    // If you set this to true, you should also set thresholdError property.
                    failIfLessThanThresholdError true

                    // Set this property to false if you don't like this plugin automatically changing some
                    // property of jacoco plugin.
                    // If this is set to true, the plugin will set some properties of jacoco plugin
                    // to calculate coverage.
                    autoconfigureCoverageConfig true

                    // Set this property to your custom JacocoReport type task name, if you need.
                    coverageTaskName 'jacocoTestReport'

                    // Set this property to your JaCoCo report XML file.
                    // Default is null, which means
                    // ${project.buildDir}/reports/jacoco/test/jacocoTestReport.xml
                    // will be parsed.
                    reportFile project.file("${project.buildDir}/reports/jacoco/xml/jacoco.xml")

                    // Set this property to a certain C0 coverage percentage.
                    // When the coverage is greater than or equals to this value,
                    // the coverage will be shown with green color.
                    thresholdFine 90

                    // Set this property to a certain C0 coverage percentage.
                    // When the coverage is greater than or equals to this value,
                    // the coverage will be shown with yellow color.
                    // (When the coverage is less than this value, result will be red.)
                    thresholdWarning 85

                    // Set this property to a certain C0 coverage percentage.
                    // When the coverage is less than this value and
                    // failIfLessThanThresholdError property is set to true,
                    // the build will fail. Even if the property is called
                    // failIfLessThan... the build will actually fail at 80.0%
                    thresholdError qualityConfig.minCodeCoverage

                    // Set this property if you want to customize build error message
                    // when you use 'failIfLessThanThresholdError' feature.
                    brokenCoverageErrorMessage "Code Coverage has fallen below the defined threshold. (${qualityConfig.minCodeCoverage})"

                    // Set this property to false if you don't need colorized output.
                    colorEnabled true
                }
            }
        }
    }
}
