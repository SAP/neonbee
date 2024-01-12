package io.neonbee.endpoint.odatav4.internal.olingo.expression;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.edm.EdmProperty;
import org.apache.olingo.commons.api.edm.constants.EdmTypeKind;
import org.apache.olingo.server.api.ODataApplicationException;
import org.apache.olingo.server.api.uri.UriResource;
import org.apache.olingo.server.api.uri.UriResourcePrimitiveProperty;
import org.apache.olingo.server.api.uri.queryoption.OrderByOption;
import org.apache.olingo.server.api.uri.queryoption.expression.Member;

import io.neonbee.logging.LoggingFacade;
import io.vertx.ext.web.RoutingContext;

public final class OrderExpressionExecutor implements EntityComparison {
    private static final LoggingFacade LOGGER = LoggingFacade.create();

    private OrderExpressionExecutor() {
        // No need to instantiate
    }

    /**
     * Creates new EntityComparators based on the passed order options and sort the passed list.
     *
     * @param routingContext the current routingContent
     * @param orderByOption  the orderByOption
     * @param entityList     the list of entities to order
     * @return the passed list with the new order
     */
    public static List<Entity> executeOrderOption(RoutingContext routingContext, OrderByOption orderByOption,
            List<Entity> entityList) {

        // Sorts the list in 'asc' order by default e.g. in the case that nothing is specified
        Collections.sort(entityList, new EntityChainedComparator(orderByOption.getOrders().stream()
                .filter(orderByItem -> orderByItem.getExpression() instanceof Member).map(orderByItem -> {
                    /*
                     * See https://docs.oasis-open.org/odata/odata/v4.01/odata-v4.01-part2-url-conventions.html#
                     * sec_URLComponents for details about how the OData URL broken down into its component parts.
                     */
                    UriResource uriResource =
                            ((Member) orderByItem.getExpression()).getResourcePath().getUriResourceParts().get(0);
                    if (uriResource instanceof UriResourcePrimitiveProperty) {
                        // The property we want to sort by
                        EdmProperty property = ((UriResourcePrimitiveProperty) uriResource).getProperty();
                        if (property.getType().getKind() == EdmTypeKind.PRIMITIVE) {
                            String sortPropertyName = property.getName();
                            try {
                                return new EntityComparator(routingContext, sortPropertyName,
                                        orderByItem.isDescending(), property.getType().toString());
                            } catch (ODataApplicationException e) {
                                LOGGER.correlateWith(routingContext).error("Failure during order options execution", e);
                            }
                        }
                    }
                    return null;
                }).filter(Objects::nonNull).toList()));

        return entityList;
    }
}
