package io.neonbee.internal.scanner;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Scans a class if either the class itself, or any of it's methods or fields is annotated with a given annotation.
 */
class AnnotationClassVisitor extends ClassVisitor {
    private final Set<String> classNames = new HashSet<>();

    private final String annotationClassDescriptor;

    private final boolean includeTypes;

    private MethodVisitor methodVisitor;

    private FieldVisitor fieldVisitor;

    private String className;

    private int access;

    AnnotationClassVisitor(Class<? extends Annotation> annotation, ElementType... elementTypes) {
        super(Opcodes.ASM7);

        annotationClassDescriptor = "L" + annotation.getName().replace('.', '/') + ";";

        // if required initialize the method / field visitors (if null the types won't be tracked)
        Set<ElementType> elementTypeSet = new HashSet<>(Arrays.asList(elementTypes));
        includeTypes = elementTypeSet.contains(TYPE);
        if (elementTypeSet.contains(FIELD)) {
            fieldVisitor = new AnnotationFieldVisitor();
        }
        if (elementTypeSet.contains(METHOD)) {
            methodVisitor = new AnnotationMethodVisitor();
        }
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        className = name.replace('/', '.');
        this.access = access;
    }

    @Override
    public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
        return fieldVisitor;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
            String[] exceptions) {
        return methodVisitor;
    }

    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        if (includeTypes && ((this.access & Opcodes.ACC_PUBLIC) != 0) && annotationClassDescriptor.equals(desc)) {
            classNames.add(className);
        }

        return null;
    }

    /**
     * Returns the names of classes annotated with the annotation specified when created the visitor instance.
     *
     * @return the class names
     */
    public Set<String> getClassNames() {
        return Collections.unmodifiableSet(classNames);
    }

    class AnnotationMethodVisitor extends MethodVisitor {
        AnnotationMethodVisitor() {
            super(Opcodes.ASM7);
        }

        @Override
        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
            if (visible && annotationClassDescriptor.equals(desc)) {
                classNames.add(className);
            }
            return null;
        }
    }

    class AnnotationFieldVisitor extends FieldVisitor {
        AnnotationFieldVisitor() {
            super(Opcodes.ASM7);
        }

        @Override
        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
            if (visible && annotationClassDescriptor.equals(desc)) {
                classNames.add(className);
            }
            return null;
        }
    }
}
