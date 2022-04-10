package io.neonbee.gradle

import static io.neonbee.gradle.ModelsPlugin.MODELS_COMPILE_TASK_NAME
import static java.lang.annotation.ElementType.METHOD
import static java.lang.annotation.ElementType.TYPE

import org.gradle.api.InvalidUserDataException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskProvider

import com.github.jengelman.gradle.plugins.shadow.ShadowJavaPlugin
import com.github.jengelman.gradle.plugins.shadow.ShadowPlugin
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

import io.neonbee.gradle.internal.BundleHelper
import io.neonbee.gradle.internal.GradleHelper
import io.neonbee.gradle.internal.plugins.JavaPlugin

class ModulePlugin implements Plugin<Project> {
    static String MODELS_MODULE_JAR_TASK_NAME = 'modelsModuleJar'
    static String CUSTOM_DEPENDENCIES_CONFIGURATION_NAME = 'custom'

    void apply(Project project) {
        project.pluginManager.apply(BasePlugin)
        project.pluginManager.apply(JavaPlugin)
        project.pluginManager.apply(ShadowPlugin)

        Configuration customDependenciesConfiguration = project.configurations.maybeCreate(CUSTOM_DEPENDENCIES_CONFIGURATION_NAME)
        customDependenciesConfiguration.setVisible(false)
        customDependenciesConfiguration.setDescription('Custom verticle dependencies configuration')
        customDependenciesConfiguration.setCanBeConsumed(true)
        customDependenciesConfiguration.setCanBeResolved(true)

        Configuration compileConfiguration = project.configurations.maybeCreate(org.gradle.api.plugins.JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME)
        compileConfiguration.extendsFrom(customDependenciesConfiguration)

        verifyNeonBeeExtension(project, BaseExtension.get(project))
        registerShadowJarTasks(project)
    }

    static void verifyNeonBeeExtension(Project project, BaseExtension config) {
        project.afterEvaluate {
            if (!config.componentName || !config.componentGroup || !config.componentVersion) {
                throw new InvalidUserDataException("""It is mandatory to define a componentName, componentGroup and componentVersion in the ${BaseExtension.NAME} configuration extension object DSL block in the root project:

neonbeeModule {
    componentName = 'example'
    componentGroup = 'org.example'
    componentVersion = '0.0.1'
    neonbeeVersion = '0.10.0'
}""")
            }
        }

        // after project evaluation, the configuration was applied, so use the componentName as archivesBaseName for this project
        project.afterEvaluate {
            project.group = config.getComponentGroup()
            project.archivesBaseName = config.getComponentName()
            project.version = config.getComponentVersion()
        }
    }

    static Set<String> collectModelIdentifiersByEnding(Project project, Configuration modelsConfiguration, String ending) {
        modelsConfiguration.resolvedConfiguration.files.collect { it.isDirectory() ? project.fileTree(it).files : it }
        .flatten().findAll { it.name.endsWith(ending) }.collect { "models/${it.name}" }
    }

    private static void registerShadowJarTasks(Project project) {
        if (ModelsPlugin.isApplied(project)) {
            TaskProvider<ShadowJar> modelsModuleJar = GradleHelper.registerOrConfigureTask(project, MODELS_MODULE_JAR_TASK_NAME, ShadowJar) {
                // Ensure that no class files are in this module
                configurations = []
                archiveClassifier = 'models'
            }
            configureShadowTask(project, modelsModuleJar)
            // finally add all new / modified bundle tasks to the build task
            project.tasks.named(JavaBasePlugin.BUILD_TASK_NAME) {
                dependsOn modelsModuleJar
            }
        }

        def sourceSets = project.properties['sourceSets'] as SourceSet
        SourceSet mainSourceSet = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME)
        Configuration customDependenciesConfiguration = project.configurations.getByName(CUSTOM_DEPENDENCIES_CONFIGURATION_NAME)

        TaskProvider<Task> moduleJar = project.tasks.named(ShadowJavaPlugin.SHADOW_JAR_TASK_NAME) {
            // Ensure that no class files are in this module
            dependsOn project.tasks.named(org.gradle.api.plugins.JavaPlugin.CLASSES_TASK_NAME), customDependenciesConfiguration
            configurations = [
                customDependenciesConfiguration
            ]
            archiveClassifier = 'module'

            doFirst {
                // list of full qualified name of each verticle (search during execution time, as the NeonBee dependencies get added afterEvaluate)
                List neonbeeDeployables = BundleHelper.getClassNamesFilteredByAnnotation(mainSourceSet, 'io.neonbee.NeonBeeDeployable', TYPE)
                List neonbeeHooks = BundleHelper.getClassNamesFilteredByAnnotation(mainSourceSet, 'io.neonbee.hook.Hooks', METHOD)
                neonbeeHooks.addAll(BundleHelper.getClassNamesFilteredByAnnotation(mainSourceSet, 'io.neonbee.hook.Hook', METHOD))

                manifest {
                    attributes(
                            'NeonBee-Deployables': neonbeeDeployables.join(';'), // class names separated the by semicolon
                            'NeonBee-Hooks': neonbeeHooks.join(';'), // class names separated the by semicolon
                            )
                }
            }
        }
        configureShadowTask(project, moduleJar)
        // finally add all new / modified bundle tasks to the build task
        project.tasks.named(JavaBasePlugin.BUILD_TASK_NAME) {
            dependsOn moduleJar
        }
    }

    private static void configureShadowTask(Project project, TaskProvider<Task> taskProvider) {
        Configuration modelsConfiguration = BasePlugin.getModelsConfiguration(project)
        project.configure(taskProvider.get(), {
            dependsOn modelsConfiguration
            destinationDirectory = project.buildDir

            doFirst {
                Set neonbeeModels = collectModelIdentifiersByEnding(project, modelsConfiguration, '.csn')
                Set neonbeeExtensionModels = collectModelIdentifiersByEnding(project, modelsConfiguration, '.edmx')

                manifest {
                    attributes(
                            'NeonBee-Module': "${project.archivesBaseName}:${project.version}",
                            'NeonBee-Models': neonbeeModels.join(';'), // model paths separated the by semicolon
                            'NeonBee-Model-Extensions': neonbeeExtensionModels.join(';') // model paths separated the by semicolon
                            )
                }
            }

            into('models') {
                from {
                    // include all models (use of closure defers evaluation until execution time)
                    modelsConfiguration
                }
            }

            exclude 'META-INF/*.RSA', 'META-INF/*.SF', 'META-INF/*.DSA', 'META-INF/NOTICE', 'META-INF/LICENSE', 'META-INF/DEPENDENCIES', '.gitkeep'

            archiveExtension = 'jar'
            doFirst {
                archiveVersion = project.version
            }

            mergeServiceFiles() // as defined by the ShadowPlugin
        })
    }
}
