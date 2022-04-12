package io.neonbee.gradle.internal

import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.UnknownTaskException
import org.gradle.api.tasks.TaskProvider

class GradleHelper {

    static boolean isRootProject(Project project) {
        project.rootProject == project
    }

    static Path rootProjectCacheDir(Project project) {
        project.rootProject.file(".gradle/").toPath()
    }

    static String getFileNameWithoutExtension(Path file) {
        String fileName = file.getName(file.getNameCount()-1).toString()
        fileName.substring(0, fileName.lastIndexOf('.'))
    }

    static File copyResourceToFile(String resourceName, Path target) {
        Files.createDirectories(target.getParent())
        GradleHelper.class.getClassLoader().getResourceAsStream(resourceName).withStream {
            Files.copy(it, target, StandardCopyOption.REPLACE_EXISTING)
        }
        target.toFile()
    }

    static void copyResourceDir(String resourceDir, Path targetDir) {
        URI resource = GradleHelper.class.getResource("").toURI()
        def fileSystem = FileSystems.newFileSystem( resource, Collections.<String, String>emptyMap())
        Path jarPath = fileSystem.getPath(resourceDir)

        Files.walk(jarPath).forEach {
            if (Files.isDirectory(it)) {
                Files.createDirectory(targetDir.resolve(it.toString()))
            } else {
                copyResourceToFile(it.toString(), targetDir.resolve(it.toString()))
            }
        }
        fileSystem.close()
    }

    static TaskProvider<? extends Task> probeTask(Project project, String name) {
        try {
            project.tasks.named(name)
        } catch (UnknownTaskException ignore) {
            null // nothing to do here (Groovy Truth can handle this!)
        }
    }

    /**
     * Registers and configures a task if non-existent or (re-)configures the existing task
     */
    static <T extends Task> TaskProvider<T> registerOrConfigureTask(Project project, String name, Class<T> type) {
        registerOrConfigureTask(project, name, type, {})
    }
    static <T extends Task> TaskProvider<T> registerOrConfigureTask(Project project, String name, Class<T> type, Closure configureClosure) {
        _registerOrConfigureTask(project, name, type, { project.configure(it, configureClosure) } as Action)
    }
    private static <T extends Task> TaskProvider<T> _registerOrConfigureTask(Project project, String name, Class<T> type, Action<? super T> configureAction) {
        def taskProvider = probeTask(project, name) ?: project.tasks.register(name, type)
        taskProvider.configure configureAction
        taskProvider as TaskProvider<T>
    }
}
