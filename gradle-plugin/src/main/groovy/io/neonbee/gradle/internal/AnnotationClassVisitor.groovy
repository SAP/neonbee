package io.neonbee.gradle.internal

import static java.lang.annotation.ElementType.FIELD
import static java.lang.annotation.ElementType.METHOD
import static java.lang.annotation.ElementType.TYPE

import java.lang.annotation.Annotation
import java.lang.annotation.ElementType

import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * Scans a class if either the class itself, or any of it's methods or fields is annotated with a given annotation
 */
class AnnotationClassVisitor extends ClassVisitor {
    private final Set<String> classNames = new HashSet<>()

    private final String annotationClassDescriptor

    private final boolean includeTypes

    private MethodVisitor methodVisitor

    private FieldVisitor fieldVisitor

    private String className

    private int access

    AnnotationClassVisitor(final Class<? extends Annotation> annotation, final ElementType... elementTypes) {
        super(Opcodes.ASM7)

        annotationClassDescriptor = "L" + annotation.getName().replace('.', '/') + ";"

        // if required initialize the method / field visitors (if null the types won't be tracked)
        final Set<ElementType> elementTypeSet = new HashSet<>(Arrays.asList(elementTypes))
        includeTypes = elementTypeSet.contains(TYPE)
        if (elementTypeSet.contains(FIELD)) {
            fieldVisitor = new AnnotationFieldVisitor()
        }
        if (elementTypeSet.contains(METHOD)) {
            methodVisitor = new AnnotationMethodVisitor(classNames)
        }
    }

    @Override
    void visit(final int version, final int access, final String name, final String signature,
            final String superName, final String[] interfaces) {
        className = name.replace('/', '.')
        this.access = access
    }

    @Override
    FieldVisitor visitField(final int access, final String name, final String descriptor, final String signature,
            final Object value) {
        return fieldVisitor
    }

    @Override
    MethodVisitor visitMethod(final int access, final String name, final String descriptor,
            final String signature, final String[] exceptions) {
        return methodVisitor
    }

    @Override
    AnnotationVisitor visitAnnotation(final String desc, final boolean visible) {
        if (includeTypes && ((this.access & Opcodes.ACC_PUBLIC) != 0) && annotationClassDescriptor == desc) {
            classNames.add(className)
        }

        return null
    }

    /**
     * @return the class names of classes annotated with a given annotation
     */
    Set<String> getClassNames() {
        return Collections.unmodifiableSet(classNames)
    }

    class AnnotationMethodVisitor extends MethodVisitor {
        private Set<String> classNames

        AnnotationMethodVisitor(Set<String> classNames) {
            super(Opcodes.ASM7)
            this.classNames = classNames
        }

        @Override
        AnnotationVisitor visitAnnotation(final String desc, final boolean visible) {
            if (visible && annotationClassDescriptor == desc) {
                classNames.add(className)
            }
            return null
        }
    }

    class AnnotationFieldVisitor extends FieldVisitor {
        private Set<String> classNames

        AnnotationFieldVisitor(Set<String> classNames) {
            super(Opcodes.ASM7)
            this.classNames = classNames
        }

        @Override
        AnnotationVisitor visitAnnotation(final String desc, final boolean visible) {
            if (visible && annotationClassDescriptor == desc) {
                classNames.add(className)
            }
            return null
        }
    }
}
