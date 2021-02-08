package io.neonbee.internal.scanner;

import static io.neonbee.test.helper.ResourceHelper.TEST_RESOURCES;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.neonbee.internal.BasicJar;
import io.neonbee.internal.ClassTemplate;

public class AnnotatedClassTemplate implements ClassTemplate {
    private static final String PLACEHOLDER_PACKAGE = "<package>";

    private static final String PLACEHOLDER_IMPORTS = "<imports>";

    private static final String PLACEHOLDER_CLASS_NAME = "<ClassName>";

    private static final String PLACEHOLDER_TYPE_ANNOTATION = "<TypeAnnotation>";

    private static final String PLACEHOLDER_FIELD_ANNOTATION = "<FieldAnnotation>";

    private static final String PLACEHOLDER_METHOD_ANNOTATION = "<MethodAnnotation>";

    private final String packageName;

    private final String simpleClassName;

    private final String template;

    private String typeAnnotation;

    private String fieldAnnotation;

    private String methodAnnotation;

    private List<String> imports = List.of();

    /**
     * Creates a dummy annotated class
     *
     * @param simpleClassName The simple class name of the new class
     * @throws IOException Template file could not be read
     */
    public AnnotatedClassTemplate(String simpleClassName) throws IOException {
        this(simpleClassName, null);
    }

    /**
     * Creates a dummy annotated class
     *
     * @param simpleClassName The simple class name of the new class
     * @param packageName     The package name of the class. Pass null for default package
     * @throws IOException Template file could not be read
     */
    public AnnotatedClassTemplate(String simpleClassName, String packageName) throws IOException {
        this.packageName = packageName;
        this.simpleClassName = simpleClassName;
        this.template = TEST_RESOURCES.getRelated("AnnotatedClass.java.template").toString();
    }

    public AnnotatedClassTemplate setTypeAnnotation(String typeAnnotation) {
        this.typeAnnotation = typeAnnotation;
        return this;
    }

    public AnnotatedClassTemplate setFieldAnnotation(String fieldAnnotation) {
        this.fieldAnnotation = fieldAnnotation;
        return this;
    }

    public AnnotatedClassTemplate setMethodAnnotation(String methodAnnotation) {
        this.methodAnnotation = methodAnnotation;
        return this;
    }

    public AnnotatedClassTemplate setImports(List<String> imports) {
        this.imports = imports;
        return this;
    }

    @Override
    public String getPackageName() {
        return packageName;
    }

    private String buildImportString() {
        StringBuilder sb = new StringBuilder();
        imports.forEach(i -> sb.append("import ").append(i).append(";\n"));
        return sb.toString();
    }

    @Override
    public String reifyTemplate() {
        String packageNameReplacement = "";
        if (packageName != null) {
            packageNameReplacement = "package " + packageName + ";";
        }

        return template.replace(PLACEHOLDER_CLASS_NAME, simpleClassName)
                .replace(PLACEHOLDER_IMPORTS, buildImportString()).replace(PLACEHOLDER_PACKAGE, packageNameReplacement)
                .replace(PLACEHOLDER_TYPE_ANNOTATION, Optional.ofNullable(typeAnnotation).orElse(""))
                .replace(PLACEHOLDER_FIELD_ANNOTATION, Optional.ofNullable(fieldAnnotation).orElse(""))
                .replace(PLACEHOLDER_METHOD_ANNOTATION, Optional.ofNullable(methodAnnotation).orElse(""));
    }

    @Override
    public String getSimpleName() {
        return simpleClassName;
    }

    public BasicJar asJar() throws IOException {
        return new BasicJar(Map.of(BasicJar.getJarEntryName(getClassName()), compileToByteCode()));
    }
}
