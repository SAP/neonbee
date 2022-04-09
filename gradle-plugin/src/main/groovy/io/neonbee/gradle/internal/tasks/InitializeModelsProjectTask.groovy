package io.neonbee.gradle.internal.tasks

import org.gradle.api.DefaultTask

import io.neonbee.gradle.internal.GradleHelper

class InitializeModelsProjectTask extends DefaultTask {
    static String modelsDirName = 'models'

    InitializeModelsProjectTask() {
        setGroup('build setup')
        setDescription('Adds a models sub project')

        doFirst {
            File modelsDir = getProject().file(modelsDirName)
            if (modelsDir.exists()) {
                String errMsg = 'Can\'t initialize models sub project, because directory "models" already exist!'
                throw new IllegalStateException(errMsg)
            }

            // Copy files to models dir
            String[] pluginFiles = [
                'build.gradle',
                'ExamplesService.cds',
                'package.json'
            ]
            pluginFiles.each {fileName ->
                GradleHelper.copyResourceToFile(modelsDirName+'/' + fileName, modelsDir.toPath().resolve(fileName))
            }

            // For some reason the resource .gitignore isn't added to the plugin jar.
            GradleHelper.copyResourceToFile(modelsDirName+'/gitignore' , modelsDir.toPath().resolve('.gitignore'))

            // add models as sub project
            String includeString = "include '${modelsDirName}'"
            File settingsFile = getProject().file('settings.gradle')
            if (!settingsFile.exists()) {
                settingsFile.createNewFile()
            }

            if(settingsFile.readLines().stream().noneMatch {line -> line.trim() == includeString} ) {
                settingsFile.append("\n"+includeString)
            }
        }
    }
}
