package io.neonbee.internal.scanner;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.test.helper.ReflectionHelper.getValueOfPrivateField;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;

import java.beans.Transient;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

import com.google.common.collect.Streams;

import io.neonbee.internal.BasicJar;
import io.neonbee.internal.helper.ThreadHelper;
import io.neonbee.internal.scanner.ClassPathScanner.CloseableClassPathScanner;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class ClassPathScannerTest {
    @Test
    @DisplayName("Check latest ASM version")
    void checkASMVersion() throws NoSuchFieldException, IllegalAccessException {
        int setAsm = getValueOfPrivateField(ClassVisitor.class,
                new AnnotationClassVisitor(Nullable.class, TYPE), "api");
        int maxAsm = Opcodes.class.getField("ASM"
                + Arrays.stream(Opcodes.class.getFields()).map(Field::getName).filter(name -> name.startsWith("ASM"))
                        .map(name -> name.substring("ASM".length())).filter(name -> name.matches("\\d+"))
                        .mapToInt(Integer::parseInt).max().getAsInt())
                .getInt(null);
        assertThat(setAsm).isEqualTo(maxAsm);
    }

    @Test
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
                .toList();

        new ClassPathScanner(urlc).scanManifestFiles(vertx, attribute1Name)
                .onComplete(testContext.succeeding(list -> testContext.verify(() -> {
                    assertThat(list).containsExactlyElementsIn(expected);
                    testContext.completeNow();
                })));
    }

    @Test
    @DisplayName("Should find files on the class path that match the passed prediction")
    void scanWithPredicateTest(Vertx vertx, VertxTestContext testContext) {
        List<String> expected =
                Stream.of(ClassPathScanner.class, ClassPathScannerTest.class, CloseableClassPathScanner.class)
                        .map(Class::getName).map(name -> name.replace(".", File.separator) + ".class")
                        .toList();

        new ClassPathScanner(ThreadHelper.getClassLoader()) // NOPMD
                .scanWithPredicate(vertx,
                        name -> name.startsWith(ClassPathScanner.class.getName().replace(".", File.separator)))
                .onComplete(testContext.succeeding(paths -> testContext.verify(() -> {
                    assertThat(paths).isNotEmpty();
                    if (!paths.stream().allMatch(path -> expected.stream().anyMatch(path::endsWith))) {
                        testContext.failNow("Not all paths matched an expected value " + paths);
                    } else {
                        testContext.completeNow();
                    }
                })));
    }

    @Test
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
    @DisplayName("Should find classes which the class or any field or method within is annotated with a given annotation")
    void scanForAnnotation(Vertx vertx, VertxTestContext testContext) throws IOException {
        BasicJar jarWithTypeAnnotatedClass =
                new AnnotatedClassTemplate("Hodor", "type").setTypeAnnotation("@Deprecated").asJar();
        BasicJar jarWithAbstractTypeAnnotatedClass =
                new AnnotatedClassTemplate("public abstract", "AbstractHodor", "type").setTypeAnnotation("@Deprecated")
                        .asJar();
        BasicJar jarWithFieldAnnotatedClass =
                new AnnotatedClassTemplate("Hodor", "field").setFieldAnnotation("@Deprecated").asJar();
        BasicJar jarWithMethodAnnotatedClass =
                new AnnotatedClassTemplate("Hodor", "method").setMethodAnnotation("@Deprecated").asJar();

        BasicJar jarWithMethodAnnotatedClass2 = new AnnotatedClassTemplate("Hodor2", "method")
                .setMethodAnnotation("@Transient").setImports(List.of("java.beans.Transient")).asJar();

        URL[] urlc = Stream
                .of(jarWithTypeAnnotatedClass.writeToTempURL(),
                        jarWithAbstractTypeAnnotatedClass.writeToTempURL(),
                        jarWithFieldAnnotatedClass.writeToTempURL(),
                        jarWithMethodAnnotatedClass.writeToTempURL(),
                        jarWithMethodAnnotatedClass2.writeToTempURL())
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
            assertThat(filterInjectedFilesForIdeCoverageRuns(list)).containsExactly(varargs);
            checkpoint.flag();
        })));
    }

    /**
     * Filter IDE specific files
     *
     * IntelliJ IDEA seems to inject a bunch of libraries to the classpath when running tests in coverage mode. This
     * leads to issues when running tests where classpath scanning is involved, because for example classes like
     * org.jetbrains.coverage.org.objectweb.asm.ClassReader and org.jetbrains.coverage.org.objectweb.asm.Opcodes (as
     * well as many more) are found during the classpath scan even if they not match the lookup criteria. Therefore, the
     * list of found files is filtered to clean it up from IntelliJ IDEA injected classes.
     *
     * @param list of classes to be filtered
     * @return the filtered list of classes without any IDE specific classes
     */
    private static List<String> filterInjectedFilesForIdeCoverageRuns(List<String> list) {
        return list.stream().filter(s -> !s.startsWith("org.jetbrains.coverage")).toList();
    }
}
