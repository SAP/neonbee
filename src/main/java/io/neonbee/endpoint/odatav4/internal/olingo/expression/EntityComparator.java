package io.neonbee.endpoint.odatav4.internal.olingo.expression;

import java.util.Comparator;

import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.edm.EdmPrimitiveTypeKind;
import org.apache.olingo.server.api.ODataApplicationException;

import io.neonbee.endpoint.odatav4.internal.olingo.edm.EdmHelper;
import io.vertx.ext.web.RoutingContext;

public class EntityComparator implements Comparator<Entity>, EntityComparison {
    private final RoutingContext routingContext;

    private final String sortPropertyName;

    private final boolean isDescending;

    private final EdmPrimitiveTypeKind propertyTypeKind;

    EntityComparator(RoutingContext routingContext, String sortPropertyName, boolean isDescending,
            EdmPrimitiveTypeKind propertyTypeKind) {
        this.routingContext = routingContext;
        this.sortPropertyName = sortPropertyName;
        this.isDescending = isDescending;
        this.propertyTypeKind = propertyTypeKind;
    }

    EntityComparator(RoutingContext routingContext, String sortPropertyName, boolean isDescending,
            String propertyTypeKind) throws ODataApplicationException {
        this(routingContext, sortPropertyName, isDescending,
                EdmHelper.getEdmPrimitiveTypeKindByPropertyType(propertyTypeKind));
    }

    @Override
    public int compare(Entity entity1, Entity entity2) {
        Object value1 = entity1.getProperty(sortPropertyName).getValue();
        Object value2 = entity2.getProperty(sortPropertyName).getValue();

        // Sort null values last in case of 'asc' order
        if (value1 == null) {
            return (value2 == null) ? 0 : (isDescending ? -1 : 1);
        } else if (value2 == null) {
            return isDescending ? 1 : -1;
        }

        int compareResult = comparePropertyValues(routingContext, value1, value2, propertyTypeKind, sortPropertyName);

        // If the requested sort order is 'desc' reverse the order
        return isDescending ? -compareResult : compareResult;
    }
}
