package io.neonbee.internal;

import static io.neonbee.test.helper.FileSystemHelper.createTempDirectory;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.common.base.Strings;
import io.vertx.core.impl.verticle.CompilingClassLoader;

/**
 * The purpose of this interface is offering a tool to transform a class template into compiled byte code. A class
 * template could be simply a text file containing the source code of a class. Of course this source code should have
 * some placeholders, otherwise it is not really a template.
 *
 * <p>
 * <b>Important:</b> At least the class name must be a placeholder.
 * <p>
 *
 * To transform the template into byte code, three steps are needed:
 * <ol>
 * <li>Load the class template.</li>
 * <li>Replace all placeholders in the template with concrete values. This step transforms the template into valid Java
 * source code and will be done in the <i>reifyTemplate</i> method.</li>
 * <li>Compile the generated source code into byte code.
 * </ol>
 *
 */
public interface ClassTemplate {

    /**
     * @see Class#getSimpleName()
     *
     * @return The simple class name of the verticle
     */
    String getSimpleName();

    /**
     * @see Class#getPackageName()
     *
     * @return The full qualified package name or null for the default package.
     */
    String getPackageName();

    /**
     * @see Class#getName();
     *
     * @return The full qualified class name of the verticle
     */
    default String getClassName() {
        if (Strings.isNullOrEmpty(getPackageName())) {
            return getSimpleName();
        }

        return getPackageName() + "." + getSimpleName();
    }

    private String resourcePath() {
        return getClassName().replace(".", "/") + ".java";
    }

    /**
     * This method replaces the placeholder in the verticle template with the real content.
     *
     * @return a reified template which represents the source of this Verticle.
     */
    String reifyTemplate();

    /**
     * Compiles the verticle to byte code
     *
     * @return A byte array which represents the compiled byte code of the verticle.
     * @throws IOException
     */
    default byte[] compileToByteCode() throws IOException {
        Path tempDir = createTempDirectory();
        Path sourceFile = tempDir.resolve(resourcePath());
        Files.createDirectories(sourceFile.getParent());
        Files.writeString(sourceFile, reifyTemplate());

        try (URLClassLoader urlc =
                new URLClassLoader(new URL[] { tempDir.toUri().toURL() }, ClassLoader.getSystemClassLoader())) {
            CompilingClassLoader compilingLoader = new CompilingClassLoader(urlc, resourcePath());
            return compilingLoader.getClassBytes(getClassName());
        }
    }
}
