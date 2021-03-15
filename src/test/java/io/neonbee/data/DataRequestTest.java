package io.neonbee.data;

import static com.google.common.truth.Truth.assertThat;

import org.apache.olingo.commons.api.edm.FullQualifiedName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DataRequestTest {

    @Test
    @DisplayName("test localPreferred handling")
    void testLocalPreferred() {
        DataRequest request = new DataRequest(new FullQualifiedName("namespace", "name"), new DataQuery());
        assertThat(request.isLocalPreferred()).isTrue();
        request.setLocalPreferred(false);
        assertThat(request.isLocalPreferred()).isFalse();
    }
}
