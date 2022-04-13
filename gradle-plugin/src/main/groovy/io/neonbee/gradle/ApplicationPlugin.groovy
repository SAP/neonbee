package io.neonbee.gradle

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING
import static org.gradle.api.plugins.JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME
import static org.gradle.api.plugins.JavaPlugin.TEST_IMPLEMENTATION_CONFIGURATION_NAME

import java.nio.file.Files
import java.nio.file.Path

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.plugins.JavaApplication
import org.gradle.api.tasks.TaskProvider

import io.neonbee.gradle.internal.GradleHelper

class ApplicationPlugin implements Plugin<Project> {
    static String CREATE_WORKING_DIR_TASK_NAME = 'createWorkingDir'
    static String NEONBEE_MODULES_CONFIGURATION_NAME = 'neonbeeModules'

    @Override
    void apply(Project project) {
        ApplicationExtension config = ApplicationExtension.create(project)

        project.pluginManager.apply(BasePlugin)
        project.pluginManager.apply(org.gradle.api.plugins.ApplicationPlugin)

        Configuration neonbeeModulesConfiguration = project.configurations.maybeCreate(NEONBEE_MODULES_CONFIGURATION_NAME)
        neonbeeModulesConfiguration.setVisible(false)
        neonbeeModulesConfiguration.setDescription('NeonBee Modules to be added to the verticles folder of the working directory')
        neonbeeModulesConfiguration.setCanBeConsumed(true)
        neonbeeModulesConfiguration.setCanBeResolved(true)

        // Add NeonBee dependencies
        project.afterEvaluate {
            // implementation dependencies
            def neonBeeCore = [group: 'io.neonbee', name: 'neonbee-core', version: config.neonbeeVersion]
            project.dependencies.add(IMPLEMENTATION_CONFIGURATION_NAME, neonBeeCore)

            def neonBeeCoreTest = [group: 'io.neonbee', name: 'neonbee-core-test', version: config.neonbeeVersion]
            project.dependencies.add(TEST_IMPLEMENTATION_CONFIGURATION_NAME, neonBeeCoreTest)
        }

        project.configure(project.extensions.getByType(JavaApplication)) {
            mainClassName = 'io.neonbee.Launcher'
        }

        TaskProvider<Task> createWorkingDirTask = GradleHelper.registerOrConfigureTask(project, CREATE_WORKING_DIR_TASK_NAME, Task) {
            Path workingDirCachePath = project.file('.gradle/working_dir').toPath()
            if (Files.notExists(workingDirCachePath)) {
                GradleHelper.copyResourceDir('working_dir', project.file('.gradle').toPath())

            }

            doFirst {
                File currentWorkingDir = project.file(config.workingDir)
                if (!currentWorkingDir.exists()) {
                    Files.createDirectory(currentWorkingDir.toPath())

                    Files.walk(workingDirCachePath).skip(1).forEach {
                        Path target = currentWorkingDir.toPath().resolve(workingDirCachePath.relativize(it))
                        if (Files.isDirectory(it)) {
                            Files.createDirectory(target)
                        } else {
                            Files.copy(it, target)
                        }
                    }
                }
            }
        }

        // configure tasks
        project.tasks.named(org.gradle.api.plugins.ApplicationPlugin.TASK_RUN_NAME) {
            if (ModelsPlugin.isApplied(project)) {
                dependsOn ModelsPlugin.getCompileModelsTask(project)
            }

            doFirst {
                neonbeeModulesConfiguration.files.findAll  {
                    it.getName().endsWith('-module.jar') || it.getName().endsWith('-models.jar')
                }.forEach {
                    Path verticlesDir = project.file("${config.workingDir}/verticles").toPath()
                    Files.copy(it.toPath(), verticlesDir.resolve(it.getName()), REPLACE_EXISTING)
                }

                Path modelsDistSir = project.file("${project.rootDir}/models/dist").toPath()
                if (Files.exists(modelsDistSir)) {
                    Files.walk(modelsDistSir)
                            .filter({
                                it.toString().endsWith('.csn') || it.toString().endsWith('.edmx')
                            }).forEach {
                                Path modelsDir = project.file("${config.workingDir}/models").toPath()
                                Files.copy(it, modelsDir.resolve(it.fileName), REPLACE_EXISTING)
                            }
                }

                // evaluate in closure, as neonbeeCwd should be available
                args = [
                    '-cwd',
                    "${config.workingDir}"
                ]
            }
            dependsOn createWorkingDirTask
        }
    }
}
