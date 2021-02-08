package io.neonbee.internal.processor;

import static io.neonbee.entity.EntityVerticle.requestEntity;
import static io.neonbee.internal.Helper.EMPTY;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.olingo.commons.api.data.ContextURL;
import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.data.EntityCollection;
import org.apache.olingo.commons.api.edm.EdmEntitySet;
import org.apache.olingo.commons.api.edm.EdmEntityType;
import org.apache.olingo.commons.api.format.ContentType;
import org.apache.olingo.commons.api.http.HttpHeader;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.server.api.OData;
import org.apache.olingo.server.api.ODataApplicationException;
import org.apache.olingo.server.api.ODataLibraryException;
import org.apache.olingo.server.api.ODataRequest;
import org.apache.olingo.server.api.ODataResponse;
import org.apache.olingo.server.api.ServiceMetadata;
import org.apache.olingo.server.api.serializer.EntityCollectionSerializerOptions;
import org.apache.olingo.server.api.serializer.SerializerException;
import org.apache.olingo.server.api.uri.UriInfo;
import org.apache.olingo.server.api.uri.UriResourceEntitySet;
import org.apache.olingo.server.api.uri.queryoption.CountOption;
import org.apache.olingo.server.api.uri.queryoption.FilterOption;
import org.apache.olingo.server.api.uri.queryoption.OrderByOption;
import org.apache.olingo.server.api.uri.queryoption.SelectOption;
import org.apache.olingo.server.api.uri.queryoption.SkipOption;
import org.apache.olingo.server.api.uri.queryoption.TopOption;
import org.apache.olingo.server.api.uri.queryoption.expression.ExpressionVisitException;

import io.neonbee.data.DataQuery;
import io.neonbee.data.DataRequest;
import io.neonbee.data.internal.DataContextImpl;
import io.neonbee.entity.EntityWrapper;
import io.neonbee.internal.processor.odata.expression.FilterExpressionVisitor;
import io.neonbee.internal.processor.odata.expression.OrderExpressionExecutor;
import io.neonbee.logging.LoggingFacade;
import io.vertx.core.AsyncResult;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.ext.web.RoutingContext;

@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "NM_SAME_SIMPLE_NAME_AS_INTERFACE",
        justification = "Common practice in Olingo to name the implementation of the processor same as the interface")
public class EntityCollectionProcessor extends AsynchronousProcessor
        implements org.apache.olingo.server.api.processor.CountEntityCollectionProcessor {
    private static final LoggingFacade LOGGER = LoggingFacade.create();

    private OData odata;

    private ServiceMetadata serviceMetadata;

    /**
     * Creates a new EntityCollectionProcessor.
     *
     * @param vertx          the related Vert.x instance
     * @param routingContext the routingContext of the related request
     * @param processPromise the promise to complete when data has been fetched
     */
    public EntityCollectionProcessor(Vertx vertx, RoutingContext routingContext, Promise<Void> processPromise) {
        super(vertx, routingContext, processPromise);
    }

    @Override
    public void init(OData odata, ServiceMetadata serviceMetadata) {
        this.odata = odata;
        this.serviceMetadata = serviceMetadata;
    }

    @Override
    public void readEntityCollection(ODataRequest request, ODataResponse response, UriInfo uriInfo,
            ContentType responseFormat) throws ODataApplicationException, ODataLibraryException {
        EntityCollection entityCollection = new EntityCollection();
        // Retrieve the requested EntitySet from the uriInfo
        /*
         * See https://docs.oasis-open.org/odata/odata/v4.01/odata-v4.01-part2-url-conventions.html#sec_URLComponents
         * for details about how the OData URL broken down into its component parts.
         */
        EdmEntitySet edmEntitySet = ((UriResourceEntitySet) uriInfo.getUriResourceParts().get(0)).getEntitySet();
        EdmEntityType edmEntityType = edmEntitySet.getEntityType();
        // Fetch the data from backend
        Promise<Void> processPromise = getProcessPromise();
        requestEntity(vertx, new DataRequest(edmEntityType.getFullQualifiedName(), odataRequestToQuery(request)),
                new DataContextImpl(routingContext)).onComplete(asyncEntity -> {
                    if (asyncEntity.failed()) {
                        processPromise.fail(asyncEntity.cause());
                        return;
                    }
                    String collectionId = request.getRawRequestUri().replaceFirst(request.getRawBaseUri(), EMPTY);
                    ContextURL contextUrl = ContextURL.with().entitySet(edmEntitySet).build();
                    EntityCollectionSerializerOptions.Builder optionsBuilder =
                            EntityCollectionSerializerOptions.with().id(collectionId).contextURL(contextUrl);
                    List<Entity> resultEntityList = getEntities(uriInfo, processPromise, asyncEntity);
                    // Apply $count system query option. The $count system query option with a value of true
                    // specifies that the total count of items within a collection matching the request be returned
                    // along with the result. The $count system query option ignores any $top, $skip, or $expand query
                    // options, and returns the total count of results across all pages including only those results
                    // matching any specified $filter and $search.
                    CountOption countOption = uriInfo.getCountOption();
                    if ((countOption != null) && countOption.getValue()) {
                        int countResult = resultEntityList.size();
                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.correlateWith(routingContext).debug(
                                    "countOption name: {}, countOption text: {}, countOption value: {}, count result: {}",
                                    countOption.getName(), countOption.getText(), countOption.getValue(), countResult);
                        }
                        entityCollection.setCount(countResult);
                        optionsBuilder.count(countOption);
                    }
                    if (!resultEntityList.isEmpty()) {
                        applySelectQueryOption(uriInfo, edmEntitySet, edmEntityType, processPromise, optionsBuilder);
                        applyOrderByQueryOption(uriInfo, processPromise, resultEntityList);
                        resultEntityList = applySkipQueryOption(uriInfo, processPromise, resultEntityList);
                        resultEntityList = applyTopQueryOption(uriInfo, processPromise, resultEntityList);
                        entityCollection.getEntities().addAll(resultEntityList);
                    }
                    try {
                        response.setContent(odata.createSerializer(responseFormat).entityCollection(serviceMetadata,
                                edmEntityType, entityCollection, optionsBuilder.build()).getContent());
                        response.setStatusCode(HttpStatusCode.OK.getStatusCode());
                        response.setHeader(HttpHeader.CONTENT_TYPE, responseFormat.toContentTypeString());
                        processPromise.complete();
                    } catch (SerializerException e) {
                        processPromise.fail(e);
                    }
                });
    }

    private List<Entity> applyTopQueryOption(UriInfo uriInfo, Promise<Void> processPromise,
            List<Entity> resultEntityList) {
        TopOption topOption = uriInfo.getTopOption();
        List<Entity> topList = resultEntityList;
        if (topOption != null) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.correlateWith(routingContext).debug(
                        "topOption name: {}, topOption text: {}, topOption value: {}", topOption.getName(),
                        topOption.getText(), topOption.getValue());
            }
            int topValue = topOption.getValue();
            if (topValue >= 0) {
                if (topValue <= resultEntityList.size()) {
                    topList = resultEntityList.subList(0, topValue);
                } // else return all available entities
            } else {
                processPromise.fail(new ODataApplicationException("Invalid value for $top",
                        HttpStatusCode.BAD_REQUEST.getStatusCode(), Locale.ENGLISH));
            }
        }
        return topList;
    }

    private List<Entity> applySkipQueryOption(UriInfo uriInfo, Promise<Void> processPromise,
            List<Entity> resultEntityList) {
        SkipOption skipOption = uriInfo.getSkipOption();
        List<Entity> skipList = resultEntityList;
        if (skipOption != null) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.correlateWith(routingContext).debug(
                        "skipOption name: {}, skipOption text: {}, skipOption value: {}", skipOption.getName(),
                        skipOption.getText(), skipOption.getValue());
            }
            int skipValue = skipOption.getValue();
            if (skipValue >= 0) {
                if (skipValue <= resultEntityList.size()) {
                    skipList = resultEntityList.subList(skipValue, resultEntityList.size());
                } else {
                    // Skip all entities
                    skipList.clear();
                }
            } else {
                String message = "Invalid value for $skip";
                LOGGER.correlateWith(routingContext).error(message);
                processPromise.fail(new ODataApplicationException(message, HttpStatusCode.BAD_REQUEST.getStatusCode(),
                        Locale.ENGLISH));
            }
        }
        return skipList;
    }

    private void applyOrderByQueryOption(UriInfo uriInfo, Promise<Void> processPromise, List<Entity> resultEntityList) {
        OrderByOption orderByOption = uriInfo.getOrderByOption();
        if (orderByOption != null) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.correlateWith(routingContext).debug("orderByOption name: {}, orderByOption text: {}",
                        orderByOption.getName(), orderByOption.getText());
            }
            try {
                OrderExpressionExecutor.executeOrderOption(routingContext, orderByOption, resultEntityList);
            } catch (Exception e) {
                String message = "Error during processing of orderby option";
                LOGGER.correlateWith(routingContext).error(message);
                processPromise.fail(new ODataApplicationException(message, HttpStatusCode.BAD_REQUEST.getStatusCode(),
                        Locale.ENGLISH, e));
            }
        }
    }

    private void applySelectQueryOption(UriInfo uriInfo, EdmEntitySet edmEntitySet, EdmEntityType edmEntityType,
            Promise<Void> processPromise, EntityCollectionSerializerOptions.Builder optionsBuilder) {
        // Apply $select system query option. The selection is handled by the olingo odata server lib,
        // therefore only the context URL and serializer options have to be configured correctly.
        SelectOption selectOption = uriInfo.getSelectOption();
        if (selectOption != null) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.correlateWith(routingContext).debug("selectOption name: {}, selectOption text: {}",
                        selectOption.getName(), selectOption.getText());
            }
            // Property names of the $select, in order to build the context URL
            try {
                optionsBuilder.contextURL(ContextURL.with().entitySet(edmEntitySet)
                        .selectList(
                                odata.createUriHelper().buildContextURLSelectList(edmEntityType, null, selectOption))
                        .build());
                optionsBuilder.select(selectOption);
            } catch (SerializerException e) {
                String message = "Error during processing select option";
                processPromise.fail(new ODataApplicationException(message,
                        HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), Locale.ENGLISH, e));
            }
        }
    }

    private List<Entity> getEntities(UriInfo uriInfo, Promise<Void> processPromise,
            AsyncResult<EntityWrapper> asyncEntity) {
        List<Entity> entityList = asyncEntity.result().getEntities();
        if (LOGGER.isDebugEnabled()) {
            LOGGER.correlateWith(routingContext).debug("Applying filter expression on list of entities with size: {}",
                    entityList.size());
        }
        List<Entity> resultEntityList = Optional.ofNullable(uriInfo.getFilterOption())
                .map(filterOption -> entityList.stream().filter(entity -> {
                    try {
                        FilterExpressionVisitor filterExpressionVisitor =
                                new FilterExpressionVisitor(routingContext, entity);
                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.correlateWith(routingContext).debug("filterOption name: {}, filterOption text: {}",
                                    filterOption.getName(), filterOption.getText());
                            LOGGER.correlateWith(routingContext)
                                    .debug("FilterExpressionVisitor for entity '{}' was created", entity);
                        }
                        return Boolean.TRUE
                                .equals(filterOption.getExpression().accept(filterExpressionVisitor).getValue());
                    } catch (ODataApplicationException | ExpressionVisitException e) {
                        String message = "Exception in filter evaluation";
                        LOGGER.correlateWith(routingContext).error(message, e);
                        processPromise.fail(new ODataApplicationException(message,
                                HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), Locale.ENGLISH, e));
                    }
                    return false;
                }).collect(Collectors.toList())).orElse(entityList);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.correlateWith(routingContext).debug(
                    "Filter expression was applied on list of entities and led to a result list of entities with size: {}",
                    resultEntityList.size());
        }
        return resultEntityList;
    }

    private DataQuery odataRequestToQuery(ODataRequest request) {
        // the uriPath without /odata root path and without query path
        String uriPath =
                request.getRawRequestUri().replaceFirst(request.getRawBaseUri(), EMPTY).replaceFirst("\\?.*$", EMPTY);
        // the raw query path
        String rawQueryPath = request.getRawQueryPath();
        return new DataQuery(uriPath, rawQueryPath, request.getAllHeaders());
    }

    @Override
    public void countEntityCollection(ODataRequest request, ODataResponse response, UriInfo uriInfo)
            throws ODataApplicationException, ODataLibraryException {
        // Retrieve the requested EntitySet from the uriInfo
        /*
         * See https://docs.oasis-open.org/odata/odata/v4.01/odata-v4.01-part2-url-conventions.html#sec_URLComponents
         * for details about how the OData URL broken down into its component parts.
         */
        EdmEntitySet edmEntitySet = ((UriResourceEntitySet) uriInfo.getUriResourceParts().get(0)).getEntitySet();
        EdmEntityType edmEntityType = edmEntitySet.getEntityType();
        // Fetch the data from backend
        Promise<Void> processPromise = getProcessPromise();
        requestEntity(vertx, new DataRequest(edmEntityType.getFullQualifiedName(), odataRequestToQuery(request)),
                new DataContextImpl(routingContext)).onComplete(asyncEntity -> {
                    if (asyncEntity.failed()) {
                        processPromise.fail(asyncEntity.cause());
                        return;
                    }
                    // Apply $filter
                    FilterOption filterOption = uriInfo.getFilterOption();
                    long entityCount = Optional.ofNullable(filterOption).map(fo -> {
                        List<Entity> entities = asyncEntity.result().getEntities();
                        return entities.stream().filter(entity -> {
                            try {
                                return Boolean.TRUE.equals(fo.getExpression()
                                        .accept(new FilterExpressionVisitor(routingContext, entity)).getValue());
                            } catch (ODataApplicationException | ExpressionVisitException e) {
                                LOGGER.correlateWith(routingContext).error(e.getMessage(), e);
                                processPromise.fail(new ODataApplicationException("Exception in filter evaluation",
                                        HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), Locale.ENGLISH, e));
                            }
                            return false;
                        }).count();
                    }).orElse((long) asyncEntity.result().getEntities().size());
                    /*
                     * The response body MUST contain the exact count of items matching the request after applying any
                     * $filter or $search system query options, formatted as a simple primitive integer value with media
                     * type text/plain. The returned count MUST NOT be affected by $top, $skip, $orderby, or $expand.
                     * Content negotiation using the Accept request header or the $format system query option is not
                     * allowed with the path segment /$count.
                     */
                    ByteArrayInputStream serializerContent =
                            new ByteArrayInputStream(String.valueOf(entityCount).getBytes(StandardCharsets.UTF_8));
                    response.setContent(serializerContent);
                    response.setHeader(HttpHeader.CONTENT_TYPE, ContentType.TEXT_PLAIN.toContentTypeString());
                    response.setStatusCode(HttpStatusCode.OK.getStatusCode());
                    processPromise.complete();
                });
    }
}
