package io.neonbee.internal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.UUID;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;

import io.neonbee.test.helper.FileSystemHelper;

public class BasicJar {
    private final Manifest manifest;

    private final Map<ZipEntry, byte[]> content;

    /**
     * Creates a jar file with the passed content and a default manifest which only contains the manifest version
     *
     * @param content The content of the jar file
     */
    public BasicJar(Map<String, byte[]> content) {
        this(Map.of(), content);
    }

    /**
     * Creates a jar file with the passed content and an default manifest which only contains the manifest version
     *
     * @param content The content of the jar file
     */
    public BasicJar(Map<String, String> manifestAttributes, Map<String, byte[]> content) {
        this(createManifest(manifestAttributes), content.entrySet().stream()
                .collect(Collectors.toMap(entry -> new ZipEntry(entry.getKey()), Entry::getValue)));
    }

    /**
     * Creates a jar file with the passed manifest and content
     *
     * @param manifest The manifest of the jar file
     * @param content  The content of the jar file
     */
    public BasicJar(Manifest manifest, Map<ZipEntry, byte[]> content) {
        this.manifest = Objects.requireNonNull(manifest);
        this.content = content;
    }

    /**
     * Writes the jar to the passed destination
     *
     * @param destination The destination to which the jar will be written
     * @throws IOException
     */
    public void writeToFile(Path destination) throws IOException {
        Files.write(destination, createJarFile());
    }

    /**
     * Writes the jar to a file created in the temp directory of the system.
     *
     * @return The path to the jar file
     * @throws IOException
     */
    public Path writeToTempPath() throws IOException {
        Path jarPath = FileSystemHelper.createTempDirectory().resolve(UUID.randomUUID().toString());
        writeToFile(jarPath);
        return jarPath;
    }

    /**
     * Is doing the same as {@link #writeToTempPath()}, but returns an URL array instead of a path. Classloaders can
     * consume an URL array more easily then a path.
     *
     * @return The path to the jar file
     * @throws IOException
     */
    public URL[] writeToTempURL() throws IOException {
        return new URL[] { writeToTempPath().toUri().toURL() };
    }

    private byte[] createJarFile() throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            try (JarOutputStream jos =
                    Objects.isNull(manifest) ? new JarOutputStream(baos) : new JarOutputStream(baos, manifest)) {
                for (Entry<ZipEntry, byte[]> entry : content.entrySet()) {
                    jos.putNextEntry(entry.getKey());
                    jos.write(entry.getValue());
                    jos.closeEntry();
                }
            }
            return baos.toByteArray();
        }
    }

    /**
     * Creates a Manifest with the passed attributes
     *
     * @param attributes Attributes to add to the generated Manifest
     * @return A Manifest with the passed attributes
     */
    public static Manifest createManifest(Map<String, String> attributes) {
        Manifest manifest = new Manifest();
        attributes.forEach((key, value) -> manifest.getMainAttributes().put(new Attributes.Name(key), value));
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        return manifest;
    }

    /**
     * Converts a given class name into the related entry name inside of a jar.
     *
     * @param className The name of the class to add into a jar
     * @return The name of the entry in the jar which represents the class byte code
     */
    public static String getJarEntryName(String className) {
        return className.replace(".", "/").concat(".class");
    }
}
