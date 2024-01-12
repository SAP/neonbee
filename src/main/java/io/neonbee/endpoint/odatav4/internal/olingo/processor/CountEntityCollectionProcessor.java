package io.neonbee.endpoint.odatav4.internal.olingo.processor;

import static io.neonbee.data.DataAction.READ;
import static io.neonbee.endpoint.odatav4.internal.olingo.processor.EntityProcessor.findEntityByKeyPredicates;
import static io.neonbee.endpoint.odatav4.internal.olingo.processor.NavigationPropertyHelper.chooseEntitySet;
import static io.neonbee.endpoint.odatav4.internal.olingo.processor.NavigationPropertyHelper.fetchNavigationTargetEntities;
import static io.neonbee.endpoint.odatav4.internal.olingo.processor.ProcessorHelper.ODATA_COUNT_SIZE_KEY;
import static io.neonbee.endpoint.odatav4.internal.olingo.processor.ProcessorHelper.ODATA_EXPAND_KEY;
import static io.neonbee.endpoint.odatav4.internal.olingo.processor.ProcessorHelper.ODATA_FILTER_KEY;
import static io.neonbee.endpoint.odatav4.internal.olingo.processor.ProcessorHelper.ODATA_ORDER_BY_KEY;
import static io.neonbee.endpoint.odatav4.internal.olingo.processor.ProcessorHelper.ODATA_SKIP_KEY;
import static io.neonbee.endpoint.odatav4.internal.olingo.processor.ProcessorHelper.ODATA_TOP_KEY;
import static io.neonbee.endpoint.odatav4.internal.olingo.processor.ProcessorHelper.RESPONSE_HEADER_PREFIX;
import static io.neonbee.endpoint.odatav4.internal.olingo.processor.ProcessorHelper.forwardRequest;
import static io.neonbee.internal.helper.StringHelper.EMPTY;
import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static java.lang.Boolean.TRUE;
import static java.util.Optional.ofNullable;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.olingo.commons.api.data.ContextURL;
import org.apache.olingo.commons.api.data.ContextURL.Suffix;
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
import org.apache.olingo.server.api.uri.UriResource;
import org.apache.olingo.server.api.uri.UriResourceEntitySet;
import org.apache.olingo.server.api.uri.queryoption.CountOption;
import org.apache.olingo.server.api.uri.queryoption.FilterOption;
import org.apache.olingo.server.api.uri.queryoption.OrderByOption;
import org.apache.olingo.server.api.uri.queryoption.SkipOption;
import org.apache.olingo.server.api.uri.queryoption.TopOption;
import org.apache.olingo.server.api.uri.queryoption.expression.ExpressionVisitException;

import io.neonbee.endpoint.odatav4.internal.olingo.expression.FilterExpressionVisitor;
import io.neonbee.endpoint.odatav4.internal.olingo.expression.OrderExpressionExecutor;
import io.neonbee.entity.EntityWrapper;
import io.neonbee.logging.LoggingFacade;
import io.vertx.core.Future;
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
        List<UriResource> resourceParts = uriInfo.getUriResourceParts();
        if (resourceParts.size() > 2) {
            throw new UnsupportedOperationException(
                    "Read requests with more than two resource parts are not supported.");
        }
        Promise<Void> processPromise = getProcessPromise();

        // Retrieve the requested EntitySet from the uriInfo
        UriResourceEntitySet uriResourceEntitySet = (UriResourceEntitySet) uriInfo.getUriResourceParts().get(0);
        EdmEntityType edmEntityType = uriResourceEntitySet.getEntitySet().getEntityType();

        EntityCollection entityCollection = new EntityCollection();

        // Fetch the data from backend
        forwardRequest(request, READ, uriInfo, vertx, routingContext, processPromise)
                .compose(ew -> processEntityResult(ew, uriInfo, resourceParts, uriResourceEntitySet, entityCollection))
                .onSuccess(finalResultEntities -> {
                    entityCollection.getEntities().addAll(finalResultEntities);
                    EntityCollectionSerializerOptions opts;
                    try {
                        EdmEntitySet edmEntitySet =
                                chooseEntitySet(resourceParts, uriResourceEntitySet.getEntitySet(), routingContext);
                        opts = createSerializerOptions(request, uriInfo, edmEntitySet);
                        response.setContent(odata.createSerializer(responseFormat)
                                .entityCollection(serviceMetadata, edmEntityType, entityCollection, opts).getContent());
                        response.setStatusCode(HttpStatusCode.OK.getStatusCode());
                        response.setHeader(HttpHeader.CONTENT_TYPE, responseFormat.toContentTypeString());
                        processPromise.complete();
                    } catch (ODataException e) {
                        processPromise.fail(e);
                    }
                }).onFailure(processPromise::fail);
    }

    private Future<List<Entity>> processEntityResult(EntityWrapper ew, UriInfo uriInfo, List<UriResource> resourceParts,
            UriResourceEntitySet uriResourceEntitySet, EntityCollection entityCollection) {
        try {
            if (resourceParts.size() == 1) {
                List<Entity> resultEntityList;
                boolean filterExecuted = TRUE.equals(routingContext.get(RESPONSE_HEADER_PREFIX + ODATA_FILTER_KEY));
                if (filterExecuted) {
                    resultEntityList = ew.getEntities();
                } else {
                    resultEntityList = applyFilterQueryOption(uriInfo.getFilterOption(), ew.getEntities());
                }

                applyCountOption(uriInfo.getCountOption(), resultEntityList, entityCollection, routingContext);

                if (resultEntityList.isEmpty()) {
                    return succeededFuture(resultEntityList);
                }

                boolean orderByExecuted = TRUE.equals(routingContext.get(RESPONSE_HEADER_PREFIX + ODATA_ORDER_BY_KEY));
                if (!orderByExecuted) {
                    applyOrderByQueryOption(uriInfo.getOrderByOption(), resultEntityList);
                }

                boolean skipExecuted = TRUE.equals(routingContext.get(RESPONSE_HEADER_PREFIX + ODATA_SKIP_KEY));
                if (!skipExecuted) {
                    resultEntityList = applySkipQueryOption(uriInfo.getSkipOption(), resultEntityList);
                }

                boolean topExecuted = TRUE.equals(routingContext.get(RESPONSE_HEADER_PREFIX + ODATA_TOP_KEY));
                if (!topExecuted) {
                    resultEntityList = applyTopQueryOption(uriInfo.getTopOption(), resultEntityList);
                }

                boolean expandExecuted = TRUE.equals(routingContext.get(RESPONSE_HEADER_PREFIX + ODATA_EXPAND_KEY));
                if (expandExecuted) {
                    return succeededFuture(resultEntityList);
                } else {
                    return applyExpandQueryOptions(uriInfo, resultEntityList);
                }
            } else {
                Entity foundEntity;

                boolean keyPredicateExecuted = TRUE.equals(routingContext.get(ProcessorHelper.ODATA_KEY_PREDICATE_KEY));
                if (keyPredicateExecuted) {
                    foundEntity = ew.getEntities().get(0);
                } else {
                    foundEntity = findEntityByKeyPredicates(routingContext, uriResourceEntitySet, ew.getEntities());
                }

                boolean expandExecuted = TRUE.equals(routingContext.get(RESPONSE_HEADER_PREFIX + ODATA_EXPAND_KEY));
                if (expandExecuted) {
                    return succeededFuture(List.of(foundEntity));
                } else {
                    return fetchNavigationTargetEntities(resourceParts.get(1), foundEntity, vertx, routingContext);
                }
            }
        } catch (ODataException e) {
            return failedFuture(e);
        }
    }

    private void applyCountOption(CountOption countOption, List<Entity> filteredEntities,
            EntityCollection entityCollection, RoutingContext routingContext) {
        // Apply $count system query option. The $count system query option with a value of true
        // specifies that the total count of items within a collection matching the request be returned
        // along with the result. The $count system query option ignores any $top, $skip, or $expand query
        // options, and returns the total count of results across all pages including only those results
        // matching any specified $filter and $search.
        if ((countOption != null) && countOption.getValue()) {
            int countSize = ofNullable(routingContext.<Integer>get(RESPONSE_HEADER_PREFIX + ODATA_COUNT_SIZE_KEY))
                    .orElse(filteredEntities.size());
            entityCollection.setCount(countSize);
        }
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
                    if (TRUE.equals(filterOption.getExpression().accept(filterExpressionVisitor).getValue())) {
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

    private Future<List<Entity>> applyExpandQueryOptions(UriInfo uriInfo, List<Entity> resultEntityList) {
        return EntityExpander.create(vertx, uriInfo.getExpandOption(), routingContext).map(expander -> {
            for (Entity requestedEntity : resultEntityList) {
                expander.expand(requestedEntity);
            }
            return resultEntityList;
        });
    }

    private EntityCollectionSerializerOptions createSerializerOptions(ODataRequest request, UriInfo uriInfo,
            EdmEntitySet edmEntitySet) throws SerializerException {
        String collectionId = request.getRawRequestUri().replaceFirst(request.getRawBaseUri(), EMPTY);
        String selectList = odata.createUriHelper().buildContextURLSelectList(edmEntitySet.getEntityType(),
                uriInfo.getExpandOption(), uriInfo.getSelectOption());
        ContextURL contextUrl =
                ContextURL.with().entitySet(edmEntitySet).selectList(selectList).suffix(Suffix.ENTITY).build();

        return EntityCollectionSerializerOptions.with().id(collectionId).contextURL(contextUrl)
                .select(uriInfo.getSelectOption()).expand(uriInfo.getExpandOption()).count(uriInfo.getCountOption())
                .build();
    }

    @Override
    public void countEntityCollection(ODataRequest request, ODataResponse response, UriInfo uriInfo) {
        Promise<Void> processPromise = getProcessPromise();

        // Fetch the data from backend
        forwardRequest(request, READ, uriInfo, vertx, routingContext, processPromise).onSuccess(ew -> {
            try {
                /*
                 * The response body MUST contain the exact count of items matching the request after applying any
                 * $filter or $search system query options, formatted as a simple primitive integer value with media
                 * type text/plain. The returned count MUST NOT be affected by $top, $skip, $orderby, or $expand.
                 * Content negotiation using the Accept request header or the $format system query option is not allowed
                 * with the path segment /$count.
                 */
                Integer countSize =
                        routingContext.<Integer>get(RESPONSE_HEADER_PREFIX + ODATA_COUNT_SIZE_KEY);
                if (countSize == null) {
                    countSize = applyFilterQueryOption(uriInfo.getFilterOption(), ew.getEntities()).size();
                }

                ByteArrayInputStream serializerContent = new ByteArrayInputStream(
                        String.valueOf(countSize).getBytes(StandardCharsets.UTF_8));
                response.setContent(serializerContent);
                response.setHeader(HttpHeader.CONTENT_TYPE, ContentType.TEXT_PLAIN.toContentTypeString());
                response.setStatusCode(HttpStatusCode.OK.getStatusCode());
                processPromise.complete();
            } catch (ODataException | ClassCastException e) {
                processPromise.fail(e);
            }
        });
    }
}
