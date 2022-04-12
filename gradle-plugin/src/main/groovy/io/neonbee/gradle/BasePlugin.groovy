package io.neonbee.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration

import io.neonbee.gradle.internal.GradleHelper
import io.neonbee.gradle.internal.tasks.InitializeModelsProjectTask

class BasePlugin implements Plugin<Project> {
    static String MODELS_CONFIGURATION_NAME = 'models'

    void apply(Project project) {
        // create models configuration for every project
        Configuration modelsConfiguration = project.configurations.maybeCreate(MODELS_CONFIGURATION_NAME)
        modelsConfiguration.description = 'Model configuration'
        modelsConfiguration.canBeConsumed = true
        modelsConfiguration.canBeResolved = true

        // we had to options here, either to only apply the org.gradle.language.base.plugins.LifecycleBasePlugin, or to apply the org.gradle.api.plugins.BasePlugin,
        // which comes with default / archive handling and implicitly also applies the LifecycleBasePlugin, as essentially all our plugin projects follow a basic clean
        // and assemble lifecycle, but also all generate artifacts (event the root plugin, that copies artifacts from the sub-projects) we decided to use the BasePlugin
        project.pluginManager.apply(org.gradle.api.plugins.BasePlugin)

        if(GradleHelper.isRootProject(project)) {
            GradleHelper.registerOrConfigureTask(project, 'initModels', InitializeModelsProjectTask)
        }
    }

    static Configuration getModelsConfiguration(Project project) {
        project.configurations.getByName(MODELS_CONFIGURATION_NAME)
    }
}
