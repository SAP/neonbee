package io.neonbee.logging.internal;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.logging.internal.LoggingFacadeImpl.DEFAULT_MARKER;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.Marker;

@SuppressWarnings("PMD.MoreThanOneLogger")
class LoggingFacadeImplTest {
    private static final String DUMMY_LOG_MSG = "HODOR {}";

    private static final Object DUMMY_ARGUMENT = new Object();

    private static final Throwable DUMMY_THROWABLE = new Exception("Exception");

    private static final Object[] DUMMY_ARGUMENTS = { DUMMY_ARGUMENT, DUMMY_THROWABLE };

    private final Logger mockedLogger = mock(Logger.class);

    private LoggingFacadeImpl facade;

    @BeforeEach
    void setUp() {
        reset(mockedLogger);
        facade = new LoggingFacadeImpl(mockedLogger);
    }

    @Test
    void testCorrelateWith() {
        String correlId = "hodor";
        LoggingFacadeImpl facade = new LoggingFacadeImpl(null);

        facade.correlateWith(correlId);
        Marker hodorMarker = facade.currentMarker;
        assertThat(hodorMarker.getName()).isEqualTo(correlId);

        facade.correlateWith(correlId);
        assertThat(facade.currentMarker).isSameInstanceAs(hodorMarker);

        facade.correlateWith("");
        assertThat(facade.currentMarker).isEqualTo(DEFAULT_MARKER);

        facade.correlateWith((String) null);
        assertThat(facade.currentMarker).isEqualTo(DEFAULT_MARKER);
    }

    @Test
    void testGetName() {
        facade.getName();
        verify(mockedLogger, times(1)).getName();
    }

    @Test
    void testTrace() {
        facade.isTraceEnabled();
        verify(mockedLogger, times(1)).isTraceEnabled(DEFAULT_MARKER);

        facade.trace(DUMMY_LOG_MSG, DUMMY_ARGUMENTS);
        verify(mockedLogger, times(1)).trace(DEFAULT_MARKER, DUMMY_LOG_MSG, DUMMY_ARGUMENTS);

        facade.trace(DUMMY_LOG_MSG, DUMMY_ARGUMENT);
        verify(mockedLogger, times(1)).trace(DEFAULT_MARKER, DUMMY_LOG_MSG, DUMMY_ARGUMENT);

        facade.trace(DUMMY_LOG_MSG, DUMMY_ARGUMENT, DUMMY_THROWABLE);
        verify(mockedLogger, times(1)).trace(DEFAULT_MARKER, DUMMY_LOG_MSG, DUMMY_ARGUMENT, DUMMY_THROWABLE);

        facade.trace(DUMMY_LOG_MSG, DUMMY_THROWABLE);
        verify(mockedLogger, times(1)).trace(DEFAULT_MARKER, DUMMY_LOG_MSG, DUMMY_THROWABLE);

        reset(mockedLogger);
        facade.correlateWith("anyid").trace(DUMMY_LOG_MSG);
        verify(mockedLogger, times(1)).trace((Marker) argThat(marker -> "anyid".equals(marker.toString())),
                eq(DUMMY_LOG_MSG));
    }

    @Test
    void testDebug() {
        facade.isDebugEnabled();
        verify(mockedLogger, times(1)).isDebugEnabled(DEFAULT_MARKER);

        facade.debug(DUMMY_LOG_MSG, DUMMY_ARGUMENTS);
        verify(mockedLogger, times(1)).debug(DEFAULT_MARKER, DUMMY_LOG_MSG, DUMMY_ARGUMENTS);

        facade.debug(DUMMY_LOG_MSG, DUMMY_ARGUMENT);
        verify(mockedLogger, times(1)).debug(DEFAULT_MARKER, DUMMY_LOG_MSG, DUMMY_ARGUMENT);

        facade.debug(DUMMY_LOG_MSG, DUMMY_ARGUMENT, DUMMY_THROWABLE);
        verify(mockedLogger, times(1)).debug(DEFAULT_MARKER, DUMMY_LOG_MSG, DUMMY_ARGUMENT, DUMMY_THROWABLE);

        facade.debug(DUMMY_LOG_MSG, DUMMY_THROWABLE);
        verify(mockedLogger, times(1)).debug(DEFAULT_MARKER, DUMMY_LOG_MSG, DUMMY_THROWABLE);

        reset(mockedLogger);
        facade.correlateWith("anyid").debug(DUMMY_LOG_MSG);
        verify(mockedLogger, times(1)).debug((Marker) argThat(marker -> "anyid".equals(marker.toString())),
                eq(DUMMY_LOG_MSG));
    }

    @Test
    void testInfo() {
        facade.isInfoEnabled();
        verify(mockedLogger, times(1)).isInfoEnabled(DEFAULT_MARKER);

        facade.info(DUMMY_LOG_MSG, DUMMY_ARGUMENTS);
        verify(mockedLogger, times(1)).info(DEFAULT_MARKER, DUMMY_LOG_MSG, DUMMY_ARGUMENTS);

        facade.info(DUMMY_LOG_MSG, DUMMY_ARGUMENT);
        verify(mockedLogger, times(1)).info(DEFAULT_MARKER, DUMMY_LOG_MSG, DUMMY_ARGUMENT);

        facade.info(DUMMY_LOG_MSG, DUMMY_ARGUMENT, DUMMY_THROWABLE);
        verify(mockedLogger, times(1)).info(DEFAULT_MARKER, DUMMY_LOG_MSG, DUMMY_ARGUMENT, DUMMY_THROWABLE);

        facade.info(DUMMY_LOG_MSG, DUMMY_THROWABLE);
        verify(mockedLogger, times(1)).info(DEFAULT_MARKER, DUMMY_LOG_MSG, DUMMY_THROWABLE);

        reset(mockedLogger);
        facade.correlateWith("anyid").info(DUMMY_LOG_MSG);
        verify(mockedLogger, times(1)).info((Marker) argThat(marker -> "anyid".equals(marker.toString())),
                eq(DUMMY_LOG_MSG));
    }

    @Test
    void testWarn() {
        facade.isWarnEnabled();
        verify(mockedLogger, times(1)).isWarnEnabled(DEFAULT_MARKER);

        facade.warn(DUMMY_LOG_MSG, DUMMY_ARGUMENTS);
        verify(mockedLogger, times(1)).warn(DEFAULT_MARKER, DUMMY_LOG_MSG, DUMMY_ARGUMENTS);

        facade.warn(DUMMY_LOG_MSG, DUMMY_ARGUMENT);
        verify(mockedLogger, times(1)).warn(DEFAULT_MARKER, DUMMY_LOG_MSG, DUMMY_ARGUMENT);

        facade.warn(DUMMY_LOG_MSG, DUMMY_ARGUMENT, DUMMY_THROWABLE);
        verify(mockedLogger, times(1)).warn(DEFAULT_MARKER, DUMMY_LOG_MSG, DUMMY_ARGUMENT, DUMMY_THROWABLE);

        facade.warn(DUMMY_LOG_MSG, DUMMY_THROWABLE);
        verify(mockedLogger, times(1)).warn(DEFAULT_MARKER, DUMMY_LOG_MSG, DUMMY_THROWABLE);

        reset(mockedLogger);
        facade.correlateWith("anyid").warn(DUMMY_LOG_MSG);
        verify(mockedLogger, times(1)).warn((Marker) argThat(marker -> "anyid".equals(marker.toString())),
                eq(DUMMY_LOG_MSG));
    }

    @Test
    void testError() {
        facade.isErrorEnabled();
        verify(mockedLogger, times(1)).isErrorEnabled(DEFAULT_MARKER);

        facade.error(DUMMY_LOG_MSG, DUMMY_ARGUMENTS);
        verify(mockedLogger, times(1)).error(DEFAULT_MARKER, DUMMY_LOG_MSG, DUMMY_ARGUMENTS);

        facade.error(DUMMY_LOG_MSG, DUMMY_ARGUMENT);
        verify(mockedLogger, times(1)).error(DEFAULT_MARKER, DUMMY_LOG_MSG, DUMMY_ARGUMENT);

        facade.error(DUMMY_LOG_MSG, DUMMY_ARGUMENT, DUMMY_THROWABLE);
        verify(mockedLogger, times(1)).error(DEFAULT_MARKER, DUMMY_LOG_MSG, DUMMY_ARGUMENT, DUMMY_THROWABLE);

        facade.error(DUMMY_LOG_MSG, DUMMY_THROWABLE);
        verify(mockedLogger, times(1)).error(DEFAULT_MARKER, DUMMY_LOG_MSG, DUMMY_THROWABLE);

        reset(mockedLogger);
        facade.correlateWith("anyid").error(DUMMY_LOG_MSG);
        verify(mockedLogger, times(1)).error((Marker) argThat(marker -> "anyid".equals(marker.toString())),
                eq(DUMMY_LOG_MSG));
    }

    @Test
    void testUnsupportedOperations() {
        assertThrows(UnsupportedOperationException.class, () -> facade.isTraceEnabled(DEFAULT_MARKER));
        assertThrows(UnsupportedOperationException.class, () -> facade.isDebugEnabled(DEFAULT_MARKER));
        assertThrows(UnsupportedOperationException.class, () -> facade.isInfoEnabled(DEFAULT_MARKER));
        assertThrows(UnsupportedOperationException.class, () -> facade.isWarnEnabled(DEFAULT_MARKER));
        assertThrows(UnsupportedOperationException.class, () -> facade.isErrorEnabled(DEFAULT_MARKER));

        assertThrows(UnsupportedOperationException.class, () -> facade.info(DEFAULT_MARKER, "test"));
        assertThrows(UnsupportedOperationException.class, () -> facade.error(DEFAULT_MARKER, "test"));
        assertThrows(UnsupportedOperationException.class, () -> facade.warn(DEFAULT_MARKER, "test"));
        assertThrows(UnsupportedOperationException.class, () -> facade.debug(DEFAULT_MARKER, "test"));
        assertThrows(UnsupportedOperationException.class, () -> facade.trace(DEFAULT_MARKER, "test"));

        assertThrows(UnsupportedOperationException.class, () -> facade.info(DEFAULT_MARKER, "test1 {}", "test2"));
        assertThrows(UnsupportedOperationException.class, () -> facade.error(DEFAULT_MARKER, "test1 {}", "test2"));
        assertThrows(UnsupportedOperationException.class, () -> facade.warn(DEFAULT_MARKER, "test1 {}", "test2"));
        assertThrows(UnsupportedOperationException.class, () -> facade.debug(DEFAULT_MARKER, "test1 {}", "test2"));
        assertThrows(UnsupportedOperationException.class, () -> facade.trace(DEFAULT_MARKER, "test1 {}", "test2"));

        assertThrows(UnsupportedOperationException.class,
                () -> facade.info(DEFAULT_MARKER, "test1 {} {}", "test2", "test3"));
        assertThrows(UnsupportedOperationException.class,
                () -> facade.error(DEFAULT_MARKER, "test1 {} {}", "test2", "test3"));
        assertThrows(UnsupportedOperationException.class,
                () -> facade.warn(DEFAULT_MARKER, "test1 {} {}", "test2", "test3"));
        assertThrows(UnsupportedOperationException.class,
                () -> facade.debug(DEFAULT_MARKER, "test1 {} {}", "test2", "test3"));
        assertThrows(UnsupportedOperationException.class,
                () -> facade.trace(DEFAULT_MARKER, "test1 {} {}", "test2", "test3"));

        assertThrows(UnsupportedOperationException.class,
                () -> facade.info(DEFAULT_MARKER, "test1 {} {} {}", "test2", "test3", "test4"));
        assertThrows(UnsupportedOperationException.class,
                () -> facade.error(DEFAULT_MARKER, "test1 {} {} {}", "test2", "test3", "test4"));
        assertThrows(UnsupportedOperationException.class,
                () -> facade.warn(DEFAULT_MARKER, "test1 {} {} {}", "test2", "test3", "test4"));
        assertThrows(UnsupportedOperationException.class,
                () -> facade.debug(DEFAULT_MARKER, "test1 {} {} {}", "test2", "test3", "test4"));
        assertThrows(UnsupportedOperationException.class,
                () -> facade.trace(DEFAULT_MARKER, "test1 {} {} {}", "test2", "test3", "test4"));

        Exception anyException = new Exception();
        assertThrows(UnsupportedOperationException.class, () -> facade.info(DEFAULT_MARKER, "test1", anyException));
        assertThrows(UnsupportedOperationException.class, () -> facade.error(DEFAULT_MARKER, "test1", anyException));
        assertThrows(UnsupportedOperationException.class, () -> facade.warn(DEFAULT_MARKER, "test1", anyException));
        assertThrows(UnsupportedOperationException.class, () -> facade.debug(DEFAULT_MARKER, "test1", anyException));
        assertThrows(UnsupportedOperationException.class, () -> facade.trace(DEFAULT_MARKER, "test1", anyException));
    }
}
