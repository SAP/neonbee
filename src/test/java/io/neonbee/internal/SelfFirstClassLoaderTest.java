package io.neonbee.internal;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.test.helper.ResourceHelper.TEST_RESOURCES;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.neonbee.test.helper.FileSystemHelper;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class SelfFirstClassLoaderTest {
    private static final String CLASS_NAME = "CoolTestClassWithUniqueName";

    private static final String RESOURCE_NAME = "CoolResourceWithUniqueName";

    private static final String ADDRESS_PLATFORM = "addressPlatform";

    private static final byte[] RESOURCE_CONTENT_PLATFORM = "PlatformContent".getBytes(StandardCharsets.UTF_8);

    private ClassLoader originalContextClassLoader;

    @BeforeEach
    void setUp() {
        originalContextClassLoader = Thread.currentThread().getContextClassLoader();
    }

    @AfterEach
    void tearDown() {
        Thread.currentThread().setContextClassLoader(originalContextClassLoader);
    }

    @Test
    @DisplayName("Test if the decision logic which class should be loaded from parent works correct")
    void testLoadFromParent() throws IOException {
        SelfFirstClassLoader sfcl = new SelfFirstClassLoader(new URL[] {}, null,
                List.of("io.neonbee.*", "io.Hod*", "com.example.LordCitrange"));
        assertThat(sfcl.loadFromParent("io.neonbee.DataVerticle")).isTrue();
        assertThat(sfcl.loadFromParent("io.Hodor")).isTrue();
        assertThat(sfcl.loadFromParent("com.example.LordCitrange")).isTrue();

        assertThat(sfcl.loadFromParent("io.hodor.Hodor")).isFalse();
        assertThat(sfcl.loadFromParent("com.example.Hodor")).isFalse();
        assertThat(sfcl.loadFromParent("io.NeonBee")).isFalse();
        assertThat(sfcl.loadFromParent("io.neonbeefoo.Lol")).isFalse();
        sfcl.close();
    }

    @Test
    @DisplayName("Test if the decision logic which class should be loaded from parent works correct if only a wildcard is passed")
    void testLoadFromParentWithOnlyWildcard() throws IOException {
        SelfFirstClassLoader sfcl = new SelfFirstClassLoader(new URL[] {}, null, List.of("*"));
        assertThat(sfcl.loadFromParent("io.neonbee.DataVerticle")).isTrue();
        assertThat(sfcl.loadFromParent("com.example.LordCitrange")).isTrue();

        sfcl.close();
    }

    @Test
    @DisplayName("Test if empty or null strings are filtered out from passed parentPreferred strings")
    void testLoadFromParentWithEmptyOrNullStrings() throws IOException {
        List<String> expected = List.of("io.hodor.Hodor", "lord.Citrange");
        List<String> parentPreferred = new ArrayList<>(expected);
        parentPreferred.add("");
        parentPreferred.add(null);
        SelfFirstClassLoader sfcl = new SelfFirstClassLoader(new URL[] {}, null, parentPreferred);
        assertThat(sfcl.parentPreferred).containsExactlyElementsIn(expected);

        sfcl.close();
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("Test if parent preferred classes are loaded from parent class loader")
    void testParentPreferred() throws IOException, ClassNotFoundException {
        String otherClass = "OtherUniqueClassName";
        ClassLoader parentClassLoader = createClassLoaderWithJars(
                List.of(new NeonBeeModuleJar("testmodule1", new IsoVerticleTemplate(CLASS_NAME, "parentAddress")),
                        new NeonBeeModuleJar("testmodule2", new IsoVerticleTemplate(otherClass, "onlyParent"))),
                ClassLoader.getSystemClassLoader());

        Class<Verticle> verticleClassFromParent = (Class<Verticle>) parentClassLoader.loadClass(CLASS_NAME);
        Class<Verticle> otherClassFromParent = (Class<Verticle>) parentClassLoader.loadClass(otherClass);

        ClassLoader childClassLoader = createClassLoaderWithJars(
                List.of(new NeonBeeModuleJar("testmodule3", new IsoVerticleTemplate(CLASS_NAME, "childAddress")),
                        new NeonBeeModuleJar("testmodule4", new IsoVerticleTemplate(otherClass, "onlyChild"))),
                parentClassLoader, List.of(CLASS_NAME));

        Class<Verticle> verticleClassFromChild = (Class<Verticle>) childClassLoader.loadClass(CLASS_NAME);
        Class<Verticle> otherClassFromChild = (Class<Verticle>) childClassLoader.loadClass(otherClass);

        assertThat(verticleClassFromParent).isSameInstanceAs(verticleClassFromChild);
        assertThat(otherClassFromParent).isNotSameInstanceAs(otherClassFromChild);
    }

    @SuppressWarnings("unchecked")
    @Test
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    @DisplayName("Deployments must be able to load a different versions of a class, even if platform already uses this class")
    void loadClassesIsolatedTest(Vertx vertx, VertxTestContext testContext) throws ClassNotFoundException, IOException {
        Checkpoint cp = testContext.checkpoint(2);

        // Create fake system classLoader which represents the class inside the platform
        IsoVerticleTemplate platformVerticleTemplate = new IsoVerticleTemplate(CLASS_NAME, ADDRESS_PLATFORM);
        NeonBeeModuleJar platformVerticleJar = new NeonBeeModuleJar("testmodule", platformVerticleTemplate);
        // Create and add platform resources
        BasicJar platformResourceJar = new BasicJar(Map.of(RESOURCE_NAME, RESOURCE_CONTENT_PLATFORM));
        ClassLoader applicationClassLoader = createClassLoaderWithJars(
                List.of(platformVerticleJar, platformResourceJar), ClassLoader.getSystemClassLoader());
        Thread.currentThread().setContextClassLoader(applicationClassLoader);

        // Load class into class loader of the platform
        Class<Verticle> platformVerticleClass = (Class<Verticle>) applicationClassLoader.loadClass(CLASS_NAME);

        // Deploy class with context class loader and verify deployment
        deployVerticle(platformVerticleClass, vertx, testContext)
                .compose(s -> platformVerticleTemplate.sendAndVerifyPing(vertx, testContext))
                .compose(v -> platformVerticleTemplate.getResourceAsString(RESOURCE_NAME, vertx, testContext))
                .onComplete(testContext.succeeding(resourceContent -> {
                    testContext.verify(() -> {
                        assertThat(resourceContent)
                                .isEqualTo(new String(RESOURCE_CONTENT_PLATFORM, StandardCharsets.UTF_8));
                    });
                    cp.flag();
                }));

        // Create other classLoader which represents the other class
        String otherAddress = "otherAddress";
        IsoVerticleTemplate otherVerticleTemplate = new IsoVerticleTemplate(CLASS_NAME, otherAddress);
        NeonBeeModuleJar otherVerticleJar = new NeonBeeModuleJar("testmodule", otherVerticleTemplate);
        // Create and add platform resources
        byte[] resourceContentOther = "OtherContent".getBytes(StandardCharsets.UTF_8);
        BasicJar otherResourceJar = new BasicJar(Map.of(RESOURCE_NAME, resourceContentOther));
        ClassLoader otherClassLoader =
                createClassLoaderWithJars(List.of(otherVerticleJar, otherResourceJar), applicationClassLoader);

        // Load other class into other class loader with platform class loader as parent
        Class<Verticle> otherVerticleClass = (Class<Verticle>) otherClassLoader.loadClass(CLASS_NAME);

        // Deploy other class with other class loader and verify deployment
        deployVerticle(otherVerticleClass, vertx, testContext)
                .compose(s -> otherVerticleTemplate.sendAndVerifyPing(vertx, testContext))
                .compose(v -> otherVerticleTemplate.getResourceAsString(RESOURCE_NAME, vertx, testContext))
                .onComplete(testContext.succeeding(resourceContent -> {
                    testContext.verify(() -> {
                        assertThat(resourceContent).isEqualTo(new String(resourceContentOther, StandardCharsets.UTF_8));
                    });
                    cp.flag();
                }));
    }

    private Future<String> deployVerticle(Class<Verticle> verticleClass, Vertx vertx, VertxTestContext testContext) {
        return Future.future(promise -> {
            vertx.deployVerticle(verticleClass, new DeploymentOptions(), testContext.succeeding(s -> {
                promise.complete(s);
            }));
        });
    }

    private static URLClassLoader createClassLoaderWithJars(List<BasicJar> jars, ClassLoader parent)
            throws IOException {
        return createClassLoaderWithJars(jars, parent, Collections.emptyList());
    }

    private static URLClassLoader createClassLoaderWithJars(List<BasicJar> jars, ClassLoader parent,
            List<String> parentPreferred) throws IOException {
        Path tempDir = FileSystemHelper.createTempDirectory();
        List<URL> urls = new ArrayList<>();
        for (BasicJar jar : jars) {
            Path path = tempDir.resolve(UUID.randomUUID().toString() + ".jar");
            jar.writeToFile(path);
            urls.add(path.toUri().toURL());

        }
        URL[] urlsArray = new URL[urls.size()];
        urlsArray = urls.toArray(urlsArray);
        return new SelfFirstClassLoader(urlsArray, parent, parentPreferred);
    }

    private static class IsoVerticleTemplate implements ClassTemplate {
        private final String className;

        private final String ebAddress;

        private final String template;

        IsoVerticleTemplate(String className, String ebAddress) throws IOException {
            this.className = className;
            this.ebAddress = ebAddress;
            template = TEST_RESOURCES.getRelated("IsoVerticle.java.template").toString();
        }

        public Future<Void> sendAndVerifyPing(Vertx vertx, VertxTestContext testContext) {
            return Future.future(promise -> {
                vertx.eventBus().request(ebAddress.concat("/ping"), "", testContext.succeeding(resp -> {
                    testContext.verify(() -> {
                        assertThat(resp.body()).isEqualTo("Pong from: " + ebAddress);
                        promise.complete();
                    });
                }));
            });
        }

        public Future<String> getResourceAsString(String resourceName, Vertx vertx, VertxTestContext testContext) {
            return Future.future(promise -> {
                vertx.eventBus().<String>request(ebAddress + "/resources", resourceName,
                        testContext.succeeding(resp -> promise.complete(resp.body())));
            });
        }

        @Override
        public String reifyTemplate() {
            return template.replace("<VerticleClassName>", className).replace("<address>", ebAddress);
        }

        @Override
        public String getSimpleName() {
            return className;
        }

        @Override
        public String getPackageName() {
            return null;
        }
    }
}
