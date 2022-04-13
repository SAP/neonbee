package io.neonbee.gradle

import java.nio.file.Files
import java.nio.file.Path

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.FileTree
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.TaskProvider

import com.github.gradle.node.NodePlugin
import com.github.gradle.node.npm.task.NpmInstallTask
import com.github.gradle.node.task.NodeTask

import io.neonbee.gradle.internal.GradleHelper
import io.neonbee.gradle.internal.NodeHelper

class ModelsPlugin implements Plugin<Project> {
    static String MODELS_COMPILE_TASK_NAME = 'compileModels'

    private static String CLEAN_TASK_NAME = 'clean'
    private static String COMPILE_EDMX_TASK_NAME = 'compileEdmx'
    private static String DIST_DIR = 'dist'

    /**
     *
     * @param name the name of the task to register
     * @param src the path to the cds file to compile
     * @param to the output format e.g. 'json' for csn or 'edmx-v4' for edmx
     * @param destDir the output dir for the compiled files
     * @param renameFunction function to rename endings of compiled files
     * @param configureClosure closure to add additional configuration e.g. dependsOn ..
     */
    private static void registerCompileTask(Project project, String name, String src, String to, String destDir, Closure configureClosure = {}) {
        ArrayList<String> cdsTaskArgs = [
            'compile',
            src,
            '--service',
            'all',
            '--to',
            to,
            '--dest',
            destDir
        ]

        TaskProvider<NodeTask> nodeTask = GradleHelper.registerOrConfigureTask(project, name, NodeTask) {
            dependsOn (NpmInstallTask.NAME)
            script = project.file('node_modules/@sap/cds-dk/bin/cds.js')
            args = cdsTaskArgs
        }

        project.configure(nodeTask.get(), configureClosure)
    }

    private static void renameFileEndings(FileTree files, Closure matchingFiles, String newEnding) {
        files.matching(matchingFiles).files.each {
            Files.move(it.toPath(), it.toPath().parent.
                    resolve(GradleHelper.getFileNameWithoutExtension(it.toPath()) + newEnding))
        }
    }

    void apply(Project project) {
        project.pluginManager.apply(BasePlugin)
        project.pluginManager.apply(NodePlugin)
        NodeHelper.configureNodeExtension(project)

        Path compiledModelsDir = project.projectDir.toPath().resolve('dist')

        def cdsFiles = project.fileTree(dir: project.projectDir, include: '*.cds').collect {
            GradleHelper.getFileNameWithoutExtension(it.toPath())
        }
        def csnCompileTaskNames = cdsFiles.collect {fileNameNoExtension ->
            String csnCompileTaskName = "compile${fileNameNoExtension}Csn"
            registerCompileTask(project, csnCompileTaskName, "${fileNameNoExtension}.cds", 'json', DIST_DIR, {
                doLast {
                    renameFileEndings(project.fileTree(compiledModelsDir), { include '**/*.json'}, '.csn')
                }
            })
            csnCompileTaskName.toString()
        }

        registerCompileTask(project, COMPILE_EDMX_TASK_NAME, './', 'edmx-v4', DIST_DIR, {
            doLast {
                renameFileEndings(project.fileTree(compiledModelsDir), {
                    include '**/*.xml'
                    include '**/*.edmx-v4'
                }, '.edmx')
            }
        })

        TaskProvider<Task> compileModels = project.tasks.register(MODELS_COMPILE_TASK_NAME, Task) {
            List<String> dependencies = new ArrayList<>()
            dependencies.addAll([
                CLEAN_TASK_NAME,
                COMPILE_EDMX_TASK_NAME
            ])
            dependencies.addAll(csnCompileTaskNames)
            dependsOn(dependencies)
        }

        project.afterEvaluate {
            project.artifacts.add(BasePlugin.MODELS_CONFIGURATION_NAME, compiledModelsDir.toFile()) {
                builtBy compileModels
            }
        }

        GradleHelper.registerOrConfigureTask(project, CLEAN_TASK_NAME, Delete) {
            it.delete(project.fileTree(DIST_DIR))
        }
    }

    static boolean isApplied(Project project) {
        getModelsProject(project) != null
    }

    static Task getCompileModelsTask(Project project) {
        getModelsProject(project).tasks.findByName(MODELS_COMPILE_TASK_NAME)
    }

    private static Project getModelsProject(Project project) {
        project.getAllprojects().find {it.projectDir.getName() == 'models'}
    }
}
