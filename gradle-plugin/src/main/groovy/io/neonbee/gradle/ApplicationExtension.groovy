package io.neonbee.gradle

import org.gradle.api.Project

class ApplicationExtension {
    private static final String NAME = 'neonbeeApplication'

    String workingDir = 'working_dir'

    String neonbeeVersion = '0.10.0'

    ApplicationExtension(Project project) {
    }

    static ApplicationExtension get(Project project) {
        project.extensions.findByType(ApplicationExtension) ?: project.rootProject.extensions.getByType(ApplicationExtension)
    }

    static ApplicationExtension create(Project project) {
        project.rootProject.extensions.findByType(ApplicationExtension) ?: project.rootProject.extensions.create(NAME, ApplicationExtension, project.rootProject)
    }
}
