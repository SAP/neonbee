package io.neonbee.gradle.internal.plugins

import static org.gradle.api.plugins.JavaPlugin.COMPILE_JAVA_TASK_NAME
import static org.gradle.api.plugins.JavaPlugin.COMPILE_TEST_JAVA_TASK_NAME

import java.nio.file.Path

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.quality.PmdExtension
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskProvider

import io.neonbee.gradle.internal.GradleHelper

class PmdPlugin implements Plugin<Project> {
    private static final String PMD_MAIN_TASK_NAME = 'pmdMain'
    private static final String PMD_TEST_TASK_NAME = 'pmdTest'
    private static final String PMD_CONFIG_DIR = 'gradle/pmd'

    @Override
    void apply(Project project) {
        project.pluginManager.apply(org.gradle.api.plugins.quality.PmdPlugin)

        if (!project.file(PMD_CONFIG_DIR).exists()) {
            Path targetDir = project.file('gradle').toPath()
            GradleHelper.copyResourceDir('pmd', targetDir)
        }

        def _sourceSets = project.properties['sourceSets'] as SourceSet
        SourceSet mainSourceSet = _sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME)
        SourceSet testSourceSet = _sourceSets.getByName(SourceSet.TEST_SOURCE_SET_NAME)

        // configure extension
        project.configure(project.extensions.getByType(PmdExtension)) {
            // ignore the issues during PMD run, because the violations plugin
            // will display the issues and let the build fail later.
            ignoreFailures = true
            sourceSets = [mainSourceSet, testSourceSet]
            ruleSets = []
            toolVersion = '6.38.0' // https://pmd.github.io/
            incrementalAnalysis = true // does require gradle 6
        }

        Path pmdConfigDir = project.file(PMD_CONFIG_DIR).toPath()
        String customRuleSet = pmdConfigDir.resolve('customRuleset.xml').toString()

        // configure pmdMain task
        TaskProvider<Task> pmdMain = project.tasks.named(PMD_MAIN_TASK_NAME) {
            ruleSetFiles = project.files(pmdConfigDir.resolve('rulesetMain.xml').toString(), customRuleSet)
        }

        project.tasks.named(COMPILE_JAVA_TASK_NAME) {
            finalizedBy pmdMain
        }

        // configure pmdTest task
        TaskProvider<Task> pmdTest = project.tasks.named(PMD_TEST_TASK_NAME) {
            ruleSetFiles = project.files(pmdConfigDir.resolve('rulesetTest.xml').toString(), customRuleSet)
        }

        project.tasks.named(COMPILE_TEST_JAVA_TASK_NAME) {
            finalizedBy pmdTest
        }
    }
}
