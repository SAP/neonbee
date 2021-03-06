package io.neonbee.logging;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.internal.handler.CorrelationIdHandler.CORRELATION_ID;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import io.neonbee.data.DataContext;
import io.neonbee.data.internal.DataContextImpl;
import io.vertx.ext.web.RoutingContext;

@SuppressWarnings("PMD.MoreThanOneLogger")
public class LoggingFacadeTest {
    private static final String DUMMY_LOG_MSG = "HODOR";

    private static final Throwable DUMMY_THROWABLE = new Exception("Exception");

    private static final LoggingFacade MOCKED_LOGGING_FACADE = mock(LoggingFacade.class, CALLS_REAL_METHODS); // NOPMD

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
    }

    @Test
    void testTrace() {
        MOCKED_LOGGING_FACADE.trace(DUMMY_LOG_MSG);
        MOCKED_LOGGING_FACADE.trace(DUMMY_LOG_MSG, DUMMY_THROWABLE);

        verify(MOCKED_LOGGING_FACADE, times(1)).trace(eq(DUMMY_LOG_MSG));
        verify(MOCKED_LOGGING_FACADE, times(1)).trace(eq(DUMMY_LOG_MSG), eq(DUMMY_THROWABLE));
    }

    @Test
    void testDebug() {
        MOCKED_LOGGING_FACADE.debug(DUMMY_LOG_MSG);
        MOCKED_LOGGING_FACADE.debug(DUMMY_LOG_MSG, DUMMY_THROWABLE);

        verify(MOCKED_LOGGING_FACADE, times(1)).debug(eq(DUMMY_LOG_MSG));
        verify(MOCKED_LOGGING_FACADE, times(1)).debug(eq(DUMMY_LOG_MSG), eq(DUMMY_THROWABLE));
    }

    @Test
    void testInfo() {
        MOCKED_LOGGING_FACADE.info(DUMMY_LOG_MSG);
        MOCKED_LOGGING_FACADE.info(DUMMY_LOG_MSG, DUMMY_THROWABLE);

        verify(MOCKED_LOGGING_FACADE, times(1)).info(eq(DUMMY_LOG_MSG));
        verify(MOCKED_LOGGING_FACADE, times(1)).info(eq(DUMMY_LOG_MSG), eq(DUMMY_THROWABLE));
    }

    @Test
    void testWarn() {
        MOCKED_LOGGING_FACADE.warn(DUMMY_LOG_MSG);
        MOCKED_LOGGING_FACADE.warn(DUMMY_LOG_MSG, DUMMY_THROWABLE);

        verify(MOCKED_LOGGING_FACADE, times(1)).warn(eq(DUMMY_LOG_MSG));
        verify(MOCKED_LOGGING_FACADE, times(1)).warn(eq(DUMMY_LOG_MSG), eq(DUMMY_THROWABLE));
    }

    @Test
    void testError() {
        MOCKED_LOGGING_FACADE.error(DUMMY_LOG_MSG);
        MOCKED_LOGGING_FACADE.error(DUMMY_LOG_MSG, DUMMY_THROWABLE);

        verify(MOCKED_LOGGING_FACADE, times(1)).error(eq(DUMMY_LOG_MSG));
        verify(MOCKED_LOGGING_FACADE, times(1)).error(eq(DUMMY_LOG_MSG), eq(DUMMY_THROWABLE));
    }

    @Test
    void testUnsupportedOperations() {
        Logger logger = LoggingFacade.masqueradeLogger(LoggerFactory.getLogger("wololo"));
        Marker marker = MarkerFactory.getDetachedMarker("anymarker");

        assertThrows(UnsupportedOperationException.class, () -> logger.info(marker, "test"));
        assertThrows(UnsupportedOperationException.class, () -> logger.error(marker, "test"));
        assertThrows(UnsupportedOperationException.class, () -> logger.warn(marker, "test"));
        assertThrows(UnsupportedOperationException.class, () -> logger.debug(marker, "test"));
        assertThrows(UnsupportedOperationException.class, () -> logger.trace(marker, "test"));

        assertThrows(UnsupportedOperationException.class, () -> logger.info(marker, "test1 {}", "test2"));
        assertThrows(UnsupportedOperationException.class, () -> logger.error(marker, "test1 {}", "test2"));
        assertThrows(UnsupportedOperationException.class, () -> logger.warn(marker, "test1 {}", "test2"));
        assertThrows(UnsupportedOperationException.class, () -> logger.debug(marker, "test1 {}", "test2"));
        assertThrows(UnsupportedOperationException.class, () -> logger.trace(marker, "test1 {}", "test2"));

        assertThrows(UnsupportedOperationException.class, () -> logger.info(marker, "test1 {} {}", "test2", "test3"));
        assertThrows(UnsupportedOperationException.class, () -> logger.error(marker, "test1 {} {}", "test2", "test3"));
        assertThrows(UnsupportedOperationException.class, () -> logger.warn(marker, "test1 {} {}", "test2", "test3"));
        assertThrows(UnsupportedOperationException.class, () -> logger.debug(marker, "test1 {} {}", "test2", "test3"));
        assertThrows(UnsupportedOperationException.class, () -> logger.trace(marker, "test1 {} {}", "test2", "test3"));

        assertThrows(UnsupportedOperationException.class,
                () -> logger.info(marker, "test1 {} {} {}", "test2", "test3", "test4"));
        assertThrows(UnsupportedOperationException.class,
                () -> logger.error(marker, "test1 {} {} {}", "test2", "test3", "test4"));
        assertThrows(UnsupportedOperationException.class,
                () -> logger.warn(marker, "test1 {} {} {}", "test2", "test3", "test4"));
        assertThrows(UnsupportedOperationException.class,
                () -> logger.debug(marker, "test1 {} {} {}", "test2", "test3", "test4"));
        assertThrows(UnsupportedOperationException.class,
                () -> logger.trace(marker, "test1 {} {} {}", "test2", "test3", "test4"));

        Exception anyException = new Exception();
        assertThrows(UnsupportedOperationException.class, () -> logger.info(marker, "test1", anyException));
        assertThrows(UnsupportedOperationException.class, () -> logger.error(marker, "test1", anyException));
        assertThrows(UnsupportedOperationException.class, () -> logger.warn(marker, "test1", anyException));
        assertThrows(UnsupportedOperationException.class, () -> logger.debug(marker, "test1", anyException));
        assertThrows(UnsupportedOperationException.class, () -> logger.trace(marker, "test1", anyException));
    }
}
