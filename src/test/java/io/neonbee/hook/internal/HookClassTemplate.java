package io.neonbee.hook.internal;

import static io.neonbee.test.helper.ResourceHelper.TEST_RESOURCES;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.neonbee.internal.BasicJar;
import io.neonbee.internal.ClassTemplate;

public class HookClassTemplate implements ClassTemplate {
    static final Path VALID_HOOK_TEMPLATE = TEST_RESOURCES.resolveRelated("HookClass.java.template");

    static final Path INVALID_HOOK_TEMPLATE = TEST_RESOURCES.resolveRelated("InvalidHookClass.java.template");

    private static final String PLACEHOLDER_PACKAGE = "<package>";

    private static final String PLACEHOLDER_IMPORTS = "<imports>";

    private static final String PLACEHOLDER_CLASS_NAME = "<ClassName>";

    private static final String PLACEHOLDER_METHOD_ANNOTATION = "<MethodAnnotation>";

    private final String packageName;

    private final String simpleClassName;

    private final String template;

    private String methodAnnotation;

    private List<String> imports = List.of("io.neonbee.hook.Hook", "io.neonbee.hook.Hooks", "io.neonbee.hook.HookType");

    /**
     * Creates a dummy hook class.
     *
     * @param simpleClassName The simple class name of the new class
     * @throws IOException
     */
    public HookClassTemplate(Path hookTemplate, String simpleClassName) throws IOException {
        this(hookTemplate, simpleClassName, null);
    }

    /**
     * Creates a dummy hook class.
     *
     * @param simpleClassName The simple class name of the new class
     * @param packageName     The package name of the class. Pass null for default package
     * @throws IOException
     */
    public HookClassTemplate(Path hookTemplate, String simpleClassName, String packageName) throws IOException {
        this.packageName = packageName;
        this.simpleClassName = simpleClassName;
        this.template = Files.readString(hookTemplate, StandardCharsets.UTF_8);
    }

    public HookClassTemplate setMethodAnnotation(String methodAnnotation) {
        this.methodAnnotation = methodAnnotation;
        return this;
    }

    public HookClassTemplate setImports(List<String> imports) {
        this.imports = imports;
        return this;
    }

    @Override
    public String getPackageName() {
        return packageName;
    }

    private String buildImportString() {
        StringBuilder sb = new StringBuilder();
        imports.forEach(i -> sb.append("import " + i + ";\n"));
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
