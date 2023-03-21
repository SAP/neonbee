package io.neonbee.internal.registry;

import static com.google.common.truth.Truth8.assertThat;
import static io.vertx.core.Future.succeededFuture;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class RegistryTest {

    @Test
    void testGetAny(VertxTestContext testContext) {
        Registry<String> dummyRegistry = mock(Registry.class);
        when(dummyRegistry.get("key")).thenReturn(succeededFuture(List.of("value")));
        doCallRealMethod().when(dummyRegistry).getAny("key");

        dummyRegistry.getAny("key").onFailure(testContext::failNow).onSuccess(optional -> {
            assertThat(optional).hasValue("value");
            testContext.completeNow();
        });
    }

    @Test
    void testRegister(VertxTestContext testContext) {
        Registry<String> dummyRegistry = mock(Registry.class);
        when(dummyRegistry.register(anyString(), anyList())).thenReturn(succeededFuture());
        doCallRealMethod().when(dummyRegistry).register(anyString(), anyString());

        dummyRegistry.register("key", "value").onFailure(testContext::failNow).onSuccess(values -> {
            verify(dummyRegistry).register(anyString(), anyList());
            testContext.completeNow();
        });
    }

    @Test
    void testUnregister(VertxTestContext testContext) {
        Registry<String> dummyRegistry = mock(Registry.class);
        when(dummyRegistry.unregister(anyString(), anyList())).thenReturn(succeededFuture());
        doCallRealMethod().when(dummyRegistry).unregister(anyString(), anyString());

        dummyRegistry.unregister("key", "value").onFailure(testContext::failNow).onSuccess(values -> {
            verify(dummyRegistry).unregister(anyString(), anyList());
            testContext.completeNow();
        });
    }
}
