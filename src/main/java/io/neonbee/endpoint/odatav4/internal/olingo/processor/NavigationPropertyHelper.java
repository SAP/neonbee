package io.neonbee.endpoint.odatav4.internal.olingo.processor;

import static io.neonbee.entity.EntityVerticle.requestEntity;
import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.apache.olingo.commons.api.http.HttpStatusCode.INTERNAL_SERVER_ERROR;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.edm.EdmEntitySet;
import org.apache.olingo.commons.api.edm.EdmNavigationProperty;
import org.apache.olingo.commons.api.edm.EdmReferentialConstraint;
import org.apache.olingo.commons.api.edm.FullQualifiedName;
import org.apache.olingo.server.api.ODataApplicationException;
import org.apache.olingo.server.api.uri.UriResource;
import org.apache.olingo.server.api.uri.UriResourceNavigation;

import io.neonbee.data.DataQuery;
import io.neonbee.data.DataRequest;
import io.neonbee.data.internal.DataContextImpl;
import io.neonbee.entity.EntityWrapper;
import io.neonbee.logging.LoggingFacade;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.web.RoutingContext;

public final class NavigationPropertyHelper {
    private static final LoggingFacade LOGGER = LoggingFacade.create();

    /**
     * Fetches the related entity collection to the passed navigation property.
     *
     * @param navigationProperty the navigation property
     * @param vertx              the current Vert.x instance
     * @param routingContext     the current routing context
     * @return a {@link Future} holding the related {@link Entity} collection
     */
    public static Future<List<Entity>> fetchReferencedEntities(EdmNavigationProperty navigationProperty, Vertx vertx,
            RoutingContext routingContext) {
        FullQualifiedName fqn = navigationProperty.getType().getFullQualifiedName();
        DataRequest req = new DataRequest(fqn, new DataQuery(fqn.getNamespace() + "/" + fqn.getName()));
        return requestEntity(vertx, req, new DataContextImpl(routingContext)).map(EntityWrapper::getEntities);
    }

    /**
     * Filters the referenced entities based on the navigation property.
     *
     * @param navigationProperty the navigation property
     * @param sourceEntity       the entity with navigation property
     * @param referencedEntities the entities of the referenced type
     * @return a {@link List} with all related {@link Entity entities}
     */
    public static List<Entity> getRelatedEntities(EdmNavigationProperty navigationProperty, Entity sourceEntity,
            List<Entity> referencedEntities) {
        List<Entity> filteredEntities = new ArrayList<>(referencedEntities);
        boolean isCollection = navigationProperty.isCollection();
        List<EdmReferentialConstraint> constraints =
                isCollection ? navigationProperty.getPartner().getReferentialConstraints()
                        : navigationProperty.getReferentialConstraints();

        for (EdmReferentialConstraint constraint : constraints) {
            String propertyName = isCollection ? constraint.getReferencedPropertyName() : constraint.getPropertyName();
            String referencePropertyName =
                    isCollection ? constraint.getPropertyName() : constraint.getReferencedPropertyName();

            Object propertyValue = sourceEntity.getProperty(propertyName).getValue();
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

    /**
     * Gets the entity set of the navigation target.
     *
     * @param uriResourceEntitySet  the entity set of the requested resource
     * @param edmNavigationProperty the requested navigation property inside the requested resource
     * @param routingContext        the current routing context to log errors
     * @return the entity set of the navigation target
     * @throws ODataApplicationException if entity set of navigation target can't be retrieved.
     */
    public static EdmEntitySet getNavigationTargetEntitySet(EdmEntitySet uriResourceEntitySet,
            EdmNavigationProperty edmNavigationProperty, RoutingContext routingContext)
            throws ODataApplicationException {
        try {
            String navPropName = edmNavigationProperty.getName();
            return (EdmEntitySet) uriResourceEntitySet.getRelatedBindingTarget(navPropName);
        } catch (Exception e) {
            String msg = "Can't retrieve entity set of navigation target.";
            LOGGER.correlateWith(routingContext).error(msg, e);
            throw new ODataApplicationException(msg, INTERNAL_SERVER_ERROR.getStatusCode(), Locale.ROOT, e);
        }
    }

    /**
     * Fetches the entity of the navigation target.
     *
     * @param navigationPart the URI resource part holding the navigation target
     * @param sourceEntity   the entity with navigation property
     * @param vertx          the current Vert.x instance
     * @param routingContext the current routing context to log errors
     * @return a {@link Future} holding the {@link Entity} of the navigation target.
     */
    public static Future<Entity> fetchNavigationTargetEntity(UriResource navigationPart, Entity sourceEntity,
            Vertx vertx, RoutingContext routingContext) {
        return fetchNavigationTargetEntities(navigationPart, sourceEntity, vertx, routingContext).compose(entities -> {
            if (entities.size() == 1) {
                return succeededFuture(entities.get(0));
            } else {
                LOGGER.correlateWith(routingContext).error("Expected one target entity but got: {}", entities.size());
                return failedFuture("Unexpected error occur during fetching navigation target");
            }
        });
    }

    /**
     * Fetches the entities of the navigation target.
     *
     * @param navigationPart the URI resource part holding the navigation target
     * @param sourceEntity   the entity with navigation property
     * @param vertx          the current Vert.x instance
     * @param routingContext the current routing context to log errors
     * @return a Future holding the entity of the navigation target.
     */
    public static Future<List<Entity>> fetchNavigationTargetEntities(UriResource navigationPart, Entity sourceEntity,
            Vertx vertx, RoutingContext routingContext) {
        if (navigationPart instanceof UriResourceNavigation) {
            EdmNavigationProperty edmNavigationProperty = ((UriResourceNavigation) navigationPart).getProperty();
            return fetchReferencedEntities(edmNavigationProperty, vertx, routingContext)
                    .map(entities -> getRelatedEntities(edmNavigationProperty, sourceEntity, entities));
        } else {
            return failedFuture("Expected second path segment to be a navigation property");
        }
    }

    /**
     * Chooses which entity set should be used to serialize the response. The original requested one, or a different one
     * in case of a different navigation target.
     *
     * @param resourceParts     the parts of the request
     * @param resourceEntitySet the original requested resource entity set
     * @param routingContext    the current routing context to log errors
     * @return The entity set which should be used to serialize the response.
     * @throws ODataApplicationException if entity set of navigation target can't be retrieved.
     */
    public static EdmEntitySet chooseEntitySet(List<UriResource> resourceParts, EdmEntitySet resourceEntitySet,
            RoutingContext routingContext) throws ODataApplicationException {
        if (resourceParts.size() == 1) {
            return resourceEntitySet;
        } else {
            EdmNavigationProperty navProp = ((UriResourceNavigation) resourceParts.get(1)).getProperty();
            return getNavigationTargetEntitySet(resourceEntitySet, navProp, routingContext);
        }
    }

    private NavigationPropertyHelper() {

    }
}
