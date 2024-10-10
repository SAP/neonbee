package io.neonbee.endpoint.odatav4;

import static com.google.common.base.Strings.emptyToNull;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.base.Strings.nullToEmpty;
import static io.neonbee.endpoint.odatav4.ODataV4Endpoint.UriConversion.STRICT;
import static io.neonbee.entity.EntityModelManager.EVENT_BUS_MODELS_LOADED_ADDRESS;
import static io.neonbee.internal.helper.FunctionalHelper.entryConsumer;
import static io.neonbee.internal.helper.FunctionalHelper.entryFunction;
import static io.neonbee.internal.helper.StringHelper.EMPTY;
import static io.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN;
import static io.vertx.core.Future.succeededFuture;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

import com.google.common.base.MoreObjects;

import io.neonbee.NeonBee;
import io.neonbee.config.EndpointConfig;
import io.neonbee.endpoint.Endpoint;
import io.neonbee.endpoint.odatav4.internal.olingo.OlingoEndpointHandler;
import io.neonbee.entity.EntityModel;
import io.neonbee.internal.RegexBlockList;
import io.neonbee.internal.SharedDataAccessorFactory;
import io.neonbee.logging.LoggingFacade;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

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

    private static final String NORMALIZED_URI_CONTEXT_KEY = ODataV4Endpoint.class.getName() + "_normalizedUri";

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
    @SuppressWarnings({ "checkstyle:JavadocVariable", "checkstyle:ParameterAssignmentCheck" })
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
        return new EndpointConfig().setType(ODataV4Endpoint.class.getName()).setBasePath(DEFAULT_BASE_PATH)
                .setAdditionalConfig(new JsonObject().put("uriConversion", STRICT.name()));
    }

    @Override
    public Future<Router> createEndpointRouter(Vertx vertx, String basePath, JsonObject config) {
        Router router = Router.router(vertx);
        AtomicBoolean initialized = new AtomicBoolean(); // true if the router was initialized already
        AtomicReference<Map<String, EntityModel>> models = new AtomicReference<>();

        // the URI convention used to expose the given service in the endpoint.
        UriConversion uriConversion = UriConversion.byName(config.getString("uriConversion", STRICT.name()));

        // a block / allow list of all entities that should be exposed via this endpoint. the entity name is always
        // matched against the full qualified name of the entity in question (URI conversion is applied by NeonBee).
        RegexBlockList exposedEntities = RegexBlockList.fromJson(config.getValue("exposedEntities"));

        // Register the event bus consumer first, otherwise it could happen that during initialization we are missing an
        // update to the data model, a refresh of the router will only be triggered in case it is already initialized.
        // This is a NON-local consumer, this means the reload could be triggered from anywhere, however currently the
        // reload is only triggered in case the EntityModelManager reloads the models locally (and triggers a local
        // publish of the message, thus only triggering the routers to be reloaded on the local instance).
        vertx.eventBus().consumer(EVENT_BUS_MODELS_LOADED_ADDRESS, message -> {
            // do not refresh the router if it wasn't even initialized
            if (initialized.get()) {
                refreshRouter(vertx, router, basePath, uriConversion, exposedEntities, models);
            }
        });

        // This router is initialized lazy, this means that all required routes will be populated on first request to
        // said router, not before. This comes with three major advantages: a) it makes the initialization code more
        // easy, as in case we'd first register to the event handler, we'd always have to check if the model is loaded
        // already and potentially have to deal with a race condition b) no model is loaded / routes are initialized if
        // when NeonBee is started and / or in case the endpoint is not used.
        Route initialRoute = router.route();
        initialRoute.handler(
                routingContext -> new SharedDataAccessorFactory(vertx)
                        .getSharedDataAccessor(ODataV4Endpoint.class)
                        .getLocalLock(asyncLock ->
                        // immediately initialize the router, this will also "arm" the event bus listener
                        (!initialized.getAndSet(true)
                                ? refreshRouter(vertx, router, basePath, uriConversion, exposedEntities, models)
                                : succeededFuture()).onComplete(handler -> {
                                    // wait for the refresh to finish (the result doesn't matter), remove the initial
                                    // route, as
                                    // this will redirect all requests to the registered service endpoint handlers (if
                                    // non have
                                    // been registered, e.g. due to a failure in model loading, it'll result in an 404).
                                    // Could
                                    // have been removed already by refreshRouter, we don't care!
                                    initialRoute.remove();
                                    if (asyncLock.succeeded()) {
                                        // releasing the lock will cause other requests unblock and not call the initial
                                        // route
                                        asyncLock.result().release();
                                    }

                                    // let the router again handle the context again, now with either all service
                                    // endpoints
                                    // registered, or none in case there have been a failure while loading the models.
                                    // NOTE: Re-route is the only elegant way I found to restart the current router to
                                    // take
                                    // the new routes! Might consider checking again with the Vert.x 4.0 release.
                                    routingContext.reroute(routingContext.request().uri());
                                })));

        return succeededFuture(router);
    }

    private static Future<Void> refreshRouter(Vertx vertx, Router router, String basePath, UriConversion uriConversion,
            RegexBlockList exposedEntities, AtomicReference<Map<String, EntityModel>> currentModels) {
        return NeonBee.get(vertx).getModelManager().getSharedModels().compose(models -> {
            if (models == currentModels.get()) {
                return succeededFuture(); // no update needed
            } else {
                currentModels.set(models);
            }

            // before adding new routes, get a list of existing routes, to remove after the new routes have been added
            List<Route> existingRoutes = router.getRoutes();

            // register new routes first, this will avoid downtimes of already existing services. Register the shortest
            // routes last, this will lead to some routes like the empty namespace / to be registered last.
            models.values().stream().flatMap(entityModel -> entityModel.getAllEdmxMetadata().entrySet().stream())
                    .map(entryFunction(
                            (schemaNamespace, edmxModel) -> Map.entry(uriConversion.apply(schemaNamespace), edmxModel)))
                    .sorted(Map.Entry.comparingByKey(Comparator.comparingInt(String::length).reversed()))
                    .forEach(entryConsumer((uriPath, edmxModel) -> {
                        String schemaNamespace = edmxModel.getEdm().getEntityContainer().getNamespace();
                        router.route((uriPath.isEmpty() ? EMPTY : ("/" + uriPath)) + "/*")
                                // some entities should not get exposed, register a handler, checking the block list
                                .handler(routingContext -> {
                                    // normalize the URI first
                                    NormalizedUri normalizedUri = normalizeUri(routingContext, schemaNamespace);
                                    if (LOGGER.isDebugEnabled()) {
                                        LOGGER.correlateWith(routingContext).debug("Normalized OData V4 URI {}",
                                                normalizedUri);
                                    }

                                    // if a entity is specified check it against the block list
                                    // TODO: maybe also navigation properties have to be taken into account here?
                                    if (normalizedUri.fullQualifiedName != null
                                            && !exposedEntities.isAllowed(normalizedUri.fullQualifiedName)) {
                                        routingContext.fail(FORBIDDEN.code());
                                        return;
                                    }

                                    routingContext.next();
                                })
                                // TODO depending on the config either create Olingo or CDS based OData V4 handlers here
                                .handler(new OlingoEndpointHandler(edmxModel));
                        if (LOGGER.isInfoEnabled()) {
                            LOGGER.info("Serving OData service endpoint for {} at {}{} ({} URI mapping)",
                                    schemaNamespace, basePath, uriPath,
                                    uriConversion.name().toLowerCase(Locale.getDefault()));
                        }
                    }));

            // remove any of the old routes, so the old models will stop serving
            existingRoutes.forEach(Route::remove);

            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("Refreshed OData endpoint router, populated {} models, removed {} existing routes",
                        models.size(), existingRoutes.size());
            }
            return succeededFuture();
        });
    }

    /**
     * Normalize a given OData V4 request URI using a given {@link RoutingContext} and the schema namespace.
     *
     * A once parsed {@link NormalizedUri} is associated with the given {@link RoutingContext} so the same reference of
     * the URI is returned if this method is invoked multiple times with the same {@link RoutingContext} given.
     *
     * @param routingContext  the routing context to parse the {@link NormalizedUri} from. Depending on the
     *                        {@link UriConversion} the routing path / uri might not contain the right schema namespace
     * @param schemaNamespace the schema namespace of the service in question
     * @return a {@link NormalizedUri}
     */
    public static NormalizedUri normalizeUri(RoutingContext routingContext, String schemaNamespace) {
        return Optional.<NormalizedUri>ofNullable(routingContext.get(NORMALIZED_URI_CONTEXT_KEY)).orElseGet(() -> {
            NormalizedUri normalizedUri = new NormalizedUri(routingContext, schemaNamespace);
            routingContext.put(NORMALIZED_URI_CONTEXT_KEY, normalizedUri);
            return normalizedUri;
        });
    }

    /* @formatter:off *//**
     * This class represents a normalized OData V4 URI and helps to split it up in several path segments.
     * <p>
     * Asserting the most specific URI, given the following example from the OASIS OData URI specification:
     *
     * <pre>
     *   http://host:port/path/SampleService.svc/Categories(1)/Products?&amp;top=2&amp;orderby=Name
     * </pre>
     *
     * Assuming loose URI conversion is used, NeonBee will expose the service at:
     *
     * <pre>
     *   http://host:port/path/sample-service-svc/Categories(1)/Products?&amp;top=2&amp;orderby=Name
     * </pre>
     *
     * We get the following information from the handler configuration:
     *
     * <pre>
     *   schemaNamespace = SampleService.svc
     * </pre>
     *
     * We get the following information from the routing context / request:
     *
     * <pre>
     *   requestUri      = http://host:port/path/sample-service-svc/Categories(1)/Products?&amp;top=2&amp;orderby=Name
     *   requestPath     = /path/sample-service-svc/Categories(1)/Products
     *   requestQuery    = &amp;top=2&amp;orderby=Name
     *   routeMountPoint = /path/
     *   routePath       = /sample-service-svc/
     * </pre>
     *
     * The URI is normalized to the following components, note how the URI always will be normalized as if no
     * URI conversion was performed:
     *
     * <pre>
     *   requestUri        = http://host:port/path/SampleService.svc/Categories(1)/Products?&amp;top=2&amp;orderby=Name
     *   requestPath       = /path/SampleService.svc/Categories(1)/Products
     *   requestQuery      = &amp;top=2&amp;orderby=Name
     *   baseUri           = http://host:port/path/
     *   basePath          = /path/
     *   schemaNamespace   = SampleService.svc
     *   resourcePath      = /Categories(1)/Products
     *   entityName        = Categories
     *   fullQualifiedName = SampleService.svc.Categories
     * </pre>
     *//* @formatter:on */
    public static class NormalizedUri {
        /**
         * The full request URI.
         *
         * <pre>
         * http://host:port/path/SampleService.svc/Categories(1)/Products?&amp;top=2&amp;orderby=Name
         * </pre>
         */
        public final String requestUri;

        /**
         * The requests path.
         *
         * <pre>
         * /path/SampleService.svc/Categories(1)/Products
         * </pre>
         */
        public final String requestPath;

        /**
         * The requests query.
         *
         * <pre>
         * &amp;top=2&amp;orderby=Name
         * </pre>
         */
        public final String requestQuery;

        /**
         * The base URI of the OData endpoint.
         *
         * <pre>
         * http://host:port/path/
         * </pre>
         */
        public final String baseUri;

        /**
         * The base path of the OData endpoint.
         *
         * <pre>
         * /path/
         * </pre>
         */
        public final String basePath;

        /**
         * The schema namespace of the OData model the request was made for.
         *
         * <pre>
         * SampleService.svc
         * </pre>
         */
        public final String schemaNamespace;

        /**
         * The full resource path of the OData request.
         *
         * <pre>
         * /Categories(1)/Products
         * </pre>
         */
        public final String resourcePath;

        /**
         * The entity name of the requested entity (if any or null).
         *
         * <pre>
         * Categories
         * </pre>
         */
        public final String entityName;

        /**
         * The full qualified name of the requested entity (if any or null).
         *
         * <pre>
         * Categories
         * </pre>
         */
        public final String fullQualifiedName;

        private NormalizedUri(RoutingContext routingContext, String schemaNamespace) {
            // the (unconverted) schema namespace is a input to the constructor
            this.schemaNamespace = schemaNamespace;

            Route route = routingContext.currentRoute();
            // note that getPath() returns *only* the path prefix, so essentially the base path and converted schema
            // namespace with leading and tailing slashes w/o the tailing *, which is handled and stripped by the router
            String routeMountPoint = routingContext.mountPoint();
            String routePath = // routePath w/ exactly one tailing slash
                    Optional.ofNullable(route).map(Route::getPath).orElse(EMPTY).replaceAll("/+$", EMPTY) + "/";

            HttpServerRequest request = routingContext.request();
            String requestPath = request.path();
            if (!requestPath.contains(routePath)) {
                // special case if calling the service root at /path/SampleService.svc, always append a forward slash
                // for easier handling, so to make it /path/SampleService.svc/
                requestPath += '/';
            }

            // parse out the base URI and path
            String hostUri = request.scheme() + "://" + request.authority().host();
            baseUri = hostUri + (basePath = routeMountPoint);

            // parse out the resource path and entity name
            resourcePath = requestPath.substring((routeMountPoint + routePath).replaceAll("/{2,}", "/").length() - 1);
            entityName = emptyToNull(resourcePath.split("\\W", 2)[0]); // assume the first non-word char separates it

            // if an entity name is provided, concatenate the full qualified name
            fullQualifiedName = entityName != null ? schemaNamespace + '.' + entityName : null;

            // construct the full request path and URI, the query is provided by the request
            requestQuery = nullToEmpty(request.query());
            requestUri = hostUri + (this.requestPath = basePath + schemaNamespace + resourcePath)
                    + (requestQuery.isEmpty() ? EMPTY : "?" + requestQuery);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this).add("requestUri", requestUri).add("requestPath", requestPath)
                    .add("requestQuery", requestQuery).add("baseUri", baseUri).add("basePath", basePath)
                    .add("schemaNamespace", schemaNamespace).add("resourcePath", resourcePath)
                    .add("entityName", entityName).add("fullQualifiedName", fullQualifiedName).toString();
        }
    }
}
