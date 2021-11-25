package io.neonbee.internal;

import static io.vertx.core.net.impl.URIDecoder.decodeURIComponent;

import java.io.File;
import java.net.URL;
import java.util.Collections;
import java.util.List;



import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.impl.verticle.CompilingClassLoader;
import io.vertx.core.impl.verticle.JavaSourceContext;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;

public class NeonbeeCompilingClassLoader extends ClassLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(CompilingClassLoader.class);

    private static final List<String> COMPILER_OPTIONS = Collections.EMPTY_LIST;
    /* Arrays.asList("-classpath", System.getProperty("java.class.path"), "-target", "11", "-verbose"); */

    private final JavaSourceContext javaSourceContext;

    private final NeonbeeMemoryFileManager fileManager;

    public NeonbeeCompilingClassLoader(ClassLoader loader, String sourceName) {
        super(loader);
        URL resource = getResource(sourceName);
        if (resource == null) {
            throw new RuntimeException("Resource not found: " + sourceName);
        }
        // Need to urldecode it too, since bug in JDK URL class which does not url decode it, so if it contains spaces
        // you are screwed
        File sourceFile = new File(decodeURIComponent(resource.getFile(), false));
        if (!sourceFile.canRead()) {
            throw new RuntimeException("File not found: " + sourceFile.getAbsolutePath() + " current dir is: "
                    + new File(".").getAbsolutePath());
        }

        this.javaSourceContext = new JavaSourceContext(sourceFile);

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try {

            JavaCompiler javaCompiler = ToolProvider.getSystemJavaCompiler();
            if (javaCompiler == null) {
                throw new RuntimeException("Unable to detect java compiler, make sure you're using a JDK not a JRE!");
            }
            StandardJavaFileManager standardFileManager = javaCompiler.getStandardFileManager(null, null, null);

            standardFileManager.setLocation(StandardLocation.SOURCE_PATH,
                    Collections.singleton(javaSourceContext.getSourceRoot()));
            fileManager = new NeonbeeMemoryFileManager(standardFileManager);

            // TODO - this needs to be fixed so it can compile classes from the classpath otherwise can't include
            // other .java resources from other modules

            JavaFileObject javaFile = standardFileManager.getJavaFileForInput(StandardLocation.SOURCE_PATH,
                    resolveMainClassName(), JavaFileObject.Kind.SOURCE);
            JavaCompiler.CompilationTask task = javaCompiler.getTask(null, fileManager, diagnostics, COMPILER_OPTIONS,
                    null, Collections.singleton(javaFile));
            boolean valid = task.call();
            if (valid) {
                for (Diagnostic<?> d : diagnostics.getDiagnostics()) {
                    String code = d.getCode();
                    if (code == null || (!code.startsWith("compiler.warn.annotation.method.not.found")
                            && !"compiler.warn.proc.processor.incompatible.source.version".equals(code))) {
                        LOGGER.info(d);
                    }
                }
            } else {
                for (Diagnostic<?> d : diagnostics.getDiagnostics()) {
                    LOGGER.warn(d);
                }
                throw new RuntimeException("Compilation failed!");
            }
        } catch (Exception e) {
            throw new RuntimeException("Compilation failed", e);
        }
    }

    private String resolveMainClassName() {
        return javaSourceContext.getClassName();
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        byte[] bytecode = getClassBytes(name);
        if (bytecode == null) {
            throw new ClassNotFoundException(name);
        }
        return defineClass(name, bytecode, 0, bytecode.length);
    }

    public byte[] getClassBytes(String name) {
        return fileManager.getCompiledClass(name);
    }
}
