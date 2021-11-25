package io.neonbee.internal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;

import io.vertx.core.impl.verticle.CustomJavaFileObject;

public class NeonbeeMemoryFileManager extends ForwardingJavaFileManager<JavaFileManager> {
    private final Map<String, ByteArrayOutputStream> compiledClasses = new HashMap<>();

    public NeonbeeMemoryFileManager(JavaFileManager fileManager) {
        super(fileManager);
    }

    @Override
    public JavaFileObject getJavaFileForOutput(Location location, String className, JavaFileObject.Kind kind,
            FileObject sibling) throws IOException {
        try {
            return new SimpleJavaFileObject(new URI(""), kind) {
                @Override
                public OutputStream openOutputStream() throws IOException {
                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    compiledClasses.put(className, outputStream);
                    return outputStream;
                }
            };
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public byte[] getCompiledClass(String name) {
        ByteArrayOutputStream bytes = compiledClasses.get(name);
        if (bytes == null) {
            return null;
        }
        return bytes.toByteArray();
    }

    @Override
    public String inferBinaryName(Location location, JavaFileObject file) {
        if (file instanceof CustomJavaFileObject) {
            return ((CustomJavaFileObject) file).binaryName();
        } else {
            return super.inferBinaryName(location, file);
        }
    }

    @Override
    public Iterable<JavaFileObject> list(Location location, String packageName, Set<JavaFileObject.Kind> kinds,
            boolean recurse) throws IOException {
        return super.list(location, packageName, kinds, recurse);
    }
}
