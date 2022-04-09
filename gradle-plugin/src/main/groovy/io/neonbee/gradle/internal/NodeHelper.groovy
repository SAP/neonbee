package io.neonbee.gradle.internal

import java.nio.file.Path

import org.gradle.api.Project

import com.github.gradle.node.NodeExtension

class NodeHelper {

    static void configureNodeExtension(Project project) {
        project.configure(project.extensions.getByType( NodeExtension)) {
            Path cacheDir = GradleHelper.rootProjectCacheDir(project)
            // If true, it will download node using above parameters.
            // If false, it will try to use globally installed node.
            download = true

            // The directory where Node.js is unpacked (when download is true)
            workDir = cacheDir.resolve('nodejs').toFile()
            // Version of node to use.
            version = '12.22.1'

            // The directory where npm is installed (when a specific version is defined)
            npmWorkDir = cacheDir.resolve( 'npm').toFile()
            // Version of npm to use.
            npmVersion = '6.14.12'

            // The Node.js project directory location
            // This is where the package.json file and node_modules directory are located
            // By default it is at the root of the current project
            nodeProjectDir = project.projectDir
        }
    }
}
