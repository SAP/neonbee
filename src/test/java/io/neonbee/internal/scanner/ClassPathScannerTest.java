package io.neonbee.internal.scanner;

import static com.google.common.truth.Truth.assertThat;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;

import java.beans.Transient;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.google.common.collect.Streams;
import io.neonbee.internal.BasicJar;

public class ClassPathScannerTest {

    @Test
    @DisplayName("Should find passed attribute in all Manifest files")
    void scanManifestFilesTest() throws IOException {
        String attribute1Name = "Attr1";
        String attribute2Name = "Attr2";
        List<String> manifest1Attribute1Values = List.of("M1A1V1", "M1A1V2");
        List<String> manifest1Attribute2Values = List.of("M1A2V1", "M1A2V2");
        List<String> manifest2Attribute1Values = List.of("M2A1V1", "M2A1V2");

        BasicJar jarWithManifest1 = new BasicJar(Map.of(attribute1Name, String.join(";", manifest1Attribute1Values),
                attribute2Name, String.join(";", manifest1Attribute2Values)), Map.of());
        BasicJar jarWithManifest2 =
                new BasicJar(Map.of(attribute1Name, String.join(";", manifest2Attribute1Values)), Map.of());

        URLClassLoader urlc =
                new URLClassLoader(
                        new URL[] { jarWithManifest1.writeToTempPath().toUri().toURL(),
                                jarWithManifest2.writeToTempPath().toUri().toURL() },
                        ClassLoader.getSystemClassLoader());

        List<String> expected = Streams.concat(manifest1Attribute1Values.stream(), manifest2Attribute1Values.stream())
                .collect(Collectors.toList());

        assertThat(new ClassPathScanner(urlc).scanManifestFiles(attribute1Name)).containsExactlyElementsIn(expected);
    }

    @Test
    @DisplayName("Should find files on the class path that match the passed prediction")
    void scanWithPredicateTest() throws IOException {
        List<String> expected = List.of(ClassPathScanner.class.getName().replace(".", "/") + ".class",
                ClassPathScannerTest.class.getName().replace(".", "/") + ".class");

        List<String> paths = new ClassPathScanner(ClassLoader.getSystemClassLoader()).scanWithPredicate(name -> {
            return name.startsWith(ClassPathScanner.class.getName().replace(".", "/"));
        });
        paths.forEach(path -> {
            assertThat(expected.stream().anyMatch(path::endsWith)).isTrue();
        });
    }

    @Test
    @DisplayName("Should find files on the class path that are inside of a JAR file")
    void scanWithPredicateJarFile() throws IOException, URISyntaxException {
        String ressourceToFind = "MyCoolResource";
        byte[] dummyContent = "lol".getBytes(StandardCharsets.UTF_8);
        BasicJar bj = new BasicJar(Map.of(ressourceToFind, dummyContent, "somethingElse", dummyContent));
        URLClassLoader urlc = new URLClassLoader(bj.writeToTempURL(), ClassLoader.getSystemClassLoader());

        String expectedUriString = urlc.getResource(ressourceToFind).toURI().toString().replace("file:/", "file:///");
        List<URI> uris = new ClassPathScanner(urlc).scanJarFilesWithPredicate(name -> name.startsWith(ressourceToFind));
        assertThat(uris).hasSize(1);
        assertThat(uris.get(0).toString()).isEqualTo(expectedUriString);
    }

    @Test
    @DisplayName("Should find classes which the class or any field or method within is annotated with a given annotation")
    void scanForAnnotation() throws IOException, URISyntaxException {
        BasicJar jarWithTypeAnnotatedClass =
                new AnnotatedClassTemplate("Hodor", "type").setTypeAnnotation("@Deprecated").asJar();
        BasicJar jarWithFieldAnnotatedClass =
                new AnnotatedClassTemplate("Hodor", "field").setFieldAnnotation("@Deprecated").asJar();
        BasicJar jarWithMethodAnnotatedClass =
                new AnnotatedClassTemplate("Hodor", "method").setMethodAnnotation("@Deprecated").asJar();

        BasicJar jarWithMethodAnnotatedClass2 = new AnnotatedClassTemplate("Hodor2", "method")
                .setMethodAnnotation("@Transient").setImports(List.of("java.beans.Transient")).asJar();

        URL[] urlc = Stream
                .of(jarWithTypeAnnotatedClass.writeToTempURL(), jarWithFieldAnnotatedClass.writeToTempURL(),
                        jarWithMethodAnnotatedClass.writeToTempURL(), jarWithMethodAnnotatedClass2.writeToTempURL())
                .flatMap(Stream::of).toArray(URL[]::new);
        ClassPathScanner cps = new ClassPathScanner(new URLClassLoader(urlc, null));

        assertThat(cps.scanForAnnotation(Deprecated.class, TYPE)).containsExactly("type.Hodor");
        assertThat(cps.scanForAnnotation(Deprecated.class, FIELD)).containsExactly("field.Hodor");
        assertThat(cps.scanForAnnotation(Deprecated.class, METHOD)).containsExactly("method.Hodor");
        assertThat(cps.scanForAnnotation(Deprecated.class, TYPE, FIELD, METHOD)).containsExactly("type.Hodor",
                "field.Hodor", "method.Hodor");

        assertThat(cps.scanForAnnotation(List.of(Transient.class, Deprecated.class), METHOD))
                .containsExactly("method.Hodor2", "method.Hodor");
    }

    @Test
    @DisplayName("Should find passed attribute in all Manifest files")
    void retrieveManifestAttribute() throws IOException {
        String attribute1Name = "Attr1";
        String attribute2Name = "Attr2";
        String manifest1Attribute1Value = "M1A1V1";
        BasicJar jarWithManifest1 = new BasicJar(Map.of(attribute1Name, manifest1Attribute1Value), Map.of());
        URLClassLoader urlClassLoader =
                new URLClassLoader(new URL[] { jarWithManifest1.writeToTempPath().toUri().toURL() }, null);
        ClassPathScanner classPathScanner = new ClassPathScanner(urlClassLoader);

        assertThat(classPathScanner.retrieveManifestAttribute(attribute1Name)).isEqualTo(manifest1Attribute1Value);
        assertThat(classPathScanner.retrieveManifestAttribute(attribute2Name)).isNull();
    }
}
