package io.neonbee.endpoint.odatav4;

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.base.Strings.nullToEmpty;
import static io.neonbee.endpoint.odatav4.ODataV4Endpoint.UriConversion.STRICT;
import static io.neonbee.entity.EntityModelManager.EVENT_BUS_MODELS_LOADED_ADDRESS;
import static io.neonbee.entity.EntityModelManager.getSharedModels;
import static io.neonbee.internal.helper.FunctionalHelper.entryConsumer;
import static io.neonbee.internal.helper.FunctionalHelper.entryFunction;
import static io.neonbee.internal.helper.StringHelper.EMPTY;
import static io.vertx.core.Future.succeededFuture;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

import io.neonbee.config.EndpointConfig;
import io.neonbee.endpoint.Endpoint;
import io.neonbee.endpoint.odatav4.internal.olingo.OlingoEndpointHandler;
import io.neonbee.entity.EntityModel;
import io.neonbee.internal.SharedDataAccessor;
import io.neonbee.logging.LoggingFacade;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;

public class ODataV4Endpoint implements Endpoint {
    /**
     * The key to configure the URI conversion.
     */
    public static final String CONFIG_URI_CONVERSION = "uriConversion";

    /**
     * The default path the OData V4 endpoint is exposed by NeonBee.
     */
    public static final String DEFAULT_BASE_PATH = "/odata/";

    private static final LoggingFacade LOGGER = LoggingFacade.create();

    /**
     * Either STRICT (&lt;namespace&gt;.&lt;service&gt;), LOOSE (&lt;path mapping of namespace&gt;-&lt;path mapping of
     * service&gt;) or CDS (&lt;path mapping of service&gt;) URI mapping:
     * <p>
     * - STRICT: is a one-to-one case-sensitive mapping of the namespace and service name to the URI of the OData
     * endpoint. For example my.very.CatalogService is mapped to the URI like "/odata/my.very.CatalogService/EntitySet"
     * where there are some simple rules to follow: /odata/ is the base path of the endpoint (to be customized in the
     * ServerVerticle configuration), everything from there to the last slash of the URI path is the service namespace
     * and name (so my.very.CatalogService), which itself the last occurring dot (.) separates the service namespace
     * (my.very) from the service name (CatalogService), everything after the last slash of the URI path is considered
     * to be the entity set name.
     * <p>
     * - CDS: is a condensed, human-readable mapping of only the service name to the URI of the OData endpoint, with
     * some limitations, due to the nature of being a non-uniform function based on the following CDS rule set:
     * <ol>
     * <li>remove the service namespace (my.very.CatalogService -&gt; CatalogService)
     * <li>delete "Service" if it is being the last word of the string (case-sensitive, CatalogService -&gt; Catalog)
     * <li>lower case the first character of the service name (Catalog -&gt; catalog)
     * <li>prefix a "-" to all upper case letters, followed by a lower case and lowercase the first (fooBar -&gt;
     * foo-bar)
     * <li>lowercase the rest of the string (FOO -&gt; foo)
     * <li>replace all occurrences of "_" with "-" (foo_bar -&gt; foo-bar)
     * </ol>
     * Due to the namespace being removed from the URI, no one-to-one mapping from URI to OData service is possible when
     * choosing CDS mapping. To still be able to address all OData services uniquely, the following condition must be
     * met: All services in different namespaces must not share the same mapped URI representation. So for example
     * my.very.CatalogService maps to /odata/catalog/EntitySet in the URI. So does my.other.CatalogService or also
     * my.third.Catalog. In this the entities accessible are non-deterministic and could be either services of any of
     * the provided namespaces (my.very, my.other or my.third). So this must be prevented by choosing different service
     * names for each of the three services, e.g. my.very.CatalogAService (maps to /odata/catalog-a/),
     * my.other.CatalogBService (maps to /odata/catalog-b/) and my.third.Catalog_C (maps to /odata/catalog-c/).
     * <p>
     * For the CDS mapping having the service name "Service", can be considered as a special case. In case a service is
     * named solely "Service", again regardless of its service namespace, it is mapped to an empty path segment, so to
     * the OData root URI /odata/. This behavior can be utilized, e.g. in case only one OData service should be exposed
     * on the OData root and no separate service name mappings are needed.
     * <p>
     * - LOOSE: is a condensed, human-readable mapping of the namespace and service name to the URI of the OData
     * endpoint, with some limitations, due to the nature of being a non-uniform transformation function. The function
     * will first capitalize the namespace and prepend it to the service name (so my.very.CatalogService maps to
     * MyVeryCatalogService). Afterwards the CDS transformation is applied and the URI is mapped to my-very-catalog in
     * the example. As in contrast to the CDS mapping, the LOOSE URI mapping will still take the namespace into
     * consideration, most services can be uniquely mapped and addressed via the endpoint. However, similar to the CDS
     * limitation, not any loaded model must share the same URI representation, in order to still be accessible. For
     * example my.very.CatalogService and my.very.catalog.Service do share the same URI representation, which would
     * cause one of the services to be inaccessible!
     */
    @SuppressWarnings("checkstyle:JavadocVariable")
    public enum UriConversion implements UnaryOperator<String> {
        STRICT(uriPart -> uriPart), CDS(uriPart -> {
            uriPart = uriPart.substring(uriPart.lastIndexOf('.') + 1); // my.very.CatalogService -> CatalogService
            uriPart = uriPart.replaceFirst("Service$", EMPTY); // CatalogService -> Catalog
            uriPart = Pattern.compile("^[A-Z]+").matcher(uriPart) // XMLService -> xmlService
                    .replaceFirst(match -> match.group().toLowerCase(Locale.ROOT));
            uriPart = Pattern.compile("[A-Z]+[a-z0-9]").matcher(uriPart) // FooBarX9 --> foo-bar-x9
                    .replaceAll(match -> '-' + match.group().toLowerCase(Locale.ROOT));
            uriPart = uriPart.toLowerCase(Locale.ROOT); // FOO -> foo
            uriPart = uriPart.replace('_', '-'); // foo_bar -> foo-bar

            return uriPart;
        }),

        LOOSE(uriPart -> {
            // join the namespace and service name, my.very.CatalogService -> my-very-catalog
            uriPart = CDS.apply(uriPart.substring(0, uriPart.lastIndexOf('.') + 1).replace('.', '-'))
                    + CDS.apply(uriPart);
            uriPart = uriPart.replaceFirst("-$", EMPTY); // service name was "Service", thus namespace ends with a -

            return uriPart;
        });

        @SuppressWarnings("ImmutableEnumChecker") // Because we can't make UnaryOperator immutable
        final UnaryOperator<String> convert;

        UriConversion(UnaryOperator<String> convert) {
            this.convert = convert;
        }

        @Override
        public String apply(String uriPart) {
            if (isNullOrEmpty(uriPart)) {
                return EMPTY;
            }

            return convert.apply(uriPart);
        }

        /**
         * Parses a given string and returns the related UriConversion.
         *
         * @param name UriConversion represented as string
         * @return the UriConversion, or STRICT in case String doesn't match a UriConversion.
         */
        public static UriConversion byName(String name) {
            switch (nullToEmpty(name).toLowerCase(Locale.ROOT)) {
            case "strict":
                return STRICT;
            case "loose":
                return LOOSE;
            case "cds":
                return CDS;
            default:
                LOGGER.warn("Unknown URI conversion {} falling back to \"strict\" mapping", name);
                return STRICT;
            }
        }
    }

    @Override
    public EndpointConfig getDefaultConfig() {
        // as the EndpointConfig stays mutable, do not extract this to a static variable, but return a new object
        return new EndpointConfig().setType(ODataV4Endpoint.class.getName()).setBasePath(DEFAULT_BASE_PATH);
    }

    @Override
    public Router createEndpointRouter(Vertx vertx, String basePath, JsonObject config) {
        Router router = Router.router(vertx);
        AtomicBoolean initialized = new AtomicBoolean(); // true if the router was initialized already
        AtomicReference<Map<String, EntityModel>> models = new AtomicReference<>();

        // Register the event bus consumer first, otherwise it could happen that during initialization we are missing an
        // update to the data model, a refresh of the router will only be triggered in case it is already initialized.
        // This is a NON-local consumer, this means the reload could be triggered from anywhere, however currently the
        // reload is only triggered in case the EntityModelManager reloads the models locally (and triggers a local
        // publish of the message, thus only triggering the routers to be reloaded on the local instance).
        UriConversion uriConversion = UriConversion.byName(config.getString(CONFIG_URI_CONVERSION, STRICT.name()));
        vertx.eventBus().consumer(EVENT_BUS_MODELS_LOADED_ADDRESS, message -> {
            // do not refresh the router if it wasn't even initialized
            if (initialized.get()) {
                refreshRouter(vertx, router, basePath, uriConversion, models);
            }
        });

        // This router is initialized lazy, this means that all required routes will be populated on first request to
        // said router, not before. This comes with three major advantages: a) it makes the initialization code more
        // easy, as in case we'd first register to the event handler, we'd always have to check if the model is loaded
        // already and potentially have to deal with a race condition b) no model is loaded / routes are initialized if
        // when NeonBee is started and / or in case the endpoint is not used.
        Route initialRoute = router.route();
        initialRoute.handler(
                routingContext -> new SharedDataAccessor(vertx, ODataV4Endpoint.class).getLocalLock(asyncLock ->
                // immediately initialize the router, this will also "arm" the event bus listener
                (!initialized.getAndSet(true) ? refreshRouter(vertx, router, basePath, uriConversion, models)
                        : succeededFuture()).onComplete(handler -> {
                            // Wait for the refresh to finish (the result doesn't matter), remove the initial route, as
                            // this will redirect all requests to the registered service endpoint handlers (if non have
                            // been registered, e.g. due to a failure in model loading, it'll result in an 404). Could
                            // have been removed already by refreshRouter, we don't care!
                            initialRoute.remove();
                            if (asyncLock.succeeded()) {
                                // releasing the lock will cause other requests unblock and not call the initial route
                                asyncLock.result().release();
                            }

                            // Let the router again handle the context again, now with either all service endpoints
                            // registered, or none in case there have been a failure while loading the models.
                            // NOTE: Re-route is the only elegant way I found to restart the current router to take
                            // the new routes! Might consider checking again with the Vert.x 4.0 release.
                            routingContext.reroute(routingContext.request().uri());
                        })));

        return router;
    }

    private static Future<Void> refreshRouter(Vertx vertx, Router router, String basePath, UriConversion uriConversion,
            AtomicReference<Map<String, EntityModel>> currentModels) {
        return getSharedModels(vertx).compose(models -> {
            if (models == currentModels.get()) {
                return succeededFuture(); // no update needed
            } else {
                currentModels.set(models);
            }

            // before adding new routes, get a list of existing routes, to remove after the new routes have been added
            List<Route> existingRoutes = router.getRoutes();

            // Register new routes first, this will avoid downtimes of already existing services. Register the shortest
            // routes last, this will lead to some routes like the empty namespace / to be registered last.
            models.values().stream().flatMap(entityModel -> entityModel.getEdmxes().entrySet().stream())
                    .map(entryFunction(
                            (schemaNamespace, edmxModel) -> Map.entry(uriConversion.apply(schemaNamespace), edmxModel)))
                    .sorted(Map.Entry.comparingByKey(Comparator.comparingInt(String::length).reversed()))
                    .forEach(entryConsumer((uriPath, edmxModel) -> {
                        // TODO depending on the config either create Olingo or CDS based OData V4 handlers here
                        router.route((uriPath.isEmpty() ? EMPTY : ("/" + uriPath)) + "/*")
                                .handler(new OlingoEndpointHandler(edmxModel));
                        LOGGER.info("Serving OData service endpoint for {} at {}{} ({} URI mapping)",
                                edmxModel.getEdm().getEntityContainer().getNamespace(), basePath, uriPath,
                                uriConversion.name().toLowerCase(Locale.getDefault()));
                    }));

            // remove any of the old routes, so the old models will stop serving
            existingRoutes.forEach(Route::remove);

            LOGGER.info("Refreshed OData endpoint router, populated {} models, removed {} existing routes",
                    models.size(), existingRoutes.size());
            return succeededFuture();
        });
    }
}
