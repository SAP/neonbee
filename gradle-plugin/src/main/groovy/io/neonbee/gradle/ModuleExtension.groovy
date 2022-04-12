package io.neonbee.gradle

import org.gradle.api.Project

/**
 * This neonbeeModule { ... } extension is to be applied to the base project once and specifies settings for all sub-plugins
 */
class ModuleExtension {
    static final String NAME = 'neonbeeModule'

    // the component name will be used throughout the project (e.g. for defining the archivesBaseName of modules)
    String componentName
    // the component group will be used throughout the project (e.g. for defining the group used for maven publications)
    String componentGroup
    // the component group will be used throughout the project (e.g. for defining the version used for maven publications)
    String componentVersion

    // The NeonBee version to build the module against.
    String neonbeeVersion

    ModuleExtension(Project project) {

    }

    static ModuleExtension get(Project project) {
        project.extensions.findByType(ModuleExtension) ?: project.rootProject.extensions.getByType(ModuleExtension)
    }

    static ModuleExtension create(Project project) {
        project.rootProject.extensions.findByType(ModuleExtension) ?: project.rootProject.extensions.create(NAME, ModuleExtension, project.rootProject)
    }
}
