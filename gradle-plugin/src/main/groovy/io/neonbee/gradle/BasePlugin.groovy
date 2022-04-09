package io.neonbee.gradle


import org.gradle.api.InvalidUserDataException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration

import io.neonbee.gradle.internal.GradleHelper
import io.neonbee.gradle.internal.tasks.InitializeModelsProjectTask

class BasePlugin implements Plugin<Project> {
    static final String MODELS_CONFIGURATION_NAME = 'models'

    private BaseExtension config

    void apply(Project project) {
        // config of the root project (where it is created)
        this.config = BaseExtension.create(project)

        project.afterEvaluate {
            if (!config.componentName || !config.componentGroup || !config.componentVersion) {
                throw new InvalidUserDataException("""It is mandatory to define a componentName, componentGroup and componentVersion in the ${BaseExtension.NAME} configuration extension object DSL block in the root project:

neonbeeModule {
    componentName = 'example'
    componentGroup = 'org.example'
    componentVersion = '0.0.1'
}""")
            }
        }

        // we had to options here, either to only apply the org.gradle.language.base.plugins.LifecycleBasePlugin, or to apply the org.gradle.api.plugins.BasePlugin,
        // which comes with default / archive handling and implicitly also applies the LifecycleBasePlugin, as essentially all our plugin projects follow a basic clean
        // and assemble lifecycle, but also all generate artifacts (event the root plugin, that copies artifacts from the sub-projects) we decided to use the BasePlugin
        project.pluginManager.apply(org.gradle.api.plugins.BasePlugin)

        // create models configuration for every project
        Configuration modelsConfiguration = project.configurations.maybeCreate(MODELS_CONFIGURATION_NAME)
        modelsConfiguration.visible = false
        modelsConfiguration.description = 'Model configuration'
        modelsConfiguration.canBeConsumed = true
        modelsConfiguration.canBeResolved = true

        if(GradleHelper.isRootProject(project)) {
            GradleHelper.registerOrConfigureTask(project, 'initModels', InitializeModelsProjectTask)
        }
    }
}
