package io.neonbee.internal.processor;

import static io.neonbee.data.DataAction.CREATE;
import static io.neonbee.data.DataAction.DELETE;
import static io.neonbee.data.DataAction.READ;
import static io.neonbee.data.DataAction.UPDATE;
import static io.neonbee.entity.EntityVerticle.requestEntity;
import static io.neonbee.internal.Helper.EMPTY;
import static org.apache.olingo.commons.api.http.HttpStatusCode.INTERNAL_SERVER_ERROR;
import static org.apache.olingo.commons.api.http.HttpStatusCode.NOT_FOUND;
import static org.apache.olingo.commons.api.http.HttpStatusCode.NO_CONTENT;
import static org.apache.olingo.commons.api.http.HttpStatusCode.OK;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.olingo.commons.api.data.ContextURL;
import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.data.Property;
import org.apache.olingo.commons.api.edm.EdmEntitySet;
import org.apache.olingo.commons.api.edm.EdmEntityType;
import org.apache.olingo.commons.api.edm.EdmPrimitiveTypeKind;
import org.apache.olingo.commons.api.format.ContentType;
import org.apache.olingo.commons.api.http.HttpHeader;
import org.apache.olingo.server.api.OData;
import org.apache.olingo.server.api.ODataApplicationException;
import org.apache.olingo.server.api.ODataLibraryException;
import org.apache.olingo.server.api.ODataRequest;
import org.apache.olingo.server.api.ODataResponse;
import org.apache.olingo.server.api.ServiceMetadata;
import org.apache.olingo.server.api.deserializer.DeserializerException;
import org.apache.olingo.server.api.serializer.EntitySerializerOptions;
import org.apache.olingo.server.api.serializer.SerializerException;
import org.apache.olingo.server.api.uri.UriInfo;
import org.apache.olingo.server.api.uri.UriParameter;
import org.apache.olingo.server.api.uri.UriResourceEntitySet;

import io.neonbee.data.DataAction;
import io.neonbee.data.DataQuery;
import io.neonbee.data.DataRequest;
import io.neonbee.data.internal.DataContextImpl;
import io.neonbee.entity.EntityWrapper;
import io.neonbee.internal.processor.odata.EntityExpander;
import io.neonbee.internal.processor.odata.edm.EdmHelper;
import io.neonbee.internal.processor.odata.expression.EntityComparison;
import io.neonbee.logging.LoggingFacade;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.RoutingContext;

/*
 * See https://docs.oasis-open.org/odata/odata/v4.01/odata-v4.01-part2-url-conventions.html#sec_URLComponents
 * for details about how the OData URL broken down into its component parts.
 */
@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "NM_SAME_SIMPLE_NAME_AS_INTERFACE",
        justification = "Common practice in Olingo to name the implementation of the processor same as the interface")
public class EntityProcessor extends AsynchronousProcessor
        implements org.apache.olingo.server.api.processor.EntityProcessor {
    private static final LoggingFacade LOGGER = LoggingFacade.create();

    private static final EntityComparison ENTITY_COMPARISON = new EntityComparison() {};

    private OData odata;

    private ServiceMetadata serviceMetadata;

    /**
     * Creates a new EntityProcessor.
     *
     * @param vertx          the related Vert.x instance
     * @param routingContext the routingContext of the related request
     * @param processPromise the promise to complete when data has been fetched
     */
    public EntityProcessor(Vertx vertx, RoutingContext routingContext, Promise<Void> processPromise) {
        super(vertx, routingContext, processPromise);
    }

    @Override
    public void init(OData odata, ServiceMetadata serviceMetadata) {
        this.odata = odata;
        this.serviceMetadata = serviceMetadata;
    }

    @Override
    public void readEntity(ODataRequest request, ODataResponse response, UriInfo uriInfo, ContentType responseFormat) {
        Promise<Void> processPromise = getProcessPromise();
        UriResourceEntitySet uriResourceEntitySet = (UriResourceEntitySet) uriInfo.getUriResourceParts().get(0);

        forwardRequest(request, uriResourceEntitySet, null, READ, processPromise).onSuccess(
                handleReadEntityResult(uriResourceEntitySet, uriInfo, response, responseFormat, processPromise));
    }

    @Override
    public void createEntity(ODataRequest request, ODataResponse response, UriInfo uriInfo, ContentType requestFormat,
            ContentType responseFormat) throws ODataLibraryException {
        Promise<Void> processPromise = getProcessPromise();
        UriResourceEntitySet uriResourceEntitySet = (UriResourceEntitySet) uriInfo.getUriResourceParts().get(0);
        Entity entity = parseBody(request, uriResourceEntitySet, requestFormat);

        forwardRequest(request, uriResourceEntitySet, entity, CREATE, processPromise).onSuccess(ew -> {
            /*
             * TODO: Upon successful completion, the response MUST contain a Location header that contains the edit URL
             * or read URL of the created entity.
             *
             * TODO: Upon successful, completion the service MUST respond with either 201 Created (in this case, the
             * response body MUST contain the resource created), or 204 No Content (in this case the response body is
             * empty) if the request included a return Prefer header with a value of return=minimal. See
             * https://docs.oasis-open.org/odata/odata/v4.01/odata-v4.01-part1-protocol.html#_Toc31358871
             */
            response.setStatusCode(NO_CONTENT.getStatusCode());
            processPromise.complete();
        });
    }

    @Override
    public void updateEntity(ODataRequest request, ODataResponse response, UriInfo uriInfo, ContentType requestFormat,
            ContentType responseFormat) throws ODataLibraryException {
        Promise<Void> processPromise = getProcessPromise();
        UriResourceEntitySet uriResourceEntitySet = (UriResourceEntitySet) uriInfo.getUriResourceParts().get(0);
        Entity entity = parseBody(request, uriResourceEntitySet, requestFormat);

        EdmHelper.addKeyPredicateValues(entity, uriResourceEntitySet, routingContext);
        forwardRequest(request, uriResourceEntitySet, entity, UPDATE, processPromise).onSuccess(ew -> {
            /*
             * TODO: Upon successful completion, the service responds with either 200 OK (in this case, the response
             * body MUST contain the resource updated), or 204 No Content (in this case the response body is empty). The
             * client may request that the response SHOULD include a body by specifying a Prefer header with a value of
             * return=representation, or by specifying the system query options $select or $expand.
             */
            response.setStatusCode(NO_CONTENT.getStatusCode());
            processPromise.complete();
        });
    }

    @Override
    public void deleteEntity(ODataRequest request, ODataResponse response, UriInfo uriInfo) {
        Promise<Void> processPromise = getProcessPromise();
        UriResourceEntitySet uriResourceEntitySet = (UriResourceEntitySet) uriInfo.getUriResourceParts().get(0);

        forwardRequest(request, uriResourceEntitySet, null, DELETE, processPromise).onSuccess(ew -> {
            response.setStatusCode(NO_CONTENT.getStatusCode());
            processPromise.complete();
        });
    }

    private Entity parseBody(ODataRequest request, UriResourceEntitySet uriResourceEntitySet, ContentType requestFormat)
            throws DeserializerException {
        EdmEntityType entityType = uriResourceEntitySet.getEntitySet().getEntityType();
        return odata.createDeserializer(requestFormat).entity(request.getBody(), entityType).getEntity();
    }

    private Future<EntityWrapper> forwardRequest(ODataRequest request, UriResourceEntitySet uriResourceEntitySet,
            Entity entity, DataAction action, Promise<Void> processPromise) {
        EdmEntityType entityType = uriResourceEntitySet.getEntitySet().getEntityType();
        Buffer body = new EntityWrapper(entityType.getFullQualifiedName(), entity).toBuffer(vertx);
        DataQuery query = odataRequestToQuery(request, action, body);

        return requestEntity(vertx, new DataRequest(entityType.getFullQualifiedName(), query),
                new DataContextImpl(routingContext)).onFailure(processPromise::fail);
    }

    private Handler<EntityWrapper> handleReadEntityResult(UriResourceEntitySet uriResourceEntitySet, UriInfo uriInfo,
            ODataResponse response, ContentType responseFormat, Promise<Void> processPromise) {
        return ew -> {
            try {
                Entity foundEntity = findEntityByKeyPredicates(routingContext, uriResourceEntitySet, ew.getEntities());
                // Return the OData response
                if (foundEntity == null) { // No entity with provided key predicates found
                    response.setStatusCode(NOT_FOUND.getStatusCode());
                    processPromise.complete();
                } else { // Return the found entity
                    EntityExpander.create(vertx, uriInfo.getExpandOption(), routingContext).onSuccess(expander -> {
                        try {
                            EdmEntitySet edmEntitySet = uriResourceEntitySet.getEntitySet();
                            String selectList = odata.createUriHelper().buildContextURLSelectList(
                                    edmEntitySet.getEntityType(), uriInfo.getExpandOption(), uriInfo.getSelectOption());
                            ContextURL contextUrl =
                                    ContextURL.with().entitySet(edmEntitySet).selectList(selectList).build();
                            EntitySerializerOptions opts = EntitySerializerOptions.with().contextURL(contextUrl)
                                    .select(uriInfo.getSelectOption()).expand(uriInfo.getExpandOption()).build();

                            expander.expand(foundEntity);
                            response.setContent(odata.createSerializer(responseFormat)
                                    .entity(serviceMetadata, edmEntitySet.getEntityType(), foundEntity, opts)
                                    .getContent());
                            response.setStatusCode(OK.getStatusCode());
                            response.setHeader(HttpHeader.CONTENT_TYPE, responseFormat.toContentTypeString());
                            processPromise.complete();
                        } catch (SerializerException e) {
                            processPromise.fail(e);
                        }
                    }).onFailure(processPromise::fail);
                }
            } catch (ODataApplicationException e) {
                processPromise.fail(e);
            }
        };
    }

    static Entity findEntityByKeyPredicates(RoutingContext routingContext, UriResourceEntitySet uriResourceEntitySet,
            List<Entity> entities) throws ODataApplicationException {
        if (entities == null || entities.isEmpty()) {
            return null;
        }
        // Get the key predicates used to select a single entity out of the start entity set, or an empty map if
        // not used. For OData services the canonical form of an absolute URI identifying a single Entity is
        // formed by adding a single path segment to the service root URI. The path segment is made up of the
        // name of the Service associated with the Entity followed by the key predicate identifying the Entry
        // within the Entity Set (list of entities).
        // The canonical key predicate for single-part keys consists only of the key property value without the
        // key property name. For multi-part keys the key properties appear in the same order they appear in the
        // key definition in the service metadata.
        // See
        // https://docs.oasis-open.org/odata/odata/v4.01/odata-v4.01-part2-url-conventions.html#sec_CanonicalURL
        // for details.
        Map<String, String> keyPredicates = uriResourceEntitySet.getKeyPredicates().stream()
                .collect(Collectors.toUnmodifiableMap(UriParameter::getName, UriParameter::getText));
        // If key predicates were provided apply the filter to the list of entities received

        // The names of the key properties provided in the key predicate query
        Set<String> keyPropertyNames = keyPredicates.keySet();
        List<Entity> foundEntities = entities.stream().filter(Objects::nonNull).filter(entity -> {
            // Get the names of all properties of the entity
            List<String> propertyNames = entity.getProperties().stream().filter(Objects::nonNull).map(Property::getName)
                    .collect(Collectors.toUnmodifiableList());

            // Check if the entity contains all key properties
            return propertyNames.containsAll(keyPropertyNames) //
                    // Find the entities matching all keys
                    && keyPropertyNames.stream().filter(Objects::nonNull).allMatch(keyPropertyName -> { //
                        // Get the provided value of the key predicate
                        String keyPropertyValue =
                                EdmHelper.extractValueFromLiteral(routingContext, keyPredicates.get(keyPropertyName));

                        // Check if the entity's key property matches the key property from the key
                        // predicate by comparing the provided value with the current entity's value
                        try {
                            // Get the EdmPrimitiveTypeKind like Edm.String or Edm.Int32 of the key property
                            EdmPrimitiveTypeKind edmPrimitiveTypeKind =
                                    EdmHelper.getEdmPrimitiveTypeKindByPropertyType(uriResourceEntitySet.getEntitySet()
                                            .getEntityType().getProperty(keyPropertyName).getType().toString());

                            // Get the value of the key property of the current entity
                            Property property = entity.getProperty(keyPropertyName);
                            Object propertyValue = property.getValue();

                            // Compare the provided key property value with the entity's current value
                            return ENTITY_COMPARISON.comparePropertyValues(routingContext, propertyValue,
                                    keyPropertyValue, edmPrimitiveTypeKind, keyPropertyName) == 0;
                        } catch (ODataApplicationException e) {
                            LOGGER.correlateWith(routingContext).error(e.getMessage(), e);
                        }
                        return false;
                    });
        }).collect(Collectors.toUnmodifiableList());
        if (foundEntities.size() == 1) {
            return foundEntities.get(0);
        } else if (foundEntities.size() > 1) {
            throw new ODataApplicationException(
                    "Error during processing the request. More than one entity with the same ids (key properties) "
                            + "was found, but ids (key properties) have to be unique.",
                    INTERNAL_SERVER_ERROR.getStatusCode(), Locale.ENGLISH);
        }
        return null;
    }

    private DataQuery odataRequestToQuery(ODataRequest request, DataAction action, Buffer body) {
        // the uriPath without /odata root path and without query path
        String uriPath =
                request.getRawRequestUri().replaceFirst(request.getRawBaseUri(), EMPTY).replaceFirst("\\?.*$", EMPTY);
        // the raw query path
        String rawQueryPath = request.getRawQueryPath();
        return new DataQuery(action, uriPath, rawQueryPath, request.getAllHeaders(), body).addHeader("X-HTTP-Method",
                request.getMethod().name());
    }
}
