package io.neonbee.gradle.internal.plugins

import static org.gradle.api.plugins.JavaPlugin.JAR_TASK_NAME

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.internal.artifacts.dsl.LazyPublishArtifact
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin

import com.github.jengelman.gradle.plugins.shadow.ShadowJavaPlugin

import io.neonbee.gradle.ModelsPlugin
import io.neonbee.gradle.ModulePlugin

class PublishPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        project.pluginManager.apply(MavenPublishPlugin)
        // At this point in time the correct archivesBaseName is not set. Therefore
        // do the configuration of the publishing task after the evaluation.
        project.afterEvaluate {
            if (ModelsPlugin.isApplied(project)) {
                project.configure(project.extensions.getByType(PublishingExtension)) {
                    publications {
                        // JAR that contains the code
                        main(MavenPublication) {
                            artifact source: new LazyPublishArtifact(project.tasks.named(ModulePlugin.MODELS_MODULE_JAR_TASK_NAME))
                        }
                    }
                }
            }

            project.configure(project.extensions.getByType(PublishingExtension)) {
                repositories {
                    mavenLocal()
                }

                publications {
                    // JAR that contains the code
                    main(MavenPublication) {
                        artifactId = project.archivesBaseName
                        artifact source: new LazyPublishArtifact(project.tasks.named(JAR_TASK_NAME))
                        artifact source: new LazyPublishArtifact(project.tasks.named(ShadowJavaPlugin.SHADOW_JAR_TASK_NAME))
                        artifact source: new LazyPublishArtifact(project.tasks.named(BundleTasks.SOURCES_JAR_TASK_NAME))
                        artifact source: new LazyPublishArtifact(project.tasks.named(BundleTasks.JAVADOC_JAR_TASK_NAME))
                        pom.withXml {
                            def dependenciesNode = asNode().appendNode('dependencies')
                            // Iterate over the compile dependencies and add them to the pom.xml
                            project.configurations.implementation.allDependencies.each {
                                def dependencyNode = dependenciesNode.appendNode('dependency')
                                dependencyNode.appendNode('groupId', it.group)
                                dependencyNode.appendNode('artifactId', it.name)
                                dependencyNode.appendNode('version', it.version)
                            }
                        }
                    }

                    // JAR that contains the test code
                    test(MavenPublication) {
                        artifactId = "${project.archivesBaseName}-test"
                        artifact source: new LazyPublishArtifact(project.tasks.named(BundleTasks.TEST_JAR_TASK_NAME))
                        artifact source: new LazyPublishArtifact(project.tasks.named(BundleTasks.TEST_SOURCES_JAR_TASK_NAME))
                        pom.withXml {
                            def dependenciesNode = asNode().appendNode('dependencies')
                            // Iterate over the testCompile dependencies and add them to the pom.xml
                            project.configurations.testImplementation.allDependencies.each {
                                def dependencyNode = dependenciesNode.appendNode('dependency')
                                dependencyNode.appendNode('groupId', it.group)
                                dependencyNode.appendNode('artifactId', it.name)
                                dependencyNode.appendNode('version', it.version)
                            }
                        }
                    }
                }
            }
        }
    }
}
