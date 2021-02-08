package io.neonbee.internal.processor;

import static io.neonbee.data.DataAction.CREATE;
import static io.neonbee.data.DataAction.DELETE;
import static io.neonbee.data.DataAction.READ;
import static io.neonbee.data.DataAction.UPDATE;
import static io.neonbee.entity.EntityVerticle.requestEntity;
import static io.neonbee.internal.Helper.EMPTY;
import static org.apache.olingo.commons.api.http.HttpStatusCode.OK;

import java.security.InvalidParameterException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.apache.olingo.commons.api.data.ContextURL;
import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.data.Property;
import org.apache.olingo.commons.api.edm.EdmEntitySet;
import org.apache.olingo.commons.api.edm.EdmEntityType;
import org.apache.olingo.commons.api.edm.EdmPrimitiveTypeKind;
import org.apache.olingo.commons.api.format.ContentType;
import org.apache.olingo.commons.api.http.HttpHeader;
import org.apache.olingo.commons.api.http.HttpStatusCode;
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
import org.apache.olingo.server.api.uri.queryoption.SelectOption;

import com.google.common.annotations.VisibleForTesting;

import io.neonbee.data.DataAction;
import io.neonbee.data.DataQuery;
import io.neonbee.data.DataRequest;
import io.neonbee.data.internal.DataContextImpl;
import io.neonbee.entity.EntityWrapper;
import io.neonbee.internal.processor.odata.edm.EdmHelper;
import io.neonbee.internal.processor.odata.expression.EntityComparison;
import io.neonbee.logging.LoggingFacade;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.RoutingContext;

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
    public void readEntity(ODataRequest request, ODataResponse response, UriInfo uriInfo, ContentType responseFormat)
            throws ODataApplicationException, ODataLibraryException {
        /*
         * See https://docs.oasis-open.org/odata/odata/v4.01/odata-v4.01-part2-url-conventions.html#sec_URLComponents
         * for details about how the OData URL broken down into its component parts.
         */
        UriResourceEntitySet uriResourceEntitySet = (UriResourceEntitySet) uriInfo.getUriResourceParts().get(0);
        EdmEntitySet edmEntitySet = uriResourceEntitySet.getEntitySet();
        EdmEntityType edmEntityType = edmEntitySet.getEntityType();
        handleEntityIgnoringBody(edmEntityType, request, READ,
                handleReadEntityResult(uriResourceEntitySet, edmEntitySet, uriInfo, response, responseFormat, OK));
    }

    @Override
    public void createEntity(ODataRequest request, ODataResponse response, UriInfo uriInfo, ContentType requestFormat,
            ContentType responseFormat) throws ODataApplicationException, ODataLibraryException {
        /*
         * See https://docs.oasis-open.org/odata/odata/v4.01/odata-v4.01-part2-url-conventions.html#sec_URLComponents
         * for details about how the OData URL broken down into its component parts.
         */
        UriResourceEntitySet uriResourceEntitySet = (UriResourceEntitySet) uriInfo.getUriResourceParts().get(0);
        handleEntity(uriResourceEntitySet, request, requestFormat, CREATE, handleCreateEntityResult(response));
    }

    @Override
    public void updateEntity(ODataRequest request, ODataResponse response, UriInfo uriInfo, ContentType requestFormat,
            ContentType responseFormat) throws ODataApplicationException, ODataLibraryException {
        /*
         * See https://docs.oasis-open.org/odata/odata/v4.01/odata-v4.01-part2-url-conventions.html#sec_URLComponents
         * for details about how the OData URL broken down into its component parts.
         */
        UriResourceEntitySet uriResourceEntitySet = (UriResourceEntitySet) uriInfo.getUriResourceParts().get(0);
        handleEntity(uriResourceEntitySet, request, requestFormat, UPDATE, handleUpdateEntityResult(response));
    }

    @Override
    public void deleteEntity(ODataRequest request, ODataResponse response, UriInfo uriInfo)
            throws ODataApplicationException, ODataLibraryException {
        /*
         * See https://docs.oasis-open.org/odata/odata/v4.01/odata-v4.01-part2-url-conventions.html#sec_URLComponents
         * for details about how the OData URL broken down into its component parts.
         */
        UriResourceEntitySet uriResourceEntitySet = (UriResourceEntitySet) uriInfo.getUriResourceParts().get(0);
        handleEntityIgnoringBody(uriResourceEntitySet.getEntityType(), request, DELETE,
                handleDeleteEntityResult(response));
    }

    private void handleEntityIgnoringBody(EdmEntityType edmEntityType, ODataRequest request, DataAction action,
            Handler<AsyncResult<EntityWrapper>> entityHandler) {
        handleEntity(edmEntityType, request, action, null, entityHandler);
    }

    private void handleEntity(UriResourceEntitySet uriResourceEntitySet, ODataRequest request,
            ContentType requestFormat, DataAction action, Handler<AsyncResult<EntityWrapper>> entityHandler)
            throws DeserializerException {
        EdmEntityType edmEntityType = uriResourceEntitySet.getEntitySet().getEntityType();
        Entity entity = odata.createDeserializer(requestFormat).entity(request.getBody(), edmEntityType).getEntity();
        if (UPDATE.equals(action)) {
            EdmHelper.addKeyPredicateValues(entity, uriResourceEntitySet, routingContext);
        }
        handleEntity(edmEntityType, request, action, entity, entityHandler);
    }

    void handleEntity(EdmEntityType edmEntityType, ODataRequest request, DataAction action, Entity entity,
            Handler<AsyncResult<EntityWrapper>> entityHandler) {
        String uriPath = request.getRawRequestUri().replaceFirst(request.getRawBaseUri(), EMPTY);
        Buffer body = new EntityWrapper(edmEntityType.getFullQualifiedName(), entity).toBuffer(vertx);
        DataQuery query = new DataQuery(action, uriPath, request.getRawQueryPath(), request.getAllHeaders(), body)
                .addHeader("X-HTTP-Method", request.getMethod().name());

        requestEntity(vertx, new DataRequest(edmEntityType.getFullQualifiedName(), query),
                new DataContextImpl(routingContext)).onComplete(entityHandler);
    }

    @VisibleForTesting
    @SuppressWarnings("PMD.PrematureDeclaration")
    Handler<AsyncResult<EntityWrapper>> handleReadEntityResult(UriResourceEntitySet uriResourceEntitySet,
            EdmEntitySet edmEntitySet, UriInfo uriInfo, ODataResponse response, ContentType responseFormat,
            HttpStatusCode responseStatus) {
        return asyncEntity -> handleResult(asyncEntity, processPromise -> {
            // The result entity that will be returned
            Entity foundEntity = null;

            // The unfiltered list of entities provided for the requested entityset. At this point in time the
            // provided key predicates was not yet applied.
            List<Entity> receivedEntities = asyncEntity.result().getEntities();
            if (receivedEntities.isEmpty()) {
                response.setStatusCode(HttpStatusCode.NOT_FOUND.getStatusCode());
                processPromise.complete();
                return;
            }

            try {
                foundEntity = findEntityByKeyPredicates(routingContext, uriResourceEntitySet, receivedEntities);
            } catch (Exception e) {
                LOGGER.correlateWith(routingContext).error(e.getMessage(), e);
                processPromise
                        .fail(new ODataApplicationException("Error during processing key predicates: " + e.getMessage(),
                                HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), Locale.ENGLISH));
            }

            EntitySerializerOptions.Builder optionsBuilder =
                    EntitySerializerOptions.with().contextURL(ContextURL.with().entitySet(edmEntitySet).build());

            // Apply $select system query option. The selection is handled by the olingo odata server lib,
            // therefore only the context URL and serializer options have to be configured correctly.
            SelectOption selectOption = uriInfo.getSelectOption();
            if (selectOption != null) {
                // Property names of the $select, in order to build the context URL
                try {
                    optionsBuilder.contextURL(ContextURL
                            .with().entitySet(edmEntitySet).selectList(odata.createUriHelper()
                                    .buildContextURLSelectList(edmEntitySet.getEntityType(), null, selectOption))
                            .build());
                    optionsBuilder.select(selectOption);
                } catch (SerializerException e) {
                    processPromise.fail(
                            new ODataApplicationException("Error during processing select option: " + e.getMessage(),
                                    HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), Locale.ENGLISH));
                }
            }

            // Return the OData response
            try {
                if (foundEntity == null) { // No entity with provided key predicates found
                    response.setStatusCode(HttpStatusCode.NOT_FOUND.getStatusCode());
                } else { // Return the found entity
                    response.setContent(odata.createSerializer(responseFormat)
                            .entity(serviceMetadata, edmEntitySet.getEntityType(), foundEntity, optionsBuilder.build())
                            .getContent());
                    response.setStatusCode(responseStatus.getStatusCode());
                    response.setHeader(HttpHeader.CONTENT_TYPE, responseFormat.toContentTypeString());
                }
                processPromise.complete();
            } catch (SerializerException e) {
                processPromise.fail(e);
            }
        });
    }

    static Entity findEntityByKeyPredicates(RoutingContext routingContext, UriResourceEntitySet uriResourceEntitySet,
            List<Entity> entities) throws ODataApplicationException {
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
        List<Entity> foundEntities = Optional.ofNullable(entities)
                .orElseThrow(() -> new InvalidParameterException("The passed 'entities' parameter must not be null."))
                .stream().filter(Objects::nonNull).filter(entity -> {
                    // Get the names of all properties of the entity
                    List<String> propertyNames = entity.getProperties().stream().filter(Objects::nonNull)
                            .map(Property::getName).collect(Collectors.toUnmodifiableList());

                    // Check if the entity contains all key properties
                    return propertyNames.containsAll(keyPropertyNames) //
                            // Find the entities matching all keys
                            && keyPropertyNames.stream().filter(Objects::nonNull).allMatch(keyPropertyName -> { //
                                // Get the provided value of the key predicate
                                String keyPropertyValue = EdmHelper.extractValueFromLiteral(routingContext,
                                        keyPredicates.get(keyPropertyName));

                                // Check if the entity's key property matches the key property from the key
                                // predicate by comparing the provided value with the current entity's value
                                try {
                                    // Get the EdmPrimitiveTypeKind like Edm.String or Edm.Int32 of the key property
                                    EdmPrimitiveTypeKind edmPrimitiveTypeKind = EdmHelper
                                            .getEdmPrimitiveTypeKindByPropertyType(uriResourceEntitySet.getEntitySet()
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
                    "Error during processing the request. More than one entity with the same ids (key properties) was found, but ids (key properties) have to be unique.",
                    HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), Locale.ENGLISH);
        }
        return null;
    }

    @VisibleForTesting
    <T> Handler<AsyncResult<T>> handleCreateEntityResult(ODataResponse response) {
        return asyncEntity -> handleResult(asyncEntity, processPromise -> {
            /*
             * TODO: Upon successful completion, the response MUST contain a Location header that contains the edit URL
             * or read URL of the created entity.
             *
             * TODO: Upon successful completion the service MUST respond with either 201 Created (in this case, the
             * response body MUST contain the resource created), or 204 No Content (in this case the response body is
             * empty) if the request included a return Prefer header with a value of return=minimal. See
             * https://docs.oasis-open.org/odata/odata/v4.01/odata-v4.01-part1-protocol.html#_Toc31358871
             */
            response.setStatusCode(HttpStatusCode.NO_CONTENT.getStatusCode());
            processPromise.complete();
        });
    }

    @VisibleForTesting
    <T> Handler<AsyncResult<T>> handleDeleteEntityResult(ODataResponse response) {
        return asyncEntity -> handleResult(asyncEntity, processPromise -> {
            response.setStatusCode(HttpStatusCode.NO_CONTENT.getStatusCode());
            processPromise.complete();
        });
    }

    @VisibleForTesting
    <T> Handler<AsyncResult<T>> handleUpdateEntityResult(ODataResponse response) {
        return asyncEntity -> handleResult(asyncEntity, processPromise -> {
            /*
             * TODO: Upon successful completion the service responds with either 200 OK (in this case, the response body
             * MUST contain the resource updated), or 204 No Content (in this case the response body is empty). The
             * client may request that the response SHOULD include a body by specifying a Prefer header with a value of
             * return=representation, or by specifying the system query options $select or $expand.
             */
            response.setStatusCode(HttpStatusCode.NO_CONTENT.getStatusCode());
            processPromise.complete();
        });
    }

    private <T> void handleResult(AsyncResult<T> result, Consumer<Promise<Void>> onSuccess) {
        Promise<Void> processPromise = getProcessPromise();
        if (result.failed()) {
            processPromise.fail(result.cause());
        } else {
            onSuccess.accept(processPromise);
        }
    }
}
