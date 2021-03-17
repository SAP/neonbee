package io.neonbee.internal.processor.odata.expression;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.test.endpoint.odata.verticle.TestService3EntityVerticle.ENTITY_DATA_1;
import static io.neonbee.test.endpoint.odata.verticle.TestService3EntityVerticle.ENTITY_DATA_3;
import static io.neonbee.test.helper.EntityHelper.createEntity;

import org.apache.olingo.server.api.ODataApplicationException;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.vertx.ext.web.RoutingContext;

class EntityComparatorTest {
    @Test
    void compareTest() throws ODataApplicationException {
        // Sorty by ID asc order
        EntityComparator comparator =
                new EntityComparator(Mockito.mock(RoutingContext.class), "ID", false, "Edm.Int32");

        assertThat(comparator.compare(createEntity(ENTITY_DATA_3), createEntity(ENTITY_DATA_1))).isEqualTo(1);

        assertThat(comparator.compare(createEntity(ENTITY_DATA_1), createEntity(ENTITY_DATA_1))).isEqualTo(0);

        assertThat(comparator.compare(createEntity(ENTITY_DATA_1), createEntity(ENTITY_DATA_3))).isEqualTo(-1);
    }
}
