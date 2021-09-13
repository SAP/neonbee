package io.neonbee.internal.scanner;

import static com.google.common.truth.Truth.assertThat;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;

import java.beans.Transient;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.google.common.collect.Streams;

import io.neonbee.internal.BasicJar;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class ClassPathScannerTest {
    @Test
    @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
    @DisplayName("Should find passed attribute in all Manifest files")
    void scanManifestFilesTest(Vertx vertx, VertxTestContext testContext) throws IOException {
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

        new ClassPathScanner(urlc).scanManifestFiles(vertx, attribute1Name)
                .onComplete(testContext.succeeding(list -> testContext.verify(() -> {
                    assertThat(list).containsExactlyElementsIn(expected);
                    testContext.completeNow();
                })));
    }

    @Test
    @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
    @DisplayName("Should find files on the class path that match the passed prediction")
    void scanWithPredicateTest(Vertx vertx, VertxTestContext testContext) throws IOException {
        List<String> expected = List.of(ClassPathScanner.class.getName().replace(".", "/") + ".class",
                ClassPathScannerTest.class.getName().replace(".", "/") + ".class");

        new ClassPathScanner(ClassLoader.getSystemClassLoader())
                .scanWithPredicate(vertx, name -> name.startsWith(ClassPathScanner.class.getName().replace(".", "/")))
                .onComplete(testContext.succeeding(paths -> testContext.verify(() -> {
                    paths.forEach(path -> {
                        assertThat(expected.stream().anyMatch(path::endsWith)).isTrue();
                    });
                    testContext.completeNow();
                })));

    }

    @Test
    @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
    @DisplayName("Should find files on the class path that are inside of a JAR file")
    void scanWithPredicateJarFile(Vertx vertx, VertxTestContext testContext) throws IOException, URISyntaxException {
        String ressourceToFind = "MyCoolResource";
        byte[] dummyContent = "lol".getBytes(StandardCharsets.UTF_8);
        BasicJar bj = new BasicJar(Map.of(ressourceToFind, dummyContent, "somethingElse", dummyContent));
        URLClassLoader urlc = new URLClassLoader(bj.writeToTempURL(), ClassLoader.getSystemClassLoader());

        String expectedUriString = urlc.getResource(ressourceToFind).toURI().toString().replace("file:/", "file:///");
        new ClassPathScanner(urlc).scanJarFilesWithPredicate(vertx, name -> name.startsWith(ressourceToFind))
                .onComplete(testContext.succeeding(uris -> testContext.verify(() -> {
                    assertThat(uris).hasSize(1);
                    assertThat(uris.get(0).toString()).isEqualTo(expectedUriString);
                    testContext.completeNow();
                })));
    }

    @Test
    @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
    @DisplayName("Should find classes which the class or any field or method within is annotated with a given annotation")
    void scanForAnnotation(Vertx vertx, VertxTestContext testContext) throws IOException, URISyntaxException {
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

        Checkpoint annotationsScanned = testContext.checkpoint(5);

        futureContainsExactly(testContext, annotationsScanned, cps.scanForAnnotation(vertx, Deprecated.class, TYPE),
                "type.Hodor");

        futureContainsExactly(testContext, annotationsScanned, cps.scanForAnnotation(vertx, Deprecated.class, FIELD),
                "field.Hodor");
        futureContainsExactly(testContext, annotationsScanned, cps.scanForAnnotation(vertx, Deprecated.class, METHOD),
                "method.Hodor");
        futureContainsExactly(testContext, annotationsScanned,
                cps.scanForAnnotation(vertx, Deprecated.class, TYPE, FIELD, METHOD), "type.Hodor", "field.Hodor",
                "method.Hodor");

        futureContainsExactly(testContext, annotationsScanned,
                cps.scanForAnnotation(vertx, List.of(Transient.class, Deprecated.class), METHOD), "method.Hodor2",
                "method.Hodor");
    }

    private static void futureContainsExactly(VertxTestContext testContext, Checkpoint checkpoint,
            Future<List<String>> future, Object... varargs) {
        future.onComplete(testContext.succeeding(list -> testContext.verify(() -> {
            assertThat(list).containsExactly(varargs);
            checkpoint.flag();
        })));
    }
}
