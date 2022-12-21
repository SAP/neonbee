package io.neonbee.internal.handler.factories;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.internal.handler.factories.SessionHandlerFactory.createSessionStore;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;

import io.neonbee.NeonBee;
import io.neonbee.config.ServerConfig;
import io.neonbee.config.ServerConfig.SessionHandling;
import io.vertx.core.Vertx;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.shareddata.SharedData;
import io.vertx.ext.web.handler.SessionHandler;
import io.vertx.ext.web.sstore.ClusteredSessionStore;
import io.vertx.ext.web.sstore.LocalSessionStore;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class SessionHandlerFactoryTest {

    @Test
    void testCreateHandlerNone(Vertx vertx, VertxTestContext testContext) {
        try (MockedStatic<NeonBee> staticNeonBeeMock = mockStatic(NeonBee.class)) {
            NeonBee neonBeeMock = mock(NeonBee.class);
            ServerConfig serverConfig = new ServerConfig();
            serverConfig.setSessionHandling(SessionHandling.NONE);
            when(neonBeeMock.getServerConfig()).thenReturn(serverConfig);
            when(neonBeeMock.getVertx()).thenReturn(vertx);
            staticNeonBeeMock.when(() -> NeonBee.get()).thenReturn(neonBeeMock);

            new SessionHandlerFactory().createHandler()
                    .onComplete(testContext.succeeding(instance -> testContext.verify(() -> {
                        assertThat(instance).isInstanceOf(SessionHandlerFactory.NoOpHandler.class);
                        testContext.completeNow();
                    })));
        }
    }

    @Test
    void testCreateHandlerLocal(Vertx vertx, VertxTestContext testContext) {
        try (MockedStatic<NeonBee> staticNeonBeeMock = mockStatic(NeonBee.class)) {
            NeonBee neonBeeMock = mock(NeonBee.class);
            ServerConfig serverConfig = new ServerConfig();
            serverConfig.setSessionHandling(ServerConfig.SessionHandling.LOCAL);
            when(neonBeeMock.getServerConfig()).thenReturn(serverConfig);
            when(neonBeeMock.getVertx()).thenReturn(vertx);
            staticNeonBeeMock.when(() -> NeonBee.get()).thenReturn(neonBeeMock);

            new SessionHandlerFactory().createHandler()
                    .onComplete(testContext.succeeding(instance -> testContext.verify(() -> {
                        assertThat(instance).isInstanceOf(SessionHandler.class);
                        testContext.completeNow();
                    })));
        }
    }

    @Test
    void testCreateHandlerClustered(Vertx vertx, VertxTestContext testContext) {
        try (MockedStatic<NeonBee> staticNeonBeeMock = mockStatic(NeonBee.class)) {
            NeonBee neonBeeMock = mock(NeonBee.class);
            ServerConfig serverConfig = new ServerConfig();
            serverConfig.setSessionHandling(ServerConfig.SessionHandling.CLUSTERED);
            when(neonBeeMock.getServerConfig()).thenReturn(serverConfig);
            when(neonBeeMock.getVertx()).thenReturn(vertx);
            staticNeonBeeMock.when(() -> NeonBee.get()).thenReturn(neonBeeMock);

            new SessionHandlerFactory().createHandler()
                    .onComplete(testContext.succeeding(instance -> testContext.verify(() -> {
                        assertThat(instance).isInstanceOf(SessionHandler.class);
                        testContext.completeNow();
                    })));
        }
    }

    @Test
    void testCreateSessionStore() {
        Vertx mockedVertx = mock(VertxInternal.class);
        when(mockedVertx.isClustered()).thenReturn(false);
        when(mockedVertx.sharedData()).thenReturn(mock(SharedData.class));

        assertThat(createSessionStore(mockedVertx, SessionHandling.NONE).isEmpty()).isTrue();
        assertThat(createSessionStore(mockedVertx, SessionHandling.LOCAL).get()).isInstanceOf(LocalSessionStore.class);
        assertThat(createSessionStore(mockedVertx, SessionHandling.CLUSTERED).get())
                .isInstanceOf(LocalSessionStore.class);
    }

    @Test
    void testCreateSessionStoreClustered() {
        Vertx mockedVertx = mock(VertxInternal.class);
        when(mockedVertx.isClustered()).thenReturn(true);
        when(mockedVertx.sharedData()).thenReturn(mock(SharedData.class));

        assertThat(createSessionStore(mockedVertx, SessionHandling.NONE).isEmpty()).isTrue();
        assertThat(createSessionStore(mockedVertx, SessionHandling.LOCAL).get()).isInstanceOf(LocalSessionStore.class);
        assertThat(createSessionStore(mockedVertx, SessionHandling.CLUSTERED).get())
                .isInstanceOf(ClusteredSessionStore.class);
    }

}
