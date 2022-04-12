package io.neonbee.gradle

import org.gradle.api.Project

class QualityExtension {
    private static final String NAME = 'neonbeeQuality'

    /**
     * If the code coverage is below that number the test task will fail. Default: 80.0
     */
    float minCodeCoverage = 80.0

    /**
     * Severity levels for the violations Plugin: INFO, WARN or ERROR. Default: WARN
     */
    String severityLevel = 'WARN'

    QualityExtension(Project project) {
    }

    static QualityExtension get(Project project) {
        project.extensions.findByType(QualityExtension) ?: project.rootProject.extensions.getByType(QualityExtension)
    }

    static QualityExtension create(Project project) {
        project.rootProject.extensions.findByType(QualityExtension) ?: project.rootProject.extensions.create(NAME, QualityExtension, project.rootProject)
    }
}
