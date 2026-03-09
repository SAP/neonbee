package io.neonbee.endpoint.odatav4;

import static io.neonbee.endpoint.odatav4.ODataV4Endpoint.UriConversion.STRICT;

import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.olingo.server.api.ServiceMetadata;

import com.sap.cds.reflect.CdsService;

import io.neonbee.config.EndpointConfig;
import io.neonbee.entity.EntityModel;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

public class ODataProxyEndpoint extends ODataV4Endpoint {

    /**
     * The key for the optional raw batch processing map in the endpoint config. Map key: EntityTypeName (e.g.
     * example/Birds), value: DataVerticle qualified name (e.g. example/_BirdsVerticle).
     */
    public static final String CONFIG_RAW_BATCH_PROCESSING = "rawBatchProcessing";

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
     * @param edmxModel     The EDMX model to be used by the handler.
     * @param uriConversion The URI conversion strategy.
     * @return The request handler.
     */
    @Override
    protected Handler<RoutingContext> getRequestHandler(ServiceMetadata edmxModel, UriConversion uriConversion) {
        return getRequestHandler(edmxModel, uriConversion, null);
    }

    /**
     * Creates a new OData Proxy Endpoint with optional endpoint config (e.g. rawBatchProcessing).
     *
     * @param edmxModel     The EDMX model to be used by the handler.
     * @param uriConversion The URI conversion strategy.
     * @param config        The endpoint configuration (may be null).
     * @return The request handler.
     */
    @Override
    protected Handler<RoutingContext> getRequestHandler(ServiceMetadata edmxModel, UriConversion uriConversion,
            JsonObject config) {
        Map<String, String> rawBatchProcessing = parseRawBatchProcessing(config);
        return new ODataProxyEndpointHandler(edmxModel, uriConversion, rawBatchProcessing);
    }

    private static Map<String, String> parseRawBatchProcessing(JsonObject config) {
        if (config == null) {
            return Collections.emptyMap();
        }
        JsonObject map = config.getJsonObject(CONFIG_RAW_BATCH_PROCESSING);
        if (map == null) {
            return Collections.emptyMap();
        }
        return map.stream().collect(Collectors.toMap(Map.Entry::getKey, e -> (String) e.getValue()));
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
