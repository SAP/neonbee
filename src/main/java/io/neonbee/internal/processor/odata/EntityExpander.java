package io.neonbee.internal.processor.odata;

import static io.neonbee.entity.EntityVerticle.requestEntity;
import static io.neonbee.internal.Helper.allComposite;
import static io.vertx.core.Future.succeededFuture;
import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.data.EntityCollection;
import org.apache.olingo.commons.api.data.Link;
import org.apache.olingo.commons.api.edm.EdmEntityType;
import org.apache.olingo.commons.api.edm.EdmNavigationProperty;
import org.apache.olingo.commons.api.edm.EdmReferentialConstraint;
import org.apache.olingo.commons.api.edm.constants.EdmTypeKind;
import org.apache.olingo.server.api.uri.UriResourceNavigation;
import org.apache.olingo.server.api.uri.queryoption.ExpandOption;

import io.neonbee.data.DataQuery;
import io.neonbee.data.DataRequest;
import io.neonbee.data.internal.DataContextImpl;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.web.RoutingContext;

public final class EntityExpander {
    private final List<EdmNavigationProperty> navigationProperties;

    private final Map<EdmEntityType, List<Entity>> fetchedEntities;

    private EntityExpander(List<EdmNavigationProperty> navigationProperties,
            Map<EdmEntityType, List<Entity>> fetchedEntities) {
        this.navigationProperties = navigationProperties;
        this.fetchedEntities = fetchedEntities;
    }

    /**
     * Creating the EntityExpander is an asynchronous operation, because during the creation the EntityExpander fetches
     * all referenced and <b>potentially</b> required entities based on the expand options. When the EntityExpander is
     * created successfully, the expand of an entity happens synchronously.
     *
     * @param vertx          The Vert.x instance
     * @param expandOption   The expand options of the OData request
     * @param routingContext The routingContext of the request
     * @return A {@link Future} holding a {@link EntityExpander} when it is completed.
     */
    public static Future<EntityExpander> create(Vertx vertx, ExpandOption expandOption, RoutingContext routingContext) {
        if (expandOption != null) {
            List<EdmNavigationProperty> navigationProperties = getNavigationProperties(expandOption);
            Map<EdmEntityType, List<Entity>> fetchedEntities = new HashMap<>();

            List<Future<?>> fetchFutures =
                    navigationProperties.stream().map(EdmNavigationProperty::getType).distinct().map(type -> {
                        DataRequest req = new DataRequest(type.getFullQualifiedName(), new DataQuery());
                        return requestEntity(vertx, req, new DataContextImpl(routingContext))
                                .map(ew -> fetchedEntities.put(type, ew.getEntities()));
                    }).collect(toList());
            return allComposite(fetchFutures).map(v -> new EntityExpander(navigationProperties, fetchedEntities));
        } else {
            return succeededFuture(new EntityExpander(List.of(), Map.of()));
        }
    }

    private static List<EdmNavigationProperty> getNavigationProperties(ExpandOption expandOption) {
        return expandOption.getExpandItems().stream().map(item -> item.getResourcePath().getUriResourceParts().get(0))
                .filter(UriResourceNavigation.class::isInstance).map(UriResourceNavigation.class::cast)
                .map(UriResourceNavigation::getProperty).collect(toList());
    }

    /**
     * Expands the attributes of the passed Entity.
     *
     * @param entityToExpand The entity to expand
     */
    public void expand(Entity entityToExpand) {
        for (EdmNavigationProperty navigationProperty : navigationProperties) {
            if (!EdmTypeKind.ENTITY.equals(navigationProperty.getType().getKind())) {
                throw new UnsupportedOperationException("At the moment only type Entity can be expanded");
            }

            List<Entity> entitiesToLink = getRelatedEntities(entityToExpand, navigationProperty);
            linkEntities(entityToExpand, navigationProperty, entitiesToLink);
        }
    }

    private void linkEntities(Entity entity, EdmNavigationProperty navigationProperty, List<Entity> entitiesToLink) {
        Link link = new Link();
        link.setTitle(navigationProperty.getName());
        // Reveal if navigation property is Collection or Entity
        if (navigationProperty.isCollection()) {
            EntityCollection expandCollection = new EntityCollection();
            expandCollection.getEntities().addAll(entitiesToLink);
            link.setInlineEntitySet(expandCollection);
        } else {
            link.setInlineEntity(entitiesToLink.get(0));
        }
        entity.getNavigationLinks().add(link);
    }

    private List<Entity> getRelatedEntities(Entity entityToExpand, EdmNavigationProperty navigationProperty) {
        boolean isCollection = navigationProperty.isCollection();

        List<Entity> filteredEntities = new ArrayList<>(fetchedEntities.get(navigationProperty.getType()));

        List<EdmReferentialConstraint> constraints =
                isCollection ? navigationProperty.getPartner().getReferentialConstraints()
                        : navigationProperty.getReferentialConstraints();

        for (EdmReferentialConstraint constraint : constraints) {
            String propertyName = isCollection ? constraint.getReferencedPropertyName() : constraint.getPropertyName();
            String referencePropertyName =
                    isCollection ? constraint.getPropertyName() : constraint.getReferencedPropertyName();

            Object propertyValue = entityToExpand.getProperty(propertyName).getValue();
            List<Entity> remainingEntities = List.copyOf(filteredEntities);
            filteredEntities.clear();
            for (Entity referenceEntity : remainingEntities) {
                Object referencePropertyValue = referenceEntity.getProperty(referencePropertyName).getValue();
                if (propertyValue.equals(referencePropertyValue)) {
                    filteredEntities.add(referenceEntity);
                }
            }
        }
        return filteredEntities;
    }
}
