package io.neonbee.gradle

import org.gradle.api.Project

/**
 * This neonbeeModule { ... } extension is to be applied to the base project once and specifies settings for all sub-plugins
 */
class BaseExtension {
    static final String NAME = 'neonbeeModule'

    // the component name will be used throughout the project (e.g. for defining the archivesBaseName of modules)
    String componentName
    // the component group will be used throughout the project (e.g. for defining the group used for maven publications)
    String componentGroup
    // the component group will be used throughout the project (e.g. for defining the version used for maven publications)
    String componentVersion

    // The NeonBee version to build the module against.
    String neonbeeVersion

    BaseExtension(Project project) {

    }

    static BaseExtension get(Project project) {
        project.extensions.findByType(BaseExtension) ?: project.rootProject.extensions.getByType(BaseExtension)
    }

    static BaseExtension create(Project project) {
        project.rootProject.extensions.findByType(BaseExtension) ?: project.rootProject.extensions.create(NAME, BaseExtension, project.rootProject)
    }
}
