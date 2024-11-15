package io.neonbee.entity;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.Map;

import org.apache.olingo.server.api.ServiceMetadata;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.sap.cds.reflect.CdsModel;

class EntityModelTest {
    @Test
    void testStaticInstantiation() {
        CdsModel csnModelMock = Mockito.mock(CdsModel.class);
        ServiceMetadata edmxMetadataMock = Mockito.mock(ServiceMetadata.class);
        Map<String, ServiceMetadata> edmxMap = new HashMap<>(Map.of("foo", edmxMetadataMock));

        assertThrows(NullPointerException.class, () -> EntityModel.of(null, null));
        assertThrows(NullPointerException.class, () -> EntityModel.of(csnModelMock, null));
        assertThrows(NullPointerException.class, () -> EntityModel.of(null, edmxMap));

        EntityModel model = EntityModel.of(csnModelMock, edmxMap);
        assertThat(model.getCsnModel()).isEqualTo(csnModelMock);
        assertThat(model.getEdmxMetadata("foo")).isEqualTo(edmxMetadataMock);
        assertThat(model.getEdmxMetadata("bar")).isNull();
        assertThat(model.getAllEdmxMetadata()).isEqualTo(edmxMap);
        assertThat(model.getAllEdmxMetadata()).isNotSameInstanceAs(edmxMap);
        assertThrows(UnsupportedOperationException.class, () -> model.getAllEdmxMetadata().put("test", null));
    }
}
