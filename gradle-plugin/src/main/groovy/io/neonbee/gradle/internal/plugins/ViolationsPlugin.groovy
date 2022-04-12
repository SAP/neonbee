package io.neonbee.gradle.internal.plugins

import static org.gradle.api.plugins.JavaPlugin.TEST_TASK_NAME

import org.gradle.api.Plugin
import org.gradle.api.Project

import se.bjurr.violations.gradle.plugin.ViolationsGradlePlugin
import se.bjurr.violations.gradle.plugin.ViolationsTask
import io.neonbee.gradle.QualityExtension

class ViolationsPlugin implements Plugin<Project> {
    private static final String VIOLATIONS_TASK_NAME = 'violations'

    @Override
    void apply(Project project) {
        project.pluginManager.apply(ViolationsGradlePlugin)

        project.tasks.named(TEST_TASK_NAME) {
            finalizedBy VIOLATIONS_TASK_NAME
        }

        QualityExtension qualityConfig = QualityExtension.get(project)

        project.afterEvaluate {
            project.tasks.register(VIOLATIONS_TASK_NAME, ViolationsTask) {
                maxReporterColumnWidth = 20 // 0 means 'no limit'
                maxRuleColumnWidth = 100
                maxSeverityColumnWidth = 10
                maxLineColumnWidth = 10
                maxMessageColumnWidth = 120
                minSeverity = qualityConfig.severityLevel // INFO, WARN or ERROR
                detailLevel = 'VERBOSE' // PER_FILE_COMPACT, COMPACT or VERBOSE
                maxViolations = 0 // Fail the build if total number of found violations is higher
                printViolations = true

                // Formats are listed here: https://github.com/tomasbjerre/violations-lib
                violations = [
                    [
                        'FINDBUGS',
                        '.',
                        '.*/build/reports/spotbugs/main.xml\$',
                        'Spotbugs'
                    ],
                    [
                        'FINDBUGS',
                        '.',
                        '.*/build/reports/spotbugs/test.xml\$',
                        'Spotbugs'
                    ],
                    [
                        'JUNIT',
                        '.',
                        '.*/build/reports/junit/xml/.*\\.xml\$',
                        'JUnit'
                    ],
                    [
                        'PMD',
                        '.',
                        '.*/build/reports/pmd/.*\\.xml\$',
                        'PMD'
                    ],
                    [
                        'CHECKSTYLE',
                        '.',
                        '.*/build/reports/checkstyle/.*\\.xml\$',
                        'Checkstyle'
                    ]
                ]
            }
        }
    }
}
