package io.neonbee.logging.internal;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.logging.internal.LoggingFacadeImpl.DEFAULT_MARKER;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.Marker;

public class LoggingFacadeImplTest {
    private static final String DUMMY_LOG_MSG = "HODOR {}";

    private static final Object[] DUMMY_ARGUMENTS = { "Hodor", new Exception("Exception") };

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
        verify(mockedLogger, times(1)).isTraceEnabled();

        facade.trace(DUMMY_LOG_MSG, DUMMY_ARGUMENTS);
        verify(mockedLogger, times(1)).trace(DEFAULT_MARKER, DUMMY_LOG_MSG, DUMMY_ARGUMENTS);
    }

    @Test
    void testDebug() {
        facade.isDebugEnabled();
        verify(mockedLogger, times(1)).isDebugEnabled();

        facade.debug(DUMMY_LOG_MSG, DUMMY_ARGUMENTS);
        verify(mockedLogger, times(1)).debug(DEFAULT_MARKER, DUMMY_LOG_MSG, DUMMY_ARGUMENTS);
    }

    @Test
    void testInfo() {
        facade.isInfoEnabled();
        verify(mockedLogger, times(1)).isInfoEnabled();

        facade.info(DUMMY_LOG_MSG, DUMMY_ARGUMENTS);
        verify(mockedLogger, times(1)).info(DEFAULT_MARKER, DUMMY_LOG_MSG, DUMMY_ARGUMENTS);
    }

    @Test
    void testWarn() {
        facade.isWarnEnabled();
        verify(mockedLogger, times(1)).isWarnEnabled();

        facade.warn(DUMMY_LOG_MSG, DUMMY_ARGUMENTS);
        verify(mockedLogger, times(1)).warn(DEFAULT_MARKER, DUMMY_LOG_MSG, DUMMY_ARGUMENTS);
    }

    @Test
    void testError() {
        facade.isErrorEnabled();
        verify(mockedLogger, times(1)).isErrorEnabled();

        facade.error(DUMMY_LOG_MSG, DUMMY_ARGUMENTS);
        verify(mockedLogger, times(1)).error(DEFAULT_MARKER, DUMMY_LOG_MSG, DUMMY_ARGUMENTS);
    }
}
