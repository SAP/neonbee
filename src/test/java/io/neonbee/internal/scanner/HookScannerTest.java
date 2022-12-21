package io.neonbee.internal.scanner;

import static com.google.common.truth.Truth.assertThat;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.neonbee.internal.BasicJar;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class HookScannerTest {
    @Test
    @DisplayName("Should find classes which have methods that are annnotaed with @Hook or @Hooks")
    void scanForHooksTest(Vertx vertx, VertxTestContext testContext)
            throws IOException, URISyntaxException, ClassNotFoundException {
        BasicJar jarWithHookAnnotation = new AnnotatedClassTemplate("HodorHook", "method")
                .setMethodAnnotation("@Hook(HookType.ONCE_PER_REQUEST)")
                .setImports(List.of("io.neonbee.hook.Hook", "io.neonbee.hook.HookType")).asJar();
        BasicJar jarWithHooksAnnotation = new AnnotatedClassTemplate("HodorHooks", "method")
                .setMethodAnnotation("@Hooks({ @Hook(HookType.ONCE_PER_REQUEST), @Hook(HookType.AFTER_STARTUP) })")
                .setImports(List.of("io.neonbee.hook.Hook", "io.neonbee.hook.Hooks", "io.neonbee.hook.HookType"))
                .asJar();

        BasicJar jarWithNoHookAnnotation =
                new AnnotatedClassTemplate("Hodor", "method").setMethodAnnotation("@Deprecated").asJar();

        URL[] urlc = Stream.of(jarWithHookAnnotation.writeToTempURL(), jarWithHooksAnnotation.writeToTempURL(),
                jarWithNoHookAnnotation.writeToTempURL()).flatMap(Stream::of).toArray(URL[]::new);
        ClassLoader loader = new URLClassLoader(urlc, null);

        Class<?> expectedHookClass = loader.loadClass("method.HodorHook");
        Class<?> expectedHooksClass = loader.loadClass("method.HodorHooks");
        HookScanner hs = new HookScanner(loader);

        hs.scanForHooks(vertx).onComplete(testContext.succeeding(hooks -> testContext.verify(() -> {
            assertThat(hooks).containsExactly(expectedHookClass, expectedHooksClass);
            testContext.completeNow();
        })));
    }
}
