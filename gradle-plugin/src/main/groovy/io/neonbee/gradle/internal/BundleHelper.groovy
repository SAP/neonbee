package io.neonbee.gradle.internal

import java.lang.annotation.Annotation
import java.lang.annotation.ElementType
import java.util.regex.Pattern

import org.gradle.api.tasks.SourceSet
import org.objectweb.asm.ClassReader

class BundleHelper {
    /**
     * Get all class names of the output directory as list which contains a certain annotation (e.g. 'io.neonbeee.NeonBeeDeployable')
     */
    static List<String> getClassNamesFilteredByAnnotation(SourceSet sourceSet, String annotationName, ElementType... elementTypes) {
        final List<String> classNames = getClassNames(sourceSet)
        if (classNames.size() == 0)
            return []

        final ClassLoader classLoader = getClassLoader(sourceSet)
        final Class<Annotation> annotation = classLoader.loadClass(annotationName).asSubclass(Annotation)
        final AnnotationClassVisitor classVisitor = new AnnotationClassVisitor(annotation, elementTypes)

        classNames.each { name ->
            try {
                final String resourceName = name.replace('.', '/') + '.class'
                // Because all classes from output are also part of compile classpath
                new ClassReader(classLoader.getResourceAsStream(resourceName)).accept(classVisitor, 0)
            } catch (final IOException ignored) {
                /* nothing to do here */
            }
        }

        new ArrayList<>(classVisitor.getClassNames())
    }

    /**
     * Initialize project's classLoader variable
     */
    private static ClassLoader getClassLoader(SourceSet sourceSet) {
        final Object[] urls = (sourceSet.runtimeClasspath + sourceSet.compileClasspath)
                .collect { it.toURI().toURL() }.toArray()

        URLClassLoader.newInstance(urls)
    }

    /**
     * Get all class names of the output directory as List
     */
    private static List<String> getClassNames(SourceSet sourceSet) {
        final String fileSeparator = Pattern.quote(File.separator)
        final List<String> classNames = []

        sourceSet.output.classesDirs
                .filter { it.exists() }
                .forEach { dir ->
                    dir.traverse {
                        if (it.absolutePath.endsWith('.class')) {
                            final String classFilePath = (it.absolutePath - dir.absolutePath - File.separator - '.class')
                            classNames << classFilePath.replaceAll(fileSeparator, '.')
                        }
                    }
                }

        classNames
    }
}
