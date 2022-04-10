package io.neonbee.gradle.internal.plugins

import static org.gradle.api.plugins.JavaPlugin.CLASSES_TASK_NAME
import static org.gradle.api.plugins.JavaPlugin.JAR_TASK_NAME
import static org.gradle.api.plugins.JavaPlugin.JAVADOC_TASK_NAME
import static org.gradle.api.plugins.JavaPlugin.TEST_CLASSES_TASK_NAME

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Jar

import io.neonbee.gradle.BaseExtension
import io.neonbee.gradle.internal.GradleHelper

class BundleTasks {
    static final String SOURCES_JAR_TASK_NAME = 'sourcesJar'
    static final String JAVADOC_JAR_TASK_NAME = 'javadocJar'
    static final String TEST_JAR_TASK_NAME = 'testJar'
    static final String TEST_SOURCES_JAR_TASK_NAME = 'testSourcesJar'

    private static String[] EXCLUDED_FILES = [
        'META-INF/*.RSA',
        'META-INF/*.SF',
        'META-INF/*.DSA',
        'META-INF/NOTICE',
        'META-INF/LICENSE',
        '.gitkeep'
    ]

    private static SourceSet getSourceSet(Project project, String name) {
        def sourceSets = project.properties['sourceSets'] as SourceSet
        sourceSets.getByName(name)
    }

    private static TaskProvider<Task> registerJarTask(Project project, String name, Closure configureClosure) {
        TaskProvider<Jar> jarTask = GradleHelper.registerOrConfigureTask(project, name, Jar) {
            destinationDirectory = project.buildDir
            exclude EXCLUDED_FILES
            archiveExtension = 'jar'
            doFirst {
                archiveVersion = BaseExtension.get(project).getComponentVersion()
            }
        }

        project.configure(jarTask.get(), configureClosure)
        jarTask
    }

    static TaskProvider<Task> registerBundleJarTask(Project project) {
        registerJarTask(project, JAR_TASK_NAME, {
            // no classifier, in alignment with https://maven.apache.org/plugins/maven-jar-plugin/jar-mojo.html
        })
    }

    static TaskProvider<Task> registerBundleSourcesJarTask(Project project) {
        registerJarTask(project, SOURCES_JAR_TASK_NAME, {
            dependsOn GradleHelper.probeTask(project, CLASSES_TASK_NAME)
            from getSourceSet(project, SourceSet.MAIN_SOURCE_SET_NAME).allSource
            // in alignment with https://maven.apache.org/plugins/maven-source-plugin/jar-mojo.html
            archiveClassifier = 'sources'
        })
    }

    static TaskProvider<Task> registerBundleJavaDocJarTask(Project project) {
        registerJarTask(project, JAVADOC_JAR_TASK_NAME, {
            TaskProvider<Task> javadoc = GradleHelper.probeTask(project, JAVADOC_TASK_NAME)
            dependsOn javadoc
            // in alignment with https://maven.apache.org/plugins/maven-source-plugin/jar-mojo.html
            archiveClassifier = 'javadoc'
            doFirst {
                from javadoc.get().destinationDir
            }
        })
    }

    static TaskProvider<Task> registerBundleTestJarTask(Project project) {
        registerJarTask(project, TEST_JAR_TASK_NAME, {
            dependsOn GradleHelper.probeTask(project, TEST_CLASSES_TASK_NAME)
            from getSourceSet(project, SourceSet.MAIN_SOURCE_SET_NAME).output
            // in alignment with https://maven.apache.org/plugins/maven-source-plugin/jar-mojo.html
            archiveClassifier = 'tests'
        })
    }

    static TaskProvider<Task> registerBundleTestSourcesJarTask(Project project) {
        registerJarTask(project, TEST_SOURCES_JAR_TASK_NAME, {
            dependsOn GradleHelper.probeTask(project, TEST_CLASSES_TASK_NAME)
            from getSourceSet(project, SourceSet.TEST_SOURCE_SET_NAME).allSource
            // in alignment with https://maven.apache.org/plugins/maven-source-plugin/jar-mojo.html
            archiveClassifier = 'test-sources'
        })
    }
}
