package io.neonbee.internal.processor.odata.expression;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.test.endpoint.odata.verticle.TestService3EntityVerticle.ENTITY_DATA_1;
import static io.neonbee.test.endpoint.odata.verticle.TestService3EntityVerticle.ENTITY_DATA_3;
import static io.neonbee.test.helper.EntityHelper.createEntity;
import static org.mockito.Mockito.mock;

import java.util.List;

import org.apache.olingo.server.api.ODataApplicationException;
import org.junit.jupiter.api.Test;

import io.vertx.ext.web.RoutingContext;

class EntityChainedComparatorTest {
    @Test
    void compareTest() throws ODataApplicationException {

        // Sort by ID and name in asc order
        List<EntityComparator> comparators =
                List.of(new EntityComparator(mock(RoutingContext.class), "ID", false, "Edm.Int32"),
                        new EntityComparator(mock(RoutingContext.class), "name", false, "Edm.String"));
        EntityChainedComparator chainedComparator = new EntityChainedComparator(comparators);

        assertThat(chainedComparator.compare(createEntity(ENTITY_DATA_3), createEntity(ENTITY_DATA_1))).isEqualTo(1);

        assertThat(chainedComparator.compare(createEntity(ENTITY_DATA_1), createEntity(ENTITY_DATA_1))).isEqualTo(0);

        assertThat(chainedComparator.compare(createEntity(ENTITY_DATA_1), createEntity(ENTITY_DATA_3))).isEqualTo(-1);
    }
}
