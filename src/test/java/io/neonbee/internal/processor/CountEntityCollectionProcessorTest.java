package io.neonbee.internal.processor;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.internal.processor.CountEntityCollectionProcessor.TOO_MANY_PARTS_EXCEPTION;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;

import org.apache.olingo.server.api.uri.UriInfo;
import org.apache.olingo.server.api.uri.UriResource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CountEntityCollectionProcessorTest {

    @Test
    @DisplayName("Throw an exception when URIResource has more than two parts")
    void testExceptionUriParts() {
        UriInfo mockedUriInfo = mock(UriInfo.class);
        when(mockedUriInfo.getUriResourceParts()).thenReturn(Arrays.asList(new UriResource[] { null, null, null }));

        CountEntityCollectionProcessor processor = new CountEntityCollectionProcessor(null, null, null);
        UnsupportedOperationException exception = assertThrows(UnsupportedOperationException.class,
                () -> processor.readEntityCollection(null, null, mockedUriInfo, null));
        assertThat(exception).isEqualTo(TOO_MANY_PARTS_EXCEPTION);
    }
}
