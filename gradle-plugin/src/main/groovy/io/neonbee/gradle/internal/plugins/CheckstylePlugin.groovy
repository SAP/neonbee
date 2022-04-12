package io.neonbee.gradle.internal.plugins

import static org.gradle.api.plugins.JavaPlugin.COMPILE_JAVA_TASK_NAME
import static org.gradle.api.plugins.JavaPlugin.COMPILE_TEST_JAVA_TASK_NAME

import java.nio.file.Path

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.quality.CheckstyleExtension
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskProvider

import io.neonbee.gradle.internal.GradleHelper

class CheckstylePlugin implements Plugin<Project> {
    private static final String CHECK_STYLE_MAIN_TASK_NAME = 'checkstyleMain'
    private static final String CHECK_STYLE_TEST_TASK_NAME = 'checkstyleTest'
    private static final String CHECK_STYLE_CONFIG_DIR = 'gradle/checkstyle'

    @Override
    void apply(Project project) {
        project.pluginManager.apply(org.gradle.api.plugins.quality.CheckstylePlugin)

        if (!project.file(CHECK_STYLE_CONFIG_DIR).exists()) {
            Path targetDir = project.file('gradle').toPath()
            GradleHelper.copyResourceDir('checkstyle', targetDir)
        }

        def _sourceSets = project.properties['sourceSets'] as SourceSet
        SourceSet mainSourceSet = _sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME)
        SourceSet testSourceSet = _sourceSets.getByName(SourceSet.TEST_SOURCE_SET_NAME)

        // configure extension
        project.configure(project.extensions.getByType(CheckstyleExtension)) {
            ignoreFailures = true
            toolVersion = '9.0'
            sourceSets = [mainSourceSet, testSourceSet]
            configDirectory = project.file(CHECK_STYLE_CONFIG_DIR)
        }

        // configure checkstyleMain task
        TaskProvider<Task> checkStyleMain = project.tasks.named(CHECK_STYLE_MAIN_TASK_NAME) {
            configFile = project.file("${CHECK_STYLE_CONFIG_DIR}/main.xml")
        }

        project.tasks.named(COMPILE_JAVA_TASK_NAME) {
            finalizedBy checkStyleMain
        }

        // configure checkstyleTest task
        TaskProvider<Task> checkStyleTest = project.tasks.named(CHECK_STYLE_TEST_TASK_NAME) {

            configFile = project.file("${CHECK_STYLE_CONFIG_DIR}/test.xml")
            CheckstyleExtension
        }

        project.tasks.named(COMPILE_TEST_JAVA_TASK_NAME) {
            finalizedBy checkStyleTest
        }
    }
}
