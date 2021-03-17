package io.neonbee.internal.processor;

import static io.neonbee.data.DataAction.READ;
import static io.neonbee.entity.EntityVerticle.requestEntity;
import static io.neonbee.internal.helper.StringHelper.EMPTY;

import java.io.InputStream;
import java.util.List;
import java.util.Locale;

import org.apache.olingo.commons.api.data.ContextURL;
import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.data.Property;
import org.apache.olingo.commons.api.edm.EdmEntitySet;
import org.apache.olingo.commons.api.edm.EdmEntityType;
import org.apache.olingo.commons.api.edm.EdmPrimitiveType;
import org.apache.olingo.commons.api.edm.EdmProperty;
import org.apache.olingo.commons.api.format.ContentType;
import org.apache.olingo.commons.api.http.HttpHeader;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.server.api.OData;
import org.apache.olingo.server.api.ODataApplicationException;
import org.apache.olingo.server.api.ODataLibraryException;
import org.apache.olingo.server.api.ODataRequest;
import org.apache.olingo.server.api.ODataResponse;
import org.apache.olingo.server.api.ServiceMetadata;
import org.apache.olingo.server.api.serializer.PrimitiveSerializerOptions;
import org.apache.olingo.server.api.uri.UriInfo;
import org.apache.olingo.server.api.uri.UriResource;
import org.apache.olingo.server.api.uri.UriResourceEntitySet;
import org.apache.olingo.server.api.uri.UriResourceProperty;

import io.neonbee.data.DataAction;
import io.neonbee.data.DataQuery;
import io.neonbee.data.DataRequest;
import io.neonbee.data.internal.DataContextImpl;
import io.neonbee.entity.EntityWrapper;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.ext.web.RoutingContext;

@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "NM_SAME_SIMPLE_NAME_AS_INTERFACE",
        justification = "Common practice in Olingo to name the implementation of the processor same as the interface")
public class PrimitiveProcessor extends AsynchronousProcessor
        implements org.apache.olingo.server.api.processor.PrimitiveProcessor {
    private OData odata;

    private ServiceMetadata serviceMetadata;

    /**
     * Creates a new PrimitiveProcessor.
     *
     * @param vertx          the related Vert.x instance
     * @param routingContext the routingContext of the related request
     * @param processPromise the promise to complete when data has been fetched
     */
    public PrimitiveProcessor(Vertx vertx, RoutingContext routingContext, Promise<Void> processPromise) {
        super(vertx, routingContext, processPromise);
    }

    @Override
    public void init(OData odata, ServiceMetadata serviceMetadata) {
        this.odata = odata;
        this.serviceMetadata = serviceMetadata;
    }

    @Override
    public void readPrimitive(ODataRequest request, ODataResponse response, UriInfo uriInfo, ContentType responseFormat)
            throws ODataApplicationException, ODataLibraryException {
        /*
         * See https://docs.oasis-open.org/odata/odata/v4.01/odata-v4.01-part2-url-conventions.html#sec_URLComponents
         * for details about how the OData URL broken down into its component parts.
         */
        List<UriResource> resourceParts = uriInfo.getUriResourceParts();
        UriResourceEntitySet uriEntityset = (UriResourceEntitySet) resourceParts.get(0);
        EdmEntitySet edmEntitySet = uriEntityset.getEntitySet();
        EdmEntityType edmEntityType = edmEntitySet.getEntityType();

        handleEntity(edmEntityType, request, READ, handleResult(uriEntityset, uriInfo, response, responseFormat));
    }

    @Override
    public void updatePrimitive(ODataRequest request, ODataResponse response, UriInfo uriInfo,
            ContentType requestFormat, ContentType responseFormat)
            throws ODataApplicationException, ODataLibraryException {
        throw new UnsupportedOperationException("updatePrimitive not implemented");
    }

    @Override
    public void deletePrimitive(ODataRequest request, ODataResponse response, UriInfo uriInfo)
            throws ODataApplicationException, ODataLibraryException {
        throw new UnsupportedOperationException("deletePrimitive not implemented");
    }

    private void handleEntity(EdmEntityType edmEntityType, ODataRequest request, DataAction action,
            Handler<AsyncResult<EntityWrapper>> entityHandler) {
        handleEntity(edmEntityType, request, action, (Entity) null, entityHandler);
    }

    void handleEntity(EdmEntityType edmEntityType, ODataRequest request, DataAction action, Entity entity,
            Handler<AsyncResult<EntityWrapper>> entityHandler) {
        String uriPath = request.getRawRequestUri().replaceFirst(request.getRawBaseUri(), EMPTY);
        requestEntity(vertx,
                new DataRequest(edmEntityType.getFullQualifiedName(),
                        new DataQuery(action, uriPath, request.getRawQueryPath(), request.getAllHeaders(),
                                new EntityWrapper(edmEntityType.getFullQualifiedName(), entity).toBuffer(vertx))
                                        .addHeader("X-HTTP-Method", request.getMethod().name())),
                new DataContextImpl(routingContext)).onComplete(entityHandler);
    }

    private Handler<AsyncResult<EntityWrapper>> handleResult(UriResourceEntitySet uriResourceEntitySet, UriInfo uriInfo,
            ODataResponse response, ContentType responseFormat) {
        Promise<Void> processPromise = getProcessPromise();
        return asyncEntity -> {
            if (asyncEntity.failed()) {
                processPromise.fail(asyncEntity.cause());
                return;
            }

            try {
                Entity entity = EntityProcessor.findEntityByKeyPredicates(routingContext, uriResourceEntitySet,
                        asyncEntity.result().getEntities());
                if (entity == null) {
                    processPromise.fail(new ODataApplicationException("Entity not found",
                            HttpStatusCode.NOT_FOUND.getStatusCode(), Locale.ENGLISH));
                    return;
                }

                /*
                 * See https://docs.oasis-open.org/odata/odata/v4.01/odata-v4.01-part2-url-conventions.html#
                 * sec_URLComponents for details about how the OData URL broken down into its component parts.
                 */
                List<UriResource> resourceParts = uriInfo.getUriResourceParts();
                // Retrieve the requested Edm property. The last segment is the Property
                UriResourceProperty uriProperty = (UriResourceProperty) resourceParts.get(resourceParts.size() - 1);
                EdmProperty edmProperty = uriProperty.getProperty();
                String edmPropertyName = edmProperty.getName();

                // Retrieve the property data from the entity
                Property property = entity.getProperty(edmPropertyName);
                if (property == null) {
                    processPromise.fail(new ODataApplicationException("Property not found",
                            HttpStatusCode.NOT_FOUND.getStatusCode(), Locale.ENGLISH));
                    return;
                }
                // Serialize the property's value
                Object value = property.getValue();
                if (value != null) {
                    ContextURL contextUrl = ContextURL.with().entitySet(uriResourceEntitySet.getEntitySet())
                            .navOrPropertyPath(edmPropertyName).build();
                    PrimitiveSerializerOptions options =
                            PrimitiveSerializerOptions.with().contextURL(contextUrl).build();
                    EdmPrimitiveType edmPropertyType = (EdmPrimitiveType) edmProperty.getType();
                    InputStream propertyStream = odata.createSerializer(responseFormat)
                            .primitive(serviceMetadata, edmPropertyType, property, options).getContent();

                    response.setContent(propertyStream);
                    response.setStatusCode(HttpStatusCode.OK.getStatusCode());
                    response.setHeader(HttpHeader.CONTENT_TYPE, responseFormat.toContentTypeString());
                } else {
                    // in case there's no value for the property, we can skip the serialization
                    response.setStatusCode(HttpStatusCode.NO_CONTENT.getStatusCode());
                }

                processPromise.complete();
            } catch (Exception e) {
                processPromise.fail(e);
            }
        };
    }
}
