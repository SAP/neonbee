package io.neonbee.internal.processor;

import static io.neonbee.entity.EntityVerticle.requestEntity;
import static io.neonbee.internal.Helper.EMPTY;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.olingo.commons.api.data.ContextURL;
import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.data.EntityCollection;
import org.apache.olingo.commons.api.edm.EdmEntitySet;
import org.apache.olingo.commons.api.edm.EdmEntityType;
import org.apache.olingo.commons.api.ex.ODataException;
import org.apache.olingo.commons.api.format.ContentType;
import org.apache.olingo.commons.api.http.HttpHeader;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.server.api.OData;
import org.apache.olingo.server.api.ODataApplicationException;
import org.apache.olingo.server.api.ODataRequest;
import org.apache.olingo.server.api.ODataResponse;
import org.apache.olingo.server.api.ServiceMetadata;
import org.apache.olingo.server.api.serializer.EntityCollectionSerializerOptions;
import org.apache.olingo.server.api.serializer.SerializerException;
import org.apache.olingo.server.api.uri.UriInfo;
import org.apache.olingo.server.api.uri.UriResourceEntitySet;
import org.apache.olingo.server.api.uri.queryoption.FilterOption;
import org.apache.olingo.server.api.uri.queryoption.OrderByOption;
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
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.ext.web.RoutingContext;

/*
 * See https://docs.oasis-open.org/odata/odata/v4.01/odata-v4.01-part2-url-conventions.html#sec_URLComponents
 * for details about how the OData URL broken down into its component parts.
 */
@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "NM_SAME_SIMPLE_NAME_AS_INTERFACE",
        justification = "Common practice in Olingo to name the implementation of the processor same as the interface")
public class CountEntityCollectionProcessor extends AsynchronousProcessor
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
    public CountEntityCollectionProcessor(Vertx vertx, RoutingContext routingContext, Promise<Void> processPromise) {
        super(vertx, routingContext, processPromise);
    }

    @Override
    public void init(OData odata, ServiceMetadata serviceMetadata) {
        this.odata = odata;
        this.serviceMetadata = serviceMetadata;
    }

    @Override
    public void readEntityCollection(ODataRequest request, ODataResponse response, UriInfo uriInfo,
            ContentType responseFormat) {
        Promise<Void> processPromise = getProcessPromise();
        EntityCollection entityCollection = new EntityCollection();

        // Retrieve the requested EntitySet from the uriInfo
        EdmEntitySet edmEntitySet = ((UriResourceEntitySet) uriInfo.getUriResourceParts().get(0)).getEntitySet();
        EdmEntityType edmEntityType = edmEntitySet.getEntityType();

        // Fetch the data from backend
        fetchEntities(request, edmEntityType, ew -> {
            try {
                List<Entity> resultEntityList = applyFilterQueryOption(uriInfo.getFilterOption(), ew.getEntities());

                if (!resultEntityList.isEmpty()) {
                    applyOrderByQueryOption(uriInfo.getOrderByOption(), resultEntityList);
                    resultEntityList = applySkipQueryOption(uriInfo.getSkipOption(), resultEntityList);
                    resultEntityList = applyTopQueryOption(uriInfo.getTopOption(), resultEntityList);
                    entityCollection.getEntities().addAll(resultEntityList);
                }

                EntityCollectionSerializerOptions opts = createSerializerOptions(request, uriInfo, edmEntitySet);
                response.setContent(odata.createSerializer(responseFormat)
                        .entityCollection(serviceMetadata, edmEntityType, entityCollection, opts).getContent());
                response.setStatusCode(HttpStatusCode.OK.getStatusCode());
                response.setHeader(HttpHeader.CONTENT_TYPE, responseFormat.toContentTypeString());
                processPromise.complete();
            } catch (ODataException e) {
                processPromise.fail(e);
            }
        });
    }

    private void fetchEntities(ODataRequest request, EdmEntityType edmEntityType,
            Handler<EntityWrapper> resultHandler) {
        requestEntity(vertx, new DataRequest(edmEntityType.getFullQualifiedName(), odataRequestToQuery(request)),
                new DataContextImpl(routingContext)).onFailure(getProcessPromise()::fail).onSuccess(resultHandler);
    }

    private List<Entity> applyFilterQueryOption(FilterOption filterOption, List<Entity> unfilteredEntities)
            throws ODataException {
        List<Entity> filteredEntities = unfilteredEntities;

        if (filterOption != null) {
            LOGGER.correlateWith(routingContext).debug("Applying filter expression on list of entities with size: {}",
                    unfilteredEntities.size());
            filteredEntities = new ArrayList<>();
            for (Entity entity : unfilteredEntities) {
                FilterExpressionVisitor filterExpressionVisitor = new FilterExpressionVisitor(routingContext, entity);
                LOGGER.correlateWith(routingContext).debug("filterOption name: {}, filterOption text: {}",
                        filterOption.getName(), filterOption.getText());
                LOGGER.correlateWith(routingContext).debug("FilterExpressionVisitor for entity '{}' was created",
                        entity);
                try {
                    if (Boolean.TRUE.equals(filterOption.getExpression().accept(filterExpressionVisitor).getValue())) {
                        filteredEntities.add(entity);
                    }
                } catch (ODataApplicationException | ExpressionVisitException e) {
                    LOGGER.correlateWith(routingContext).error("Exception in filter evaluation", e);
                    throw e;
                }
            }
            LOGGER.correlateWith(routingContext).debug(
                    "Filter expression was applied on list of entities and led to a result list of entities with size: {}",
                    filteredEntities.size());
        }

        return filteredEntities;
    }

    private void applyOrderByQueryOption(OrderByOption orderByOption, List<Entity> resultEntityList)
            throws ODataApplicationException {
        if (orderByOption != null) {
            LOGGER.correlateWith(routingContext).debug("orderByOption name: {}, orderByOption text: {}",
                    orderByOption.getName(), orderByOption.getText());
            try {
                OrderExpressionExecutor.executeOrderOption(routingContext, orderByOption, resultEntityList);
            } catch (Exception e) {
                String message = "Error during processing of orderBy option";
                LOGGER.correlateWith(routingContext).error(message);
                throw new ODataApplicationException(message, HttpStatusCode.BAD_REQUEST.getStatusCode(), Locale.ENGLISH,
                        e);
            }
        }
    }

    private List<Entity> applySkipQueryOption(SkipOption skipOption, List<Entity> resultEntityList)
            throws ODataApplicationException {
        List<Entity> skipList = resultEntityList;
        if (skipOption != null) {
            LOGGER.correlateWith(routingContext).debug("skipOption name: {}, skipOption text: {}, skipOption value: {}",
                    skipOption.getName(), skipOption.getText(), skipOption.getValue());

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
                throw new ODataApplicationException(message, HttpStatusCode.BAD_REQUEST.getStatusCode(),
                        Locale.ENGLISH);
            }
        }
        return skipList;
    }

    private List<Entity> applyTopQueryOption(TopOption topOption, List<Entity> resultEntityList)
            throws ODataApplicationException {
        List<Entity> topList = resultEntityList;
        if (topOption != null) {
            LOGGER.correlateWith(routingContext).debug("topOption name: {}, topOption text: {}, topOption value: {}",
                    topOption.getName(), topOption.getText(), topOption.getValue());
            int topValue = topOption.getValue();
            if (topValue >= 0) {
                if (topValue <= resultEntityList.size()) {
                    topList = resultEntityList.subList(0, topValue);
                } // else return all available entities
            } else {
                throw new ODataApplicationException("Invalid value for $top",
                        HttpStatusCode.BAD_REQUEST.getStatusCode(), Locale.ENGLISH);
            }
        }
        return topList;
    }

    private EntityCollectionSerializerOptions createSerializerOptions(ODataRequest request, UriInfo uriInfo,
            EdmEntitySet edmEntitySet) throws SerializerException {
        String collectionId = request.getRawRequestUri().replaceFirst(request.getRawBaseUri(), EMPTY);
        String selectList = odata.createUriHelper().buildContextURLSelectList(edmEntitySet.getEntityType(), null,
                uriInfo.getSelectOption());
        ContextURL contextUrl = ContextURL.with().entitySet(edmEntitySet).selectList(selectList).build();

        return EntityCollectionSerializerOptions.with().id(collectionId).contextURL(contextUrl)
                .select(uriInfo.getSelectOption()).build();
    }

    @Override
    public void countEntityCollection(ODataRequest request, ODataResponse response, UriInfo uriInfo) {
        Promise<Void> processPromise = getProcessPromise();

        // Retrieve the requested EntitySet from the uriInfo
        EdmEntitySet edmEntitySet = ((UriResourceEntitySet) uriInfo.getUriResourceParts().get(0)).getEntitySet();
        EdmEntityType edmEntityType = edmEntitySet.getEntityType();

        // Fetch the data from backend
        fetchEntities(request, edmEntityType, ew -> {
            try {
                /*
                 * The response body MUST contain the exact count of items matching the request after applying any
                 * $filter or $search system query options, formatted as a simple primitive integer value with media
                 * type text/plain. The returned count MUST NOT be affected by $top, $skip, $orderby, or $expand.
                 * Content negotiation using the Accept request header or the $format system query option is not allowed
                 * with the path segment /$count.
                 */
                List<Entity> resultEntityList = applyFilterQueryOption(uriInfo.getFilterOption(), ew.getEntities());

                ByteArrayInputStream serializerContent = new ByteArrayInputStream(
                        String.valueOf(resultEntityList.size()).getBytes(StandardCharsets.UTF_8));
                response.setContent(serializerContent);
                response.setHeader(HttpHeader.CONTENT_TYPE, ContentType.TEXT_PLAIN.toContentTypeString());
                response.setStatusCode(HttpStatusCode.OK.getStatusCode());
                processPromise.complete();
            } catch (ODataException e) {
                processPromise.fail(e);
            }
        });
    }

    private DataQuery odataRequestToQuery(ODataRequest request) {
        // the uriPath without /odata root path and without query path
        String uriPath =
                request.getRawRequestUri().replaceFirst(request.getRawBaseUri(), EMPTY).replaceFirst("\\?.*$", EMPTY);
        // the raw query path
        String rawQueryPath = request.getRawQueryPath();
        return new DataQuery(uriPath, rawQueryPath, request.getAllHeaders());
    }
}
