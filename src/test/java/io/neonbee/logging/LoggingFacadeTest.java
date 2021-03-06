package io.neonbee.logging;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.internal.handler.CorrelationIdHandler.CORRELATION_ID;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.neonbee.data.DataContext;
import io.neonbee.data.internal.DataContextImpl;
import io.vertx.ext.web.RoutingContext;

@SuppressWarnings({ "PMD.MoreThanOneLogger", "PMD.ProperLogger" })
public class LoggingFacadeTest {
    private static final LoggingFacade MOCKED_LOGGING_FACADE = mock(LoggingFacade.class, CALLS_REAL_METHODS);

    @BeforeEach
    void setUp() {
        reset(MOCKED_LOGGING_FACADE);
    }

    @Test
    void testFactoryMethods() {
        Logger logger = LoggerFactory.getLogger("wololo");
        assertThat(LoggingFacade.masqueradeLogger(logger).getName()).isEqualTo(logger.getName());

        String loggerName = "Hodor";
        assertThat(LoggingFacade.create(loggerName).getName()).isEqualTo(loggerName);

        assertThat(LoggingFacade.create(String.class).getName()).isEqualTo(String.class.getName());
        assertThat(LoggingFacade.create().getName()).isEqualTo(LoggingFacadeTest.class.getName());
    }

    @Test
    void testCorrelateWith() {
        String correlId = "Hodor";
        RoutingContext routingContextMock = mock(RoutingContext.class);
        when(routingContextMock.get(eq(CORRELATION_ID))).thenReturn(correlId);

        LoggingFacade mockedLoggingFacade = mock(LoggingFacade.class);
        when(mockedLoggingFacade.correlateWith(any(DataContext.class))).thenCallRealMethod();
        when(mockedLoggingFacade.correlateWith(any(RoutingContext.class))).thenCallRealMethod();

        mockedLoggingFacade.correlateWith(new DataContextImpl(correlId, null));
        mockedLoggingFacade.correlateWith(routingContextMock);

        verify(mockedLoggingFacade, times(2)).correlateWith(eq(correlId));

        when(mockedLoggingFacade.correlateWith(ArgumentMatchers.<RoutingContext>isNull())).thenCallRealMethod();
        assertThrows(NullPointerException.class, () -> mockedLoggingFacade.correlateWith((RoutingContext) null));
    }
}
