package io.neonbee.endpoint.odatav4;

import static io.neonbee.endpoint.odatav4.ODataV4Endpoint.UriConversion.STRICT;

import org.apache.olingo.server.api.ServiceMetadata;

import com.sap.cds.reflect.CdsService;

import io.neonbee.config.EndpointConfig;
import io.neonbee.entity.EntityModel;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

public class ODataProxyEndpoint extends ODataV4Endpoint {

    private static final String BASE_PATH_SEGMENT = "odataproxy";

    /**
     * The default path the OData V4 endpoint is exposed by NeonBee.
     */
    public static final String DEFAULT_BASE_PATH = "/" + BASE_PATH_SEGMENT + "/";

    @Override
    public EndpointConfig getDefaultConfig() {
        // as the EndpointConfig stays mutable, do not extract this to a static variable, but return a new object
        return new EndpointConfig()
                .setType(ODataProxyEndpoint.class.getName())
                .setBasePath(DEFAULT_BASE_PATH)
                .setAdditionalConfig(new JsonObject().put("uriConversion", STRICT.name()));
    }

    /**
     * Creates a new OData Proxy Endpoint.
     *
     * @param edmxModel The EDMX model to be used by the handler.
     * @return The request handler.
     */
    @Override
    protected Handler<RoutingContext> getRequestHandler(ServiceMetadata edmxModel) {
        return new ODataProxyEndpointHandler(edmxModel);
    }

    /**
     * Filters the models to be included in the OData Proxy Endpoint. Includes only those models that have the
     * "neonbee.endpoint" annotation with value "odataproxy".
     *
     * @param model The entity model to be checked.
     * @return true if the model should be included, false otherwise.
     */
    @Override
    protected boolean filterModels(EntityModel model) {
        return model.getCsnModel().services()
                .flatMap(CdsService::annotations)
                .filter(annotation -> NEONBEE_ENDPOINT_CDS_SERVICE_ANNOTATION.equals(annotation.getName()))
                .anyMatch(annotation -> BASE_PATH_SEGMENT.equalsIgnoreCase(annotation.getValue().toString()));
    }
}
