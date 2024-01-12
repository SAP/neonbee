package io.neonbee.endpoint.odatav4.internal.olingo.processor;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;

import org.apache.olingo.server.api.uri.UriInfo;
import org.apache.olingo.server.api.uri.UriResource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EntityProcessorTest {

    @Test
    @DisplayName("Throw an exception when URIResource has more than two parts")
    void testExceptionUriParts() {
        UriInfo mockedUriInfo = mock(UriInfo.class);
        when(mockedUriInfo.getUriResourceParts()).thenReturn(Arrays.asList(new UriResource[] { null, null, null }));

        EntityProcessor processor = new EntityProcessor(null, null, null);
        UnsupportedOperationException exception = assertThrows(UnsupportedOperationException.class,
                () -> processor.readEntity(null, null, mockedUriInfo, null));
        assertThat(exception).isInstanceOf(UnsupportedOperationException.class);
        assertThat(exception).hasMessageThat()
                .isEqualTo("Read requests with more than two resource parts are not supported.");
    }
}
