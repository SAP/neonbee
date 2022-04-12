package io.neonbee.gradle.internal.plugins

import static org.gradle.api.plugins.JavaPlugin.TEST_TASK_NAME

import java.nio.file.Path

import org.gradle.api.Plugin
import org.gradle.api.Project

import com.diffplug.gradle.spotless.SpotlessExtension
import io.neonbee.gradle.internal.GradleHelper

class SpotlessPlugin implements Plugin<Project> {
    private static final String SPOTLESS_CHECK_TASK_NAME = 'spotlessCheck'
    private static final String SPOTLESS_CONFIG_DIR = 'gradle/spotless'

    @Override
    void apply(Project project) {
        project.pluginManager.apply(com.diffplug.gradle.spotless.SpotlessPlugin)

        project.tasks.named(TEST_TASK_NAME) {
            finalizedBy SPOTLESS_CHECK_TASK_NAME
        }

        if (!project.file(SPOTLESS_CONFIG_DIR).exists()) {
            Path targetDir = project.file('gradle').toPath()
            GradleHelper.copyResourceDir('spotless', targetDir)
        }

        project.configure(project.extensions.getByType(SpotlessExtension)) {
            java {
                // note: these settings must be duplicated into the build.gradle file!
                encoding 'UTF8'
                trimTrailingWhitespace()
                removeUnusedImports()
                endWithNewline()
                lineEndings 'UNIX'
                importOrderFile(project.file("${SPOTLESS_CONFIG_DIR}/neonbee.importorder"))
                eclipse('4.17.0').configFile project.file("${SPOTLESS_CONFIG_DIR}/eclipse-formatter.xml")
                custom 'Lambda fix', { it.replace('} )', '})').replace('} ,', '},') }
            }
        }
    }
}
